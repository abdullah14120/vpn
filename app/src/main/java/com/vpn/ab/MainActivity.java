package com.vpn.ab;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.vpn.ab.core.ShieldStatus;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // عناصر الواجهة المحدثة
    private View viewGlow, statusIndicator;
    private ImageView imgStatus;
    private TextView txtStatusMain, txtStatusMini, txtBlockedCount, txtThreatCount, txtSecurityLog;
    private MaterialButton btnStart;
    
    private Vibrator vibrator;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActive = false;
    private ObjectAnimator pulseAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupAnimations();
        setupInitialState();

        btnStart.setOnClickListener(v -> toggleShield());
        
        // مراقبة العدادات وتحديث السجل الحي
        startSystemMonitoring();
    }

    private void initViews() {
        viewGlow = findViewById(R.id.viewGlow);
        statusIndicator = findViewById(R.id.statusIndicator);
        imgStatus = findViewById(R.id.imgStatus);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtStatusMini = findViewById(R.id.txtStatusMini);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        txtThreatCount = findViewById(R.id.txtThreatCount);
        txtSecurityLog = findViewById(R.id.txtSecurityLog);
        btnStart = findViewById(R.id.btnStart);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupAnimations() {
        // إنشاء تأثير النبض (Pulse) للتوهج خلف الدرع
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
                viewGlow,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.4f),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.4f),
                PropertyValuesHolder.ofFloat("alpha", 0.3f, 0.0f)
        );
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ObjectAnimator.RESTART);
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        updateUI(isActive, false);
        addToLog("SYSTEM: Initialize Core Shield...");
        addToLog("SYSTEM: Identity Verified [" + android.os.Build.MODEL + "]");
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        
        if (vibrator != null) vibrator.vibrate(isActive ? 100 : 50);

        updateUI(isActive, true);
        addToLog(isActive ? "PROTOCOL: Alpha-Shield Engaged" : "PROTOCOL: System Isolation Active");
    }

    private void updateUI(boolean active, boolean animate) {
        int cyan = Color.parseColor("#00FFFF");
        int red = Color.parseColor("#FF3B30");
        int dimGray = Color.parseColor("#8E8E93");
        int targetColor = active ? cyan : red;

        txtStatusMain.setText(active ? "درع الحماية نشط" : "النظام في انتظار الأوامر");
        txtStatusMini.setText(active ? "STATUS: PROTECTED" : "STATUS: STANDBY");
        txtStatusMini.setTextColor(active ? Color.GREEN : dimGray);
        btnStart.setText(active ? "تعطيل بروتوكول الحماية" : "تفعيل بروتوكol الحماية");

        if (active) {
            pulseAnimator.start();
            statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
        } else {
            pulseAnimator.cancel();
            viewGlow.setAlpha(0f);
            statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        }

        if (animate) {
            animateColorChange(btnStart, targetColor);
            animateColorChange(imgStatus, targetColor);
            
            // تحريك الأيقونة
            imgStatus.animate().rotationY(active ? 360f : 0f).setDuration(500).start();
        } else {
            btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
            imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));
        }

        imgStatus.setImageResource(active ? 
                android.R.drawable.ic_lock_idle_lock : 
                android.R.drawable.ic_lock_power_off);
    }

    private void addToLog(String message) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String currentLog = txtSecurityLog.getText().toString();
        // نبقي آخر 10 أسطر فقط لضمان الأداء
        String newLog = "> [" + timeStamp + "] " + message + "\n" + currentLog;
        if (newLog.length() > 500) newLog = newLog.substring(0, 500);
        txtSecurityLog.setText(newLog);
    }

    private void startSystemMonitoring() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int blocked = ShieldStatus.getBlockedCount(MainActivity.this);
                
                // تحديث العداد بتأثير "العداد المتصاعد"
                if (!txtBlockedCount.getText().toString().equals(String.valueOf(blocked))) {
                    animateNumber(txtBlockedCount, Integer.parseInt(txtBlockedCount.getText().toString()), blocked);
                    addToLog("INTERCEPT: Security Packet Blocked");
                    if (vibrator != null) vibrator.vibrate(20);
                }
                
                handler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private void animateNumber(TextView view, int start, int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(500);
        animator.addUpdateListener(animation -> view.setText(animation.getAnimatedValue().toString()));
        animator.start();
    }

    private void animateColorChange(final View view, int targetColor) {
        int colorFrom = view instanceof MaterialButton ? 
                ((MaterialButton) view).getBackgroundTintList().getDefaultColor() : 
                ((ImageView) view).getImageTintList().getDefaultColor();

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, targetColor);
        colorAnimation.setDuration(500);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(color));
            } else {
                ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
            }
        });
        colorAnimation.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
