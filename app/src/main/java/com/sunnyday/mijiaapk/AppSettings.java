package com.sunnyday.mijiaapk;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static final int DEFAULT_HIGH_THRESHOLD = 80;
    static final int DEFAULT_LOW_THRESHOLD = 40;

    private static final String PREFS = "mijia_plug_settings";
    private static final String KEY_PLUG_NAME = "plug_name";
    private static final String KEY_PLUG_IP = "plug_ip";
    private static final String KEY_PLUG_TOKEN = "plug_token";
    private static final String KEY_HIGH_THRESHOLD = "high_threshold";
    private static final String KEY_LOW_THRESHOLD = "low_threshold";
    private static final String KEY_AUTOMATION_ENABLED = "automation_enabled";
    private static final String KEY_LAST_COMMAND_SET = "last_command_set";
    private static final String KEY_LAST_COMMAND_ON = "last_command_on";

    private AppSettings() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String plugName(Context context) {
        return prefs(context).getString(KEY_PLUG_NAME, "小米智能插座 3");
    }

    static String plugIp(Context context) {
        return prefs(context).getString(KEY_PLUG_IP, "");
    }

    static String plugToken(Context context) {
        return prefs(context).getString(KEY_PLUG_TOKEN, "");
    }

    static int highThreshold(Context context) {
        return prefs(context).getInt(KEY_HIGH_THRESHOLD, DEFAULT_HIGH_THRESHOLD);
    }

    static int lowThreshold(Context context) {
        return prefs(context).getInt(KEY_LOW_THRESHOLD, DEFAULT_LOW_THRESHOLD);
    }

    static boolean automationEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTOMATION_ENABLED, false);
    }

    static Boolean lastCommandOn(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_LAST_COMMAND_SET, false)) {
            return null;
        }
        return prefs.getBoolean(KEY_LAST_COMMAND_ON, false);
    }

    static void save(
            Context context,
            String name,
            String ip,
            String token,
            int lowThreshold,
            int highThreshold,
            boolean automationEnabled
    ) {
        prefs(context)
                .edit()
                .putString(KEY_PLUG_NAME, normalizePlugName(name))
                .putString(KEY_PLUG_IP, ip.trim())
                .putString(KEY_PLUG_TOKEN, token.trim().toLowerCase())
                .putInt(KEY_LOW_THRESHOLD, lowThreshold)
                .putInt(KEY_HIGH_THRESHOLD, highThreshold)
                .putBoolean(KEY_AUTOMATION_ENABLED, automationEnabled)
                .apply();
    }

    static void rememberCommand(Context context, boolean on) {
        prefs(context)
                .edit()
                .putBoolean(KEY_LAST_COMMAND_SET, true)
                .putBoolean(KEY_LAST_COMMAND_ON, on)
                .apply();
    }

    static void clearRememberedCommand(Context context) {
        prefs(context)
                .edit()
                .remove(KEY_LAST_COMMAND_SET)
                .remove(KEY_LAST_COMMAND_ON)
                .apply();
    }

    static boolean hasValidPlugConfig(Context context) {
        return isValidIpOrHost(plugIp(context)) && isValidToken(plugToken(context));
    }

    static String plugSummary(Context context) {
        if (!hasValidPlugConfig(context)) {
            return "还没有添加可用插座";
        }
        return plugName(context) + " / " + plugIp(context);
    }

    static boolean isValidToken(String token) {
        return token != null && token.trim().matches("(?i)[0-9a-f]{32}");
    }

    static boolean isValidIpOrHost(String value) {
        return value != null && value.trim().length() >= 3 && !value.trim().contains(" ");
    }

    private static String normalizePlugName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "小米智能插座 3";
        }
        return name.trim();
    }
}
