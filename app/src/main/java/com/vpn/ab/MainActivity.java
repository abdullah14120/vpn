package com.vpn.ab;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.vpn.ab.proxy.LocalProxyServer;

public class MainActivity extends AppCompatActivity {

    private LocalProxyServer proxyServer;
    private boolean isRunning = false;
    private int blockedCount = 0;
    private final int PROXY_PORT = 8888; // تغيير المنفذ إلى 8888 احتياطياً

    // عناصر الواجهة
    private RelativeLayout mainLayout;
    private TextView txtStatusMain, txtDescription, txtBlockedCount;
    private ImageView imgStatus;
    private Button btnStart;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ربط العناصر البرمجية بالواجهة
        mainLayout = findViewById(R.id.mainLayout);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtDescription = findViewById(R.id.txtDescription);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        imgStatus = findViewById(R.id.imgStatus);
        btnStart = findViewById(R.id.btnStart);
        
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        createNotificationChannel();

        // تعيين الحالة الافتراضية
        updateUI(false);

        btnStart.setOnClickListener(v -> {
            if (!isRunning) {
                startProtection();
            } else {
                stopProtection();
            }
        });
    }

    private void startProtection() {
        // تشغيل السيرفر في Thread منفصل لضمان استقرار التطبيق ومنع ERR_CONNECTION_REFUSED
        new Thread(() -> {
            try {
                proxyServer = new LocalProxyServer(PROXY_PORT, MainActivity.this);
                proxyServer.start();

                runOnUiThread(() -> {
                    isRunning = true;
                    updateUI(true);
                    showNotification("الدرع النشط", "الحماية تعمل الآن عبر المنفذ " + PROXY_PORT);
                    if (vibrator != null) vibrator.vibrate(100);
                    Toast.makeText(this, "🛡️ تم تشغيل السيرفر بنجاح", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "فشل التشغيل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void stopProtection() {
        if (proxyServer != null) {
            proxyServer.stopServer();
            proxyServer = null;
        }

        isRunning = false;
        updateUI(false);
        showNotification("تنبيه أمني", "تم إيقاف درع الحماية ⚠️");
    }

    private void updateUI(boolean active) {
        if (active) {
            mainLayout.setBackgroundColor(Color.parseColor("#0A1F11"));
            txtStatusMain.setText("النظام محمي");
            txtStatusMain.setTextColor(Color.parseColor("#4CAF50"));
            txtDescription.setText("الوكيل نشط (127.0.0.1:" + PROXY_PORT + ")");
            imgStatus.setImageResource(android.R.drawable.checkbox_on_background);
            imgStatus.setColorFilter(Color.parseColor("#4CAF50"));
            btnStart.setText("إيقاف الدرع");
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935")));
        } else {
            mainLayout.setBackgroundColor(Color.parseColor("#1A0A0A"));
            txtStatusMain.setText("النظام غير محمي");
            txtStatusMain.setTextColor(Color.parseColor("#FF5252"));
            txtDescription.setText("تنبيه: اضبط واتساب على المنفذ " + PROXY_PORT);
            imgStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            imgStatus.setColorFilter(Color.parseColor("#FF5252"));
            btnStart.setText("تفعيل درع الحماية");
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        }
    }

    public void incrementBlockedCount() {
        runOnUiThread(() -> {
            blockedCount++;
            txtBlockedCount.setText(String.valueOf(blockedCount));
            Toast.makeText(MainActivity.this, "🛡️ تم حظر محاولة تتبع!", Toast.LENGTH_SHORT).show();
            if (vibrator != null) vibrator.vibrate(50);
        });
    }

    private void showNotification(String title, String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "shield_channel")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        
        if (manager != null) {
            manager.notify(1, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "shield_channel", 
                    "حالة الدرع الأمني", 
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
}
