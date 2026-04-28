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

        // تعيين الحالة الافتراضية عند الفتح (متوقف)
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
        try {
            // تشغيل السيرفر وتمرير 'this' لتمكينه من تحديث العداد
            proxyServer = new LocalProxyServer(8080, this);
            proxyServer.start();

            isRunning = true;
            updateUI(true);
            showNotification("الدرع النشط", "حماية الواتساب تعمل الآن بنجاح ✅");
            
            if (vibrator != null) vibrator.vibrate(100); // اهتزاز خفيف للتأكيد
        } catch (Exception e) {
            Toast.makeText(this, "فشل تشغيل السيرفر: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopProtection() {
        if (proxyServer != null) {
            proxyServer.stopServer();
            proxyServer = null;
        }

        isRunning = false;
        updateUI(false);
        showNotification("تنبيه أمني", "تم إيقاف الحماية! واتساب في خطر ⚠️");
    }

    // دالة تحديث الواجهة ديناميكياً
    private void updateUI(boolean active) {
        if (active) {
            mainLayout.setBackgroundColor(Color.parseColor("#0A1F11")); // أخضر ليلي
            txtStatusMain.setText("النظام محمي");
            txtStatusMain.setTextColor(Color.parseColor("#4CAF50"));
            txtDescription.setText("الوكيل الآمن متصل (127.0.0.1:8080)");
            imgStatus.setImageResource(android.R.drawable.checkbox_on_background);
            imgStatus.setColorFilter(Color.parseColor("#4CAF50"));
            btnStart.setText("إيقاف الدرع");
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935")));
        } else {
            mainLayout.setBackgroundColor(Color.parseColor("#1A0A0A")); // أحمر ليلي
            txtStatusMain.setText("النظام غير محمي");
            txtStatusMain.setTextColor(Color.parseColor("#FF5252"));
            txtDescription.setText("تنبيه: قم بتشغيل الحماية لتجنب الحظر ⚠️");
            imgStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            imgStatus.setColorFilter(Color.parseColor("#FF5252"));
            btnStart.setText("تفعيل الحماية الآن");
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        }
    }

    // هذه الدالة يستدعيها كلاس LocalProxyServer عند كل حجب
    public void incrementBlockedCount() {
        runOnUiThread(() -> {
            blockedCount++;
            txtBlockedCount.setText(String.valueOf(blockedCount));
            
            // تفاعل بصري سريع عند الحجب
            Toast.makeText(MainActivity.this, "🛡️ تم حجب تقرير أمني مشبوه!", Toast.LENGTH_SHORT).show();
            
            // اهتزاز خفيف جداً لإشعار المستخدم بالنجاح
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
        
        manager.notify(1, builder.build());
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

    @Override
    protected void onDestroy() {
        // نفضل إبقاء السيرفر يعمل في الخلفية حتى لو أغلق النشاط
        // إلا إذا أردت إغلاقه لضمان توفير الموارد
        super.onDestroy();
    }
}
