from __future__ import annotations

import hashlib
import json
import socket
import struct
import threading
import time
from dataclasses import dataclass
from itertools import count
from typing import Any

from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

MIIO_PORT = 54321
HEADER_SIZE = 32
MAGIC = 0x2131


class MiioProtocolError(RuntimeError):
    pass


@dataclass(frozen=True)
class Handshake:
    device_id: int
    stamp: int


class MiioLanClient:
    def __init__(self, ip: str, token_hex: str, timeout: float = 5.0):
        self.ip = ip
        self.timeout = timeout
        self.token = self._parse_token(token_hex)
        self._ids = count(int(time.time()) % 10_000)
        self._lock = threading.Lock()

    def call(self, method: str, params: list[Any] | dict[str, Any] | None = None) -> Any:
        if params is None:
            params = []

        with self._lock:
            request = {
                "id": next(self._ids),
                "method": method,
                "params": params,
            }
            response = self._send_packet(request)

        if "error" in response:
            raise MiioProtocolError(f"Device returned error: {response['error']!r}")
        return response.get("result", response)

    def _send_packet(self, payload: dict[str, Any]) -> dict[str, Any]:
        handshake = self._handshake()
        encrypted = self._encrypt_json(payload)
        stamp = (handshake.stamp + 1) & 0xFFFFFFFF

        header = struct.pack(
            ">HHIII",
            MAGIC,
            HEADER_SIZE + len(encrypted),
            0,
            handshake.device_id,
            stamp,
        )
        checksum = hashlib.md5(header + self.token + encrypted).digest()  # nosec B324
        packet = header + checksum + encrypted

        raw = self._udp_roundtrip(packet)
        return self._decode_response(raw)

    def _handshake(self) -> Handshake:
        hello = struct.pack(
            ">HHIII16s",
            MAGIC,
            HEADER_SIZE,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            b"\xff" * 16,
        )
        raw = self._udp_roundtrip(hello)
        magic, length, _unknown, device_id, stamp = self._parse_header(raw[:16])
        if magic != MAGIC or length != HEADER_SIZE:
            raise MiioProtocolError(f"Unexpected handshake response: {raw.hex()}")
        return Handshake(device_id=device_id, stamp=stamp)

    def _udp_roundtrip(self, packet: bytes) -> bytes:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(self.timeout)
            try:
                sock.sendto(packet, (self.ip, MIIO_PORT))
                raw, _addr = sock.recvfrom(4096)
            except TimeoutError as exc:
                raise MiioProtocolError(f"Timeout talking to {self.ip}:{MIIO_PORT}") from exc
            except OSError as exc:
                raise MiioProtocolError(str(exc)) from exc
        if len(raw) < HEADER_SIZE:
            raise MiioProtocolError(f"Short response from device: {raw.hex()}")
        return raw

    def _decode_response(self, raw: bytes) -> dict[str, Any]:
        magic, length, _unknown, _device_id, _stamp = self._parse_header(raw[:16])
        if magic != MAGIC:
            raise MiioProtocolError(f"Unexpected response magic: 0x{magic:04x}")
        if length != len(raw):
            raise MiioProtocolError(
                f"Unexpected response length: header={length}, actual={len(raw)}"
            )

        checksum = raw[16:32]
        ciphertext = raw[32:]
        expected = hashlib.md5(raw[:16] + self.token + ciphertext).digest()  # nosec B324
        if checksum != expected:
            raise MiioProtocolError("Response checksum mismatch; check PLUG_TOKEN")

        plaintext = self._decrypt(ciphertext).rstrip(b"\x00")
        try:
            return json.loads(plaintext.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise MiioProtocolError(f"Invalid JSON response: {plaintext!r}") from exc

    @staticmethod
    def _parse_header(raw_header: bytes) -> tuple[int, int, int, int, int]:
        if len(raw_header) != 16:
            raise MiioProtocolError("Header must be 16 bytes")
        return struct.unpack(">HHIII", raw_header)

    @staticmethod
    def _parse_token(token_hex: str) -> bytes:
        token_hex = token_hex.strip().lower()
        if len(token_hex) != 32:
            raise ValueError("PLUG_TOKEN must be a 32-character hex string")
        try:
            token = bytes.fromhex(token_hex)
        except ValueError as exc:
            raise ValueError("PLUG_TOKEN must be a valid hex string") from exc
        if len(token) != 16:
            raise ValueError("PLUG_TOKEN must decode to 16 bytes")
        return token

    def _encrypt_json(self, payload: dict[str, Any]) -> bytes:
        plaintext = json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode("utf-8") + b"\x00"
        return self._encrypt(plaintext)

    def _encrypt(self, plaintext: bytes) -> bytes:
        padder = padding.PKCS7(128).padder()
        padded = padder.update(plaintext) + padder.finalize()
        encryptor = self._cipher().encryptor()
        return encryptor.update(padded) + encryptor.finalize()

    def _decrypt(self, ciphertext: bytes) -> bytes:
        decryptor = self._cipher().decryptor()
        padded = decryptor.update(ciphertext) + decryptor.finalize()
        unpadder = padding.PKCS7(128).unpadder()
        return unpadder.update(padded) + unpadder.finalize()

    def _cipher(self) -> Cipher:
        key = hashlib.md5(self.token).digest()  # nosec B324
        iv = hashlib.md5(key + self.token).digest()  # nosec B324
        return Cipher(algorithms.AES(key), modes.CBC(iv))
