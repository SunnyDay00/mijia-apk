package com.sunnyday.mijiaapk;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int COLOR_PAGE = 0xfff4f6f8;
    private static final int COLOR_CARD = 0xffffffff;
    private static final int COLOR_TEXT = 0xff111827;
    private static final int COLOR_MUTED = 0xff667085;
    private static final int COLOR_PRIMARY = 0xff0f766e;
    private static final int COLOR_SOFT = 0xffe8f5f3;
    private static final int COLOR_WARNING = 0xfffff7ed;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText nameInput;
    private EditText ipInput;
    private EditText tokenInput;
    private EditText lowInput;
    private EditText highInput;
    private Switch automationInput;
    private Switch keepAliveInput;
    private TextView plugSummaryText;
    private TextView batteryText;
    private TextView serviceStateText;
    private TextView statusText;
    private TextView logText;
    private BroadcastReceiver logReceiver;
    private BroadcastReceiver batteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        buildUi();
        loadSettingsIntoUi();
        registerLogReceiver();
        registerBatteryReceiver();
        updateBatteryFromStickyIntent();
    }

    @Override
    protected void onDestroy() {
        if (logReceiver != null) {
            unregisterReceiver(logReceiver);
        }
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_PAGE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        scroll.addView(root);

        LinearLayout hero = card();
        hero.setBackground(rounded(COLOR_PRIMARY, 18));
        TextView title = text("米家插座电量控制", 26, Color.WHITE, Typeface.BOLD);
        TextView subtitle = text("手机直连局域网插座，按电量阈值自动停止或恢复充电。", 14, 0xffd8fffa, Typeface.NORMAL);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        hero.addView(title, matchWrap());
        hero.addView(subtitle, matchWrap());

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        batteryText = metric("当前电量", "--%");
        serviceStateText = metric("服务状态", "未运行");
        statusRow.addView(batteryText, weightWithRightMargin());
        statusRow.addView(serviceStateText, weight());
        hero.addView(statusRow, matchWrap());
        root.addView(hero, matchWrap());

        LinearLayout plugCard = card();
        plugCard.addView(sectionTitle("插座"));
        plugSummaryText = badge("");
        plugCard.addView(plugSummaryText, matchWrap());
        nameInput = input("插座名称，例如 床头充电插座", InputType.TYPE_CLASS_TEXT);
        ipInput = input("插座局域网 IP，例如 192.168.1.100", InputType.TYPE_CLASS_TEXT);
        tokenInput = input("32 位 token", InputType.TYPE_CLASS_TEXT);
        plugCard.addView(field("名称", nameInput));
        plugCard.addView(field("局域网 IP", ipInput));
        plugCard.addView(field("Token", tokenInput));
        Button savePlug = primaryButton("添加 / 保存插座");
        savePlug.setOnClickListener(v -> savePlugSettings());
        plugCard.addView(savePlug, matchWrap());
        root.addView(plugCard, matchWrap());

        LinearLayout manualCard = card();
        manualCard.addView(sectionTitle("手动测试"));
        manualCard.addView(helper("添加插座后先读取状态，再测试开启和关闭。"));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button on = secondaryButton("开启");
        Button off = secondaryButton("关闭");
        Button status = secondaryButton("状态");
        on.setOnClickListener(v -> setPlugPower(true));
        off.setOnClickListener(v -> setPlugPower(false));
        status.setOnClickListener(v -> readPlugStatus());
        actions.addView(on, weightWithRightMargin());
        actions.addView(off, weightWithRightMargin());
        actions.addView(status, weight());
        manualCard.addView(actions, matchWrap());
        root.addView(manualCard, matchWrap());

        LinearLayout automationCard = card();
        automationCard.addView(sectionTitle("自动控制"));
        lowInput = input("低电量开启阈值，例如 40", InputType.TYPE_CLASS_NUMBER);
        highInput = input("高电量关闭阈值，例如 80", InputType.TYPE_CLASS_NUMBER);
        LinearLayout thresholdRow = new LinearLayout(this);
        thresholdRow.setOrientation(LinearLayout.HORIZONTAL);
        thresholdRow.addView(field("低于多少开启", lowInput), weightWithRightMargin());
        thresholdRow.addView(field("高于多少关闭", highInput), weight());
        automationCard.addView(thresholdRow, matchWrap());

        keepAliveInput = new Switch(this);
        keepAliveInput.setText("常驻通知，保持电量监听服务运行");
        keepAliveInput.setTextColor(COLOR_TEXT);
        keepAliveInput.setTextSize(15);
        keepAliveInput.setPadding(0, dp(10), 0, dp(6));
        keepAliveInput.setOnCheckedChangeListener(this::onKeepAliveChanged);
        automationCard.addView(keepAliveInput, matchWrap());

        automationInput = new Switch(this);
        automationInput.setText("启用阈值自动开关插座");
        automationInput.setTextColor(COLOR_TEXT);
        automationInput.setTextSize(15);
        automationInput.setPadding(0, dp(6), 0, dp(8));
        automationInput.setOnCheckedChangeListener(this::onAutomationChanged);
        automationCard.addView(automationInput, matchWrap());
        root.addView(automationCard, matchWrap());

        LinearLayout permissionCard = card();
        permissionCard.addView(sectionTitle("后台权限"));
        TextView note = helper("HyperOS 需要允许通知、自启动和后台运行，否则锁屏后可能停止自动控制。");
        note.setBackground(rounded(COLOR_WARNING, 12));
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        permissionCard.addView(note, matchWrap());
        LinearLayout permissionRow = new LinearLayout(this);
        permissionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button battery = secondaryButton("后台运行");
        Button autostart = secondaryButton("自启动");
        Button appSettings = secondaryButton("应用设置");
        battery.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
        autostart.setOnClickListener(v -> openMiuiAutostart());
        appSettings.setOnClickListener(v -> openAppDetails());
        permissionRow.addView(battery, weightWithRightMargin());
        permissionRow.addView(autostart, weightWithRightMargin());
        permissionRow.addView(appSettings, weight());
        permissionCard.addView(permissionRow, matchWrap());
        root.addView(permissionCard, matchWrap());

        LinearLayout logCard = card();
        logCard.addView(sectionTitle("状态日志"));
        statusText = text("配置已加载", 14, COLOR_MUTED, Typeface.NORMAL);
        statusText.setPadding(0, 0, 0, dp(8));
        logCard.addView(statusText, matchWrap());
        logText = text("", 13, COLOR_MUTED, Typeface.NORMAL);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        logText.setBackground(rounded(0xfff2f4f7, 12));
        logCard.addView(logText, matchWrap());
        root.addView(logCard, matchWrap());

        setContentView(scroll);
    }

    private void loadSettingsIntoUi() {
        nameInput.setText(AppSettings.plugName(this));
        ipInput.setText(AppSettings.plugIp(this));
        tokenInput.setText(AppSettings.plugToken(this));
        lowInput.setText(String.format(Locale.US, "%d", AppSettings.lowThreshold(this)));
        highInput.setText(String.format(Locale.US, "%d", AppSettings.highThreshold(this)));
        keepAliveInput.setChecked(AppSettings.keepAliveEnabled(this));
        automationInput.setChecked(AppSettings.automationEnabled(this));
        updatePlugSummary();
        updateServiceState();
        status("配置已加载");
    }

    private boolean savePlugSettings() {
        String name = nameInput.getText().toString().trim();
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

        AppSettings.save(
                this,
                name,
                ip,
                token,
                low,
                high,
                automationInput.isChecked(),
                keepAliveInput.isChecked()
        );
        AppSettings.clearRememberedCommand(this);
        updatePlugSummary();
        syncService();
        status("插座已保存，建议先读取状态测试连接");
        return true;
    }

    private void onAutomationChanged(CompoundButton button, boolean enabled) {
        if (!button.isPressed()) {
            return;
        }
        if (enabled && !savePlugSettings()) {
            automationInput.setChecked(false);
            return;
        }
        AppSettings.setRuntimeFlags(this, enabled, keepAliveInput.isChecked());
        syncService();
        status(enabled ? "自动控制已启用" : "自动控制已关闭");
    }

    private void onKeepAliveChanged(CompoundButton button, boolean enabled) {
        if (!button.isPressed()) {
            return;
        }
        AppSettings.setRuntimeFlags(this, automationInput.isChecked(), enabled);
        syncService();
        status(enabled ? "常驻通知已开启" : "常驻通知已关闭");
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
        return savePlugSettings() && AppSettings.hasValidPlugConfig(this);
    }

    private void syncService() {
        Intent service = new Intent(this, PlugAutomationService.class)
                .setAction(PlugAutomationService.ACTION_REFRESH);
        if (AppSettings.automationEnabled(this) || AppSettings.keepAliveEnabled(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } else {
            stopService(service);
        }
        updateServiceState();
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

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateBatteryText(intent);
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void updateBatteryFromStickyIntent() {
        Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            updateBatteryText(sticky);
        }
    }

    private void updateBatteryText(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            batteryText.setText("当前电量\n--%");
            return;
        }
        int percent = Math.round(level * 100f / scale);
        batteryText.setText("当前电量\n" + percent + "%");
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

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openMiuiPowerManager();
            return;
        }

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            status("系统已允许忽略电池优化");
            openMiuiPowerManager();
            return;
        }

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + getPackageName()));
        if (!openIntent(intent, "无法打开电池优化申请页")) {
            openMiuiPowerManager();
        }
    }

    private void openMiuiAutostart() {
        Intent intent = new Intent().setComponent(new ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ));
        if (!openIntent(intent, "无法打开 HyperOS 自启动页面")) {
            openAppDetails();
        }
    }

    private void openMiuiPowerManager() {
        Intent intent = new Intent("miui.intent.action.POWER_MANAGER");
        if (!openIntent(intent, "无法打开 HyperOS 电量管理页面")) {
            Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (!openIntent(fallback, "无法打开系统电池优化页面")) {
                openAppDetails();
            }
        }
    }

    private void openAppDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()));
        openIntent(intent, "无法打开应用设置");
    }

    private boolean openIntent(Intent intent, String errorMessage) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            status(errorMessage + ": " + e.getMessage());
            return false;
        }
    }

    private void updatePlugSummary() {
        plugSummaryText.setText("当前插座：" + AppSettings.plugSummary(this));
    }

    private void updateServiceState() {
        boolean running = AppSettings.automationEnabled(this) || AppSettings.keepAliveEnabled(this);
        serviceStateText.setText(running ? "服务状态\n常驻中" : "服务状态\n未运行");
    }

    private void status(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        editText.setTextSize(15);
        editText.setTextColor(COLOR_TEXT);
        editText.setHintTextColor(0xff98a2b3);
        return editText;
    }

    private LinearLayout field(String label, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        box.addView(text(label, 13, COLOR_MUTED, Typeface.NORMAL), matchWrap());
        box.addView(input, matchWrap());
        return box;
    }

    private TextView sectionTitle(String text) {
        TextView label = text(text, 18, COLOR_TEXT, Typeface.BOLD);
        label.setPadding(0, 0, 0, dp(10));
        return label;
    }

    private TextView helper(String text) {
        TextView view = text(text, 14, COLOR_MUTED, Typeface.NORMAL);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView badge(String text) {
        TextView view = text(text, 14, COLOR_PRIMARY, Typeface.BOLD);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(rounded(COLOR_SOFT, 12));
        return view;
    }

    private TextView metric(String label, String value) {
        TextView view = text(label + "\n" + value, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(12), dp(10), dp(12));
        view.setBackground(rounded(0x22ffffff, 14));
        return view;
    }

    private TextView text(String content, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button primaryButton(String text) {
        Button button = button(text);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(COLOR_PRIMARY, 14));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = button(text);
        button.setTextColor(COLOR_PRIMARY);
        button.setBackground(rounded(COLOR_SOFT, 14));
        return button;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(COLOR_CARD, 18));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
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

    private LinearLayout.LayoutParams weightWithRightMargin() {
        LinearLayout.LayoutParams params = weight();
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
