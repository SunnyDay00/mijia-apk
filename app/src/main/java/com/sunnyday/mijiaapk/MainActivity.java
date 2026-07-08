package com.sunnyday.mijiaapk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.NotificationManager;
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
import android.os.Process;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Method;
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
    private static final int COLOR_BORDER = 0xffd8e7e4;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText lowInput;
    private EditText highInput;
    private Switch automationInput;
    private Switch keepAliveInput;
    private Button plugHeaderButton;
    private LinearLayout plugContent;
    private LinearLayout permissionContent;
    private TextView batteryText;
    private TextView serviceStateText;
    private TextView statusText;
    private TextView logText;
    private BroadcastReceiver logReceiver;
    private BroadcastReceiver batteryReceiver;
    private Boolean lastKnownPower;

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

    @Override
    protected void onResume() {
        super.onResume();
        renderPermissionStatus();
        updateServiceState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        renderPermissionStatus();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(COLOR_PAGE);
        getWindow().setNavigationBarColor(COLOR_PAGE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_PAGE);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(16);
        int baseTopPadding = dp(8);
        int baseBottomPadding = dp(28);
        root.setPadding(horizontalPadding, baseTopPadding, horizontalPadding, baseBottomPadding);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int topInset;
                int bottomInset;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    topInset = bars.top;
                    bottomInset = bars.bottom;
                } else {
                    topInset = insets.getSystemWindowInsetTop();
                    bottomInset = insets.getSystemWindowInsetBottom();
                }
                view.setPadding(
                        horizontalPadding,
                        baseTopPadding + topInset,
                        horizontalPadding,
                        baseBottomPadding + bottomInset
                );
                return insets;
            });
        }
        scroll.addView(root);

        LinearLayout hero = card();
        hero.setPadding(dp(16), dp(14), dp(16), dp(14));
        hero.setBackground(rounded(COLOR_PRIMARY, 18));
        TextView title = text("米家插座电量控制", 23, Color.WHITE, Typeface.BOLD);
        TextView subtitle = text("手机直连局域网插座，按电量阈值自动停止或恢复充电。", 13, 0xffd8fffa, Typeface.NORMAL);
        subtitle.setPadding(0, dp(5), 0, dp(12));
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
        LinearLayout plugHeader = new LinearLayout(this);
        plugHeader.setOrientation(LinearLayout.HORIZONTAL);
        plugHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView plugTitle = sectionTitle("插座");
        plugTitle.setPadding(0, 0, 0, 0);
        plugHeaderButton = compactButton("添加");
        plugHeaderButton.setOnClickListener(v -> showPlugEditor(AppSettings.hasValidPlugConfig(this)));
        plugHeader.addView(plugTitle, weight());
        plugHeader.addView(plugHeaderButton, wrap());
        plugCard.addView(plugHeader, matchWrap());
        plugContent = new LinearLayout(this);
        plugContent.setOrientation(LinearLayout.VERTICAL);
        plugContent.setPadding(0, dp(12), 0, 0);
        plugCard.addView(plugContent, matchWrap());
        root.addView(plugCard, matchWrap());

        LinearLayout automationCard = card();
        automationCard.addView(sectionTitle("自动控制"));
        lowInput = input("低电量开启阈值，例如 40", InputType.TYPE_CLASS_NUMBER);
        highInput = input("高电量关闭阈值，例如 80", InputType.TYPE_CLASS_NUMBER);
        LinearLayout thresholdRow = new LinearLayout(this);
        thresholdRow.setOrientation(LinearLayout.HORIZONTAL);
        thresholdRow.addView(field("低于多少开启", lowInput), weightWithRightMargin());
        thresholdRow.addView(field("高于多少关闭", highInput), weight());
        automationCard.addView(thresholdRow, matchWrap());
        Button saveThresholds = secondaryButton("保存阈值");
        saveThresholds.setOnClickListener(v -> {
            if (saveThresholdSettings(true)) {
                syncService();
            }
        });
        automationCard.addView(saveThresholds, matchWrap());

        keepAliveInput = new Switch(this);
        keepAliveInput.setText("常驻通知，保持电量监听服务运行");
        keepAliveInput.setTextColor(COLOR_TEXT);
        keepAliveInput.setTextSize(15);
        keepAliveInput.setPadding(0, dp(12), 0, dp(6));
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
        permissionContent = new LinearLayout(this);
        permissionContent.setOrientation(LinearLayout.VERTICAL);
        permissionCard.addView(permissionContent, matchWrap());
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.requestApplyInsets();
        }
    }

    private void loadSettingsIntoUi() {
        lowInput.setText(String.format(Locale.US, "%d", AppSettings.lowThreshold(this)));
        highInput.setText(String.format(Locale.US, "%d", AppSettings.highThreshold(this)));
        keepAliveInput.setChecked(AppSettings.keepAliveEnabled(this));
        automationInput.setChecked(AppSettings.automationEnabled(this));
        lastKnownPower = AppSettings.lastCommandOn(this);
        renderPlugSection();
        renderPermissionStatus();
        renderLog();
        updateServiceState();
        status("配置已加载");
    }

    private void renderPlugSection() {
        if (plugContent == null) {
            return;
        }
        boolean hasPlug = AppSettings.hasValidPlugConfig(this);
        plugHeaderButton.setText(hasPlug ? "更换" : "添加");
        plugContent.removeAllViews();
        if (!hasPlug) {
            plugContent.addView(emptyPlugView(), matchWrap());
            return;
        }

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), dp(12), dp(12), dp(12));
        tile.setBackground(roundedStroke(0xfff8fbfb, 14, COLOR_BORDER));
        tile.setOnClickListener(v -> readPlugStatus());

        TextView icon = plugIcon();
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconParams.setMargins(0, 0, dp(12), 0);
        tile.addView(icon, iconParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(AppSettings.plugName(this), 16, COLOR_TEXT, Typeface.BOLD);
        TextView ip = text(AppSettings.plugIp(this), 13, COLOR_MUTED, Typeface.NORMAL);
        ip.setPadding(0, dp(3), 0, dp(8));
        info.addView(name, matchWrap());
        info.addView(ip, matchWrap());
        info.addView(stateBadge(), wrap());
        tile.addView(info, weightWithRightMargin());

        Button edit = compactButton("编辑");
        edit.setOnClickListener(v -> showPlugEditor(true));
        tile.addView(edit, wrap());
        plugContent.addView(tile, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);
        Button status = secondaryButton("状态");
        Button on = secondaryButton("开启");
        Button off = secondaryButton("关闭");
        status.setOnClickListener(v -> readPlugStatus());
        on.setOnClickListener(v -> setPlugPower(true));
        off.setOnClickListener(v -> setPlugPower(false));
        actions.addView(status, weightWithRightMargin());
        actions.addView(on, weightWithRightMargin());
        actions.addView(off, weight());
        plugContent.addView(actions, matchWrap());

        LinearLayout management = new LinearLayout(this);
        management.setOrientation(LinearLayout.HORIZONTAL);
        management.setPadding(0, dp(8), 0, 0);
        Button editConnection = secondaryButton("编辑连接");
        Button delete = dangerButton("删除插座");
        editConnection.setOnClickListener(v -> showPlugEditor(true));
        delete.setOnClickListener(v -> confirmDeletePlug());
        management.addView(editConnection, weightWithRightMargin());
        management.addView(delete, weight());
        plugContent.addView(management, matchWrap());
    }

    private LinearLayout emptyPlugView() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(14), dp(18), dp(14), dp(18));
        empty.setBackground(roundedStroke(0xfff8fbfb, 14, COLOR_BORDER));

        TextView icon = plugIcon();
        empty.addView(icon, fixed(dp(52), dp(52)));
        TextView title = text("还没有添加插座", 16, COLOR_TEXT, Typeface.BOLD);
        title.setPadding(0, dp(10), 0, dp(4));
        title.setGravity(Gravity.CENTER);
        empty.addView(title, matchWrap());
        TextView detail = text("点击添加，填写局域网 IP 和 32 位 token。", 13, COLOR_MUTED, Typeface.NORMAL);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, 0, 0, dp(14));
        empty.addView(detail, matchWrap());
        Button add = primaryButton("添加插座");
        add.setOnClickListener(v -> showPlugEditor(false));
        empty.addView(add, matchWrap());
        return empty;
    }

    private void showPlugEditor(boolean useCurrentValues) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), 0);

        EditText nameInput = input("插座名称，例如 小米智能插座 3", InputType.TYPE_CLASS_TEXT);
        EditText ipInput = input("插座局域网 IP，例如 192.168.123.207", InputType.TYPE_CLASS_TEXT);
        EditText tokenInput = input("32 位 token", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        if (useCurrentValues) {
            nameInput.setText(AppSettings.plugName(this));
            ipInput.setText(AppSettings.plugIp(this));
            tokenInput.setText(AppSettings.plugToken(this));
        } else {
            nameInput.setText("小米智能插座 3");
        }

        form.addView(field("名称", nameInput));
        form.addView(field("局域网 IP", ipInput));
        form.addView(field("Token", tokenInput));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(useCurrentValues ? "编辑插座" : "添加插座")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(current -> dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (savePlugFromDialog(nameInput, ipInput, tokenInput)) {
                        dialog.dismiss();
                    }
                }));
        dialog.show();
    }

    private boolean savePlugFromDialog(EditText nameInput, EditText ipInput, EditText tokenInput) {
        String name = nameInput.getText().toString().trim();
        String ip = ipInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        boolean editing = AppSettings.hasValidPlugConfig(this);
        String label = name.isEmpty() ? "小米智能插座 3" : name;

        if (!AppSettings.isValidIpOrHost(ip)) {
            status("插座 IP 不正确");
            ipInput.requestFocus();
            return false;
        }
        if (!AppSettings.isValidToken(token)) {
            status("token 必须是 32 位十六进制字符串");
            tokenInput.requestFocus();
            return false;
        }

        AppSettings.savePlug(this, name, ip, token);
        AppSettings.clearRememberedCommand(this);
        lastKnownPower = null;
        renderPlugSection();
        syncService();
        record((editing ? "编辑插座：" : "添加插座：") + label + " / " + ip);
        status("插座已保存，可先读取状态测试连接");
        return true;
    }

    private void confirmDeletePlug() {
        if (!AppSettings.hasValidPlugConfig(this)) {
            status("当前没有可删除的插座");
            return;
        }
        String summary = AppSettings.plugSummary(this);
        new AlertDialog.Builder(this)
                .setTitle("删除插座")
                .setMessage("删除后需要重新添加 IP 和 token 才能控制插座。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    AppSettings.deletePlug(this);
                    if (automationInput != null) {
                        automationInput.setChecked(false);
                    }
                    lastKnownPower = null;
                    renderPlugSection();
                    syncService();
                    record("删除插座：" + summary);
                    status("插座已删除");
                })
                .show();
    }

    private boolean saveThresholdSettings(boolean announce) {
        Integer low = readPercent(lowInput);
        Integer high = readPercent(highInput);
        if (low == null || high == null || low < 1 || high > 100 || low >= high) {
            status("阈值需满足 1 <= 低阈值 < 高阈值 <= 100");
            return false;
        }
        AppSettings.setThresholds(this, low, high);
        if (announce) {
            record("更新自动控制阈值：低于 " + low + "% 开启，高于 " + high + "% 关闭");
            status("阈值已保存");
        }
        return true;
    }

    private void onAutomationChanged(CompoundButton button, boolean enabled) {
        if (!button.isPressed()) {
            return;
        }
        if (enabled && !AppSettings.hasValidPlugConfig(this)) {
            status("请先添加插座");
            automationInput.setChecked(false);
            showPlugEditor(false);
            return;
        }
        if (enabled && !saveThresholdSettings(false)) {
            automationInput.setChecked(false);
            return;
        }
        AppSettings.setRuntimeFlags(this, enabled, keepAliveInput.isChecked());
        syncService();
        record(enabled ? "开启阈值自动控制" : "关闭阈值自动控制");
        status(enabled ? "自动控制已启用" : "自动控制已关闭");
    }

    private void onKeepAliveChanged(CompoundButton button, boolean enabled) {
        if (!button.isPressed()) {
            return;
        }
        AppSettings.setRuntimeFlags(this, automationInput.isChecked(), enabled);
        syncService();
        record(enabled ? "开启常驻通知" : "关闭常驻通知");
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
                lastKnownPower = on;
                recordFromWorker((on ? "开启插座：" : "关闭插座：") + AppSettings.plugSummary(this));
                runOnUiThread(this::renderPlugSection);
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
                if (on != null) {
                    AppSettings.rememberCommand(this, on);
                    lastKnownPower = on;
                    recordFromWorker("读取插座状态：" + AppSettings.plugSummary(this) + " / " + (on ? "开启" : "关闭"));
                    runOnUiThread(this::renderPlugSection);
                } else {
                    recordFromWorker("读取插座状态：" + AppSettings.plugSummary(this) + " / 未知");
                }
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
        if (!AppSettings.hasValidPlugConfig(this)) {
            status("请先添加插座");
            return false;
        }
        return true;
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

    private void renderPermissionStatus() {
        if (permissionContent == null) {
            return;
        }
        permissionContent.removeAllViews();
        boolean notifications = areNotificationsEnabled();
        boolean battery = isIgnoringBatteryOptimizations();
        Boolean autostart = isMiuiAutostartAllowed();

        permissionContent.addView(permissionRow(
                "通知权限",
                notifications,
                notifications ? "已允许显示常驻通知" : "未允许通知，前台服务状态可能不可见",
                "开启",
                v -> openNotificationSettings()
        ));
        permissionContent.addView(permissionRow(
                "后台运行",
                battery,
                battery ? "已允许忽略电池优化" : "未允许，锁屏后可能停止监听",
                "开启",
                v -> requestIgnoreBatteryOptimizations()
        ));
        permissionContent.addView(permissionRow(
                "自启动权限",
                Boolean.TRUE.equals(autostart),
                autostart == null
                        ? "系统未返回状态，请打开设置确认"
                        : (autostart ? "已允许开机后恢复服务" : "未允许，重启后可能不会恢复"),
                "开启",
                v -> openMiuiAutostart()
        ));

        Button appSettings = secondaryButton("打开应用设置");
        appSettings.setOnClickListener(v -> openAppDetails());
        permissionContent.addView(appSettings, matchWrap());
    }

    private LinearLayout permissionRow(
            String title,
            boolean enabled,
            String detail,
            String actionText,
            android.view.View.OnClickListener listener
    ) {
        int statusColor = enabled ? 0xff15803d : 0xffb42318;
        int bgColor = enabled ? 0xffecfdf3 : 0xfffff1f1;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundedStroke(bgColor, 12, statusColor));
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.setMargins(0, dp(10), 0, 0);
        row.setLayoutParams(rowParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 15, COLOR_TEXT, Typeface.BOLD);
        TextView detailView = text((enabled ? "已开启 · " : "未开启 · ") + detail, 12, statusColor, Typeface.NORMAL);
        detailView.setPadding(0, dp(4), 0, 0);
        info.addView(titleView, matchWrap());
        info.addView(detailView, matchWrap());

        Button action = compactButton(enabled ? "查看" : actionText);
        action.setOnClickListener(listener);
        row.addView(info, weightWithRightMargin());
        row.addView(action, wrap());
        return row;
    }

    private boolean areNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager == null || manager.areNotificationsEnabled();
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private Boolean isMiuiAutostartAllowed() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (appOps == null) {
                return null;
            }
            Method method = AppOpsManager.class.getDeclaredMethod(
                    "checkOpNoThrow",
                    int.class,
                    int.class,
                    String.class
            );
            method.setAccessible(true);
            int mode = (Integer) method.invoke(appOps, 10008, Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
        }
        openIntent(intent, "无法打开通知设置");
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
                renderLog();
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

    private void updateServiceState() {
        boolean running = AppSettings.automationEnabled(this) || AppSettings.keepAliveEnabled(this);
        serviceStateText.setText(running ? "服务状态\n常驻中" : "服务状态\n未运行");
    }

    private void renderLog() {
        if (logText != null) {
            logText.setText(AppSettings.operationLog(this));
        }
    }

    private void record(String message) {
        AppSettings.appendOperationLog(this, message);
        renderLog();
    }

    private void recordFromWorker(String message) {
        AppSettings.appendOperationLog(this, message);
        runOnUiThread(this::renderLog);
    }

    private void status(String message) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(message);
            }
        });
    }

    private TextView plugIcon() {
        TextView icon = text("⏻", 22, Color.WHITE, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(COLOR_PRIMARY, 14));
        return icon;
    }

    private TextView stateBadge() {
        Boolean on = lastKnownPower;
        if (on == null) {
            on = AppSettings.lastCommandOn(this);
        }
        String value = on == null ? "状态未知" : (on ? "当前开启" : "当前关闭");
        TextView badge = text(value, 12, COLOR_PRIMARY, Typeface.BOLD);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        badge.setBackground(rounded(COLOR_SOFT, 999));
        return badge;
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

    private TextView metric(String label, String value) {
        TextView view = text(label + "\n" + value, 14, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
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

    private Button dangerButton(String text) {
        Button button = button(text);
        button.setTextColor(0xffb42318);
        button.setBackground(rounded(0xfffff1f1, 14));
        return button;
    }

    private Button compactButton(String text) {
        Button button = secondaryButton(text);
        button.setTextSize(13);
        button.setMinHeight(dp(38));
        button.setMinimumHeight(dp(38));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
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

    private GradientDrawable roundedStroke(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private Integer readPercent(EditText input) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
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
