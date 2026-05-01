package com.vpn.ab.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ShieldForegroundService extends Service {

    private static final String CHANNEL_ID = "ShieldServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        
        // إنشاء الإشعار الدائم الذي يمنع النظام من قتل التطبيق
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("درع الحماية نشط 🛡️")
                .setContentText("نظام الرصد يعمل في الخلفية لحماية حسابك.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock) // يمكنك استبداله بأيقونة تطبيقك
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // بدء الخدمة كـ Foreground
        startForeground(1, notification);

        // START_STICKY يخبر النظام بإعادة تشغيل الخدمة فوراً إذا توقفت لأي سبب
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Shield Protection Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
