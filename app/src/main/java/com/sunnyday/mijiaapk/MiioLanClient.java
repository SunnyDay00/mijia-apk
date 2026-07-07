package com.sunnyday.mijiaapk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class MiioLanClient {
    private static final int MIIO_PORT = 54321;
    private static final int HEADER_SIZE = 32;
    private static final int MAGIC = 0x2131;
    private static final AtomicInteger IDS = new AtomicInteger((int) (System.currentTimeMillis() % 10000));

    private final String ip;
    private final byte[] token;
    private final int timeoutMs;

    MiioLanClient(String ip, String tokenHex) {
        this.ip = ip;
        this.token = parseToken(tokenHex);
        this.timeoutMs = 5000;
    }

    JSONObject call(String method, JSONArray params) throws Exception {
        Handshake handshake = handshake();
        JSONObject payload = new JSONObject()
                .put("id", IDS.incrementAndGet())
                .put("method", method)
                .put("params", params);

        byte[] encrypted = encrypt((payload.toString() + "\u0000").getBytes(StandardCharsets.UTF_8));
        int stamp = handshake.stamp + 1;

        byte[] header = ByteBuffer.allocate(16)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort((short) MAGIC)
                .putShort((short) (HEADER_SIZE + encrypted.length))
                .putInt(0)
                .putInt(handshake.deviceId)
                .putInt(stamp)
                .array();

        byte[] checksum = md5(concat(header, token, encrypted));
        byte[] response = udpRoundTrip(concat(header, checksum, encrypted));
        JSONObject decoded = decodeResponse(response);
        if (decoded.has("error")) {
            throw new MiioException("设备返回错误: " + decoded.get("error"));
        }
        return decoded;
    }

    boolean setPower(boolean on) throws Exception {
        JSONObject prop = new JSONObject()
                .put("did", "switch:on")
                .put("siid", 2)
                .put("piid", 1)
                .put("value", on);
        JSONObject response = call("set_properties", new JSONArray().put(prop));
        ensureFirstResultOk(response);
        return on;
    }

    Boolean getPower() throws Exception {
        JSONObject prop = new JSONObject()
                .put("did", "switch:on")
                .put("siid", 2)
                .put("piid", 1);
        JSONObject response = call("get_properties", new JSONArray().put(prop));
        JSONArray result = response.optJSONArray("result");
        if (result == null || result.length() == 0) {
            return null;
        }
        JSONObject first = result.getJSONObject(0);
        if (first.optInt("code", 0) != 0) {
            throw new MiioException("读取开关状态失败: " + first);
        }
        if (!first.has("value")) {
            return null;
        }
        return first.getBoolean("value");
    }

    private void ensureFirstResultOk(JSONObject response) throws Exception {
        JSONArray result = response.optJSONArray("result");
        if (result == null || result.length() == 0) {
            return;
        }
        JSONObject first = result.getJSONObject(0);
        if (first.optInt("code", 0) != 0) {
            throw new MiioException("控制失败: " + first);
        }
    }

    private Handshake handshake() throws Exception {
        byte[] hello = ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort((short) MAGIC)
                .putShort((short) HEADER_SIZE)
                .putInt(0xffffffff)
                .putInt(0xffffffff)
                .putInt(0xffffffff)
                .put(new byte[]{
                        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
                })
                .array();
        byte[] response = udpRoundTrip(hello);
        Header header = parseHeader(response);
        if (header.magic != MAGIC || header.length != HEADER_SIZE) {
            throw new MiioException("握手响应异常: " + toHex(response));
        }
        return new Handshake(header.deviceId, header.stamp);
    }

    private byte[] udpRoundTrip(byte[] request) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.send(new DatagramPacket(request, request.length, address, MIIO_PORT));

            byte[] buffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            return Arrays.copyOf(response.getData(), response.getLength());
        }
    }

    private JSONObject decodeResponse(byte[] response) throws Exception {
        Header header = parseHeader(response);
        if (header.magic != MAGIC) {
            throw new MiioException(String.format(Locale.US, "响应 magic 异常: 0x%04x", header.magic));
        }
        if (header.length != response.length) {
            throw new MiioException("响应长度异常: " + header.length + " / " + response.length);
        }

        byte[] checksum = Arrays.copyOfRange(response, 16, 32);
        byte[] encrypted = Arrays.copyOfRange(response, 32, response.length);
        byte[] expected = md5(concat(Arrays.copyOfRange(response, 0, 16), token, encrypted));
        if (!Arrays.equals(checksum, expected)) {
            throw new MiioException("响应校验失败，请检查 token");
        }

        byte[] plaintext = decrypt(encrypted);
        String json = new String(plaintext, StandardCharsets.UTF_8).replace("\u0000", "").trim();
        return new JSONObject(json);
    }

    private Header parseHeader(byte[] data) throws MiioException {
        if (data.length < HEADER_SIZE) {
            throw new MiioException("设备响应过短: " + toHex(data));
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int magic = Short.toUnsignedInt(buffer.getShort());
        int length = Short.toUnsignedInt(buffer.getShort());
        buffer.getInt();
        int deviceId = buffer.getInt();
        int stamp = buffer.getInt();
        return new Header(magic, length, deviceId, stamp);
    }

    private byte[] encrypt(byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new IvParameterSpec(iv()));
        return cipher.doFinal(plaintext);
    }

    private byte[] decrypt(byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new IvParameterSpec(iv()));
        return cipher.doFinal(ciphertext);
    }

    private byte[] key() throws Exception {
        return md5(token);
    }

    private byte[] iv() throws Exception {
        return md5(concat(key(), token));
    }

    private static byte[] parseToken(String tokenHex) {
        String normalized = tokenHex.trim().toLowerCase(Locale.US);
        if (!normalized.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("token 必须是 32 位十六进制字符串");
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return bytes;
    }

    private static byte[] md5(byte[] data) throws Exception {
        return MessageDigest.getInstance("MD5").digest(data);
    }

    private static byte[] concat(byte[]... arrays) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.write(array);
        }
        return output.toByteArray();
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte value : data) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }

    static final class MiioException extends Exception {
        MiioException(String message) {
            super(message);
        }
    }

    private static final class Header {
        final int magic;
        final int length;
        final int deviceId;
        final int stamp;

        Header(int magic, int length, int deviceId, int stamp) {
            this.magic = magic;
            this.length = length;
            this.deviceId = deviceId;
            this.stamp = stamp;
        }
    }

    private static final class Handshake {
        final int deviceId;
        final int stamp;

        Handshake(int deviceId, int stamp) {
            this.deviceId = deviceId;
            this.stamp = stamp;
        }
    }
}
