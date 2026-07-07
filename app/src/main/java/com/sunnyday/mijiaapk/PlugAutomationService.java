package com.sunnyday.mijiaapk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlugAutomationService extends Service {
    static final String ACTION_REFRESH = "com.sunnyday.mijiaapk.REFRESH";
    static final String ACTION_STOP = "com.sunnyday.mijiaapk.STOP";
    static final String BROADCAST_LOG = "com.sunnyday.mijiaapk.LOG";
    static final String EXTRA_LOG = "log";

    private static final String CHANNEL_ID = "plug_automation";
    private static final int NOTIFICATION_ID = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean commandRunning = new AtomicBoolean(false);
    private BroadcastReceiver batteryReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, notification("等待电量变化"));
        registerBatteryReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AppSettings.save(
                    this,
                    AppSettings.plugIp(this),
                    AppSettings.plugToken(this),
                    AppSettings.lowThreshold(this),
                    AppSettings.highThreshold(this),
                    false
            );
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!AppSettings.automationEnabled(this) || !AppSettings.hasValidPlugConfig(this)) {
            log("自动控制未启用或插座配置不完整");
            stopSelf();
            return START_NOT_STICKY;
        }

        evaluateCurrentBattery();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                evaluateBatteryIntent(intent);
            }
        };
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void evaluateCurrentBattery() {
        Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            evaluateBatteryIntent(sticky);
        }
    }

    private void evaluateBatteryIntent(Intent intent) {
        if (!AppSettings.automationEnabled(this) || !AppSettings.hasValidPlugConfig(this)) {
            return;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return;
        }

        int percent = Math.round(level * 100f / scale);
        int high = AppSettings.highThreshold(this);
        int low = AppSettings.lowThreshold(this);

        updateNotification("当前电量 " + percent + "%，阈值 " + low + "% / " + high + "%");

        if (percent >= high) {
            requestPlugState(false, "电量达到 " + percent + "%，关闭插座");
        } else if (percent <= low) {
            requestPlugState(true, "电量降到 " + percent + "%，开启插座");
        }
    }

    private void requestPlugState(boolean on, String reason) {
        Boolean last = AppSettings.lastCommandOn(this);
        if (last != null && last == on) {
            return;
        }
        if (!commandRunning.compareAndSet(false, true)) {
            return;
        }

        log(reason);
        executor.execute(() -> {
            try {
                MiioLanClient client = new MiioLanClient(
                        AppSettings.plugIp(this),
                        AppSettings.plugToken(this)
                );
                client.setPower(on);
                AppSettings.rememberCommand(this, on);
                log(on ? "插座已开启" : "插座已关闭");
            } catch (Exception e) {
                log("控制失败: " + e.getMessage());
            } finally {
                commandRunning.set(false);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "插座自动控制",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("根据手机电量自动控制米家智能插座");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stop = new Intent(this, PlugAutomationService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(
                this,
                1,
                stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("米家插座电量控制")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_launcher, "停止", stopIntent)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(text));
    }

    private void log(String message) {
        String now = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        Intent intent = new Intent(BROADCAST_LOG)
                .setPackage(getPackageName())
                .putExtra(EXTRA_LOG, now + " " + message);
        sendBroadcast(intent);
        updateNotification(message);
    }
}
