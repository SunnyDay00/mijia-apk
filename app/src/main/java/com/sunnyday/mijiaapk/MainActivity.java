package com.sunnyday.mijiaapk;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText ipInput;
    private EditText tokenInput;
    private EditText lowInput;
    private EditText highInput;
    private CheckBox automationInput;
    private TextView statusText;
    private TextView logText;
    private BroadcastReceiver logReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        buildUi();
        loadSettingsIntoUi();
        registerLogReceiver();
    }

    @Override
    protected void onDestroy() {
        if (logReceiver != null) {
            unregisterReceiver(logReceiver);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("米家插座电量控制");
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("填写插座 IP 和 token 后，手机可按电量阈值直接在局域网内开关插座。");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        ipInput = input("插座 IP，例如 192.168.1.100", InputType.TYPE_CLASS_TEXT);
        tokenInput = input("32 位 token", InputType.TYPE_CLASS_TEXT);
        lowInput = input("低电量开启阈值，例如 40", InputType.TYPE_CLASS_NUMBER);
        highInput = input("高电量关闭阈值，例如 80", InputType.TYPE_CLASS_NUMBER);

        root.addView(label("插座 IP"));
        root.addView(ipInput, matchWrap());
        root.addView(label("插座 Token"));
        root.addView(tokenInput, matchWrap());

        LinearLayout thresholdRow = new LinearLayout(this);
        thresholdRow.setOrientation(LinearLayout.HORIZONTAL);
        thresholdRow.addView(wrapField("低于多少开启", lowInput), weight());
        thresholdRow.addView(wrapField("高于多少关闭", highInput), weight());
        root.addView(thresholdRow, matchWrap());

        automationInput = new CheckBox(this);
        automationInput.setText("启用电量自动控制和开机自启");
        automationInput.setPadding(0, dp(8), 0, dp(8));
        automationInput.setOnCheckedChangeListener(this::onAutomationChanged);
        root.addView(automationInput, matchWrap());

        Button save = button("保存配置");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button on = button("开启插座");
        Button off = button("关闭插座");
        Button status = button("读取状态");
        on.setOnClickListener(v -> setPlugPower(true));
        off.setOnClickListener(v -> setPlugPower(false));
        status.setOnClickListener(v -> readPlugStatus());
        actions.addView(on, weight());
        actions.addView(off, weight());
        actions.addView(status, weight());
        root.addView(actions, matchWrap());

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(16), 0, dp(8));
        root.addView(statusText, matchWrap());

        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setBackgroundColor(0xffeef2f4);
        root.addView(logText, matchWrap());

        setContentView(scroll);
    }

    private void loadSettingsIntoUi() {
        ipInput.setText(AppSettings.plugIp(this));
        tokenInput.setText(AppSettings.plugToken(this));
        lowInput.setText(String.format(Locale.US, "%d", AppSettings.lowThreshold(this)));
        highInput.setText(String.format(Locale.US, "%d", AppSettings.highThreshold(this)));
        automationInput.setChecked(AppSettings.automationEnabled(this));
        status("配置已加载");
    }

    private boolean saveSettings() {
        String ip = ipInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        int low = parsePercent(lowInput, AppSettings.DEFAULT_LOW_THRESHOLD);
        int high = parsePercent(highInput, AppSettings.DEFAULT_HIGH_THRESHOLD);

        if (!AppSettings.isValidIpOrHost(ip)) {
            status("插座 IP 不正确");
            return false;
        }
        if (!AppSettings.isValidToken(token)) {
            status("token 必须是 32 位十六进制字符串");
            return false;
        }
        if (low < 1 || high > 100 || low >= high) {
            status("阈值需满足 1 <= 低阈值 < 高阈值 <= 100");
            return false;
        }

        AppSettings.save(this, ip, token, low, high, automationInput.isChecked());
        AppSettings.clearRememberedCommand(this);
        status("配置已保存");
        syncService();
        return true;
    }

    private void onAutomationChanged(CompoundButton button, boolean enabled) {
        if (!button.isPressed()) {
            return;
        }
        saveSettings();
    }

    private void setPlugPower(boolean on) {
        if (!validateSavedConfig()) {
            return;
        }
        status(on ? "正在开启插座..." : "正在关闭插座...");
        executor.execute(() -> {
            try {
                MiioLanClient client = client();
                client.setPower(on);
                AppSettings.rememberCommand(this, on);
                status(on ? "插座已开启" : "插座已关闭");
            } catch (Exception e) {
                status("控制失败: " + e.getMessage());
            }
        });
    }

    private void readPlugStatus() {
        if (!validateSavedConfig()) {
            return;
        }
        status("正在读取插座状态...");
        executor.execute(() -> {
            try {
                Boolean on = client().getPower();
                status(on == null ? "未读到开关状态" : (on ? "插座当前开启" : "插座当前关闭"));
            } catch (Exception e) {
                status("读取失败: " + e.getMessage());
            }
        });
    }

    private MiioLanClient client() {
        return new MiioLanClient(AppSettings.plugIp(this), AppSettings.plugToken(this));
    }

    private boolean validateSavedConfig() {
        return saveSettings() && AppSettings.hasValidPlugConfig(this);
    }

    private void syncService() {
        Intent service = new Intent(this, PlugAutomationService.class)
                .setAction(PlugAutomationService.ACTION_REFRESH);
        if (AppSettings.automationEnabled(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } else {
            stopService(service);
        }
    }

    private void registerLogReceiver() {
        logReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String line = intent.getStringExtra(PlugAutomationService.EXTRA_LOG);
                if (line == null) {
                    return;
                }
                logText.append(line + "\n");
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    logReceiver,
                    new IntentFilter(PlugAutomationService.BROADCAST_LOG),
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(logReceiver, new IntentFilter(PlugAutomationService.BROADCAST_LOG));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void status(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        return editText;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setPadding(0, dp(8), 0, 0);
        return label;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private LinearLayout wrapField(String label, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, dp(8), 0);
        box.addView(label(label));
        box.addView(input, matchWrap());
        return box;
    }

    private int parsePercent(EditText input, int fallback) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
