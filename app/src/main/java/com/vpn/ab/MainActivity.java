package com.vpn.ab;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.vpn.ab.core.LogAdapter;
import com.vpn.ab.core.ShieldStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // عناصر الواجهة الأساسية
    private View statusIndicator, viewGlow;
    private ImageView imgStatus;
    private TextView txtStatusMain, txtStatusMini, txtBlockedCount, txtThreatCount;
    private MaterialButton btnStart;
    
    // السجل الأمني (RecyclerView)
    private RecyclerView recyclerSecurityLog;
    private LogAdapter logAdapter;
    private List<String> logList = new ArrayList<>();
    
    private Vibrator vibrator;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupInitialState();

        btnStart.setOnClickListener(v -> toggleShield());
        
        // بدء مراقبة النظام وتحديث العدادات
        startSystemMonitoring();
    }

    private void initViews() {
        statusIndicator = findViewById(R.id.statusIndicator);
        viewGlow = findViewById(R.id.viewGlow); // خلفية التوهج
        imgStatus = findViewById(R.id.imgStatus);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtStatusMini = findViewById(R.id.txtStatusMini);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        txtThreatCount = findViewById(R.id.txtThreatCount);
        btnStart = findViewById(R.id.btnStart);
        recyclerSecurityLog = findViewById(R.id.recyclerSecurityLog);
        
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupRecyclerView() {
        logAdapter = new LogAdapter(logList);
        recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSecurityLog.setAdapter(logAdapter);
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        updateUI(isActive, false);
        addToLog("SYSTEM: Core Engine Initialized.");
        addToLog("SYSTEM: Stability Mode Active (Static UI).");
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        
        if (vibrator != null) {
            // اهتزاز مخصص للتشغيل والإيقاف
            vibrator.vibrate(isActive ? 80 : 40);
        }

        updateUI(isActive, true);
        addToLog(isActive ? "PROTOCOL: Shield Engaged." : "PROTOCOL: System Standby.");
    }

    private void updateUI(boolean active, boolean animate) {
        int cyan = Color.parseColor("#00FFFF");
        int red = Color.parseColor("#FF3B30");
        int dimGray = Color.parseColor("#8E8E93");
        int targetColor = active ? cyan : red;

        txtStatusMain.setText(active ? "درع الحماية نشط" : "النظام في انتظار الأوامر");
        txtStatusMini.setText(active ? "STATUS: PROTECTED" : "STATUS: STANDBY");
        txtStatusMini.setTextColor(active ? Color.GREEN : dimGray);
        btnStart.setText(active ? "تعطيل بروتوكول الحماية" : "تفعيل بروتوكول الحماية");

        // إدارة تأثير التوهج (بديل Lottie)
        if (active) {
            viewGlow.setVisibility(View.VISIBLE);
            viewGlow.setAlpha(0.4f);
            statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
            if (animate) startPulseAnimation(); // نبض يدوي عند التشغيل
        } else {
            viewGlow.setVisibility(View.INVISIBLE);
            statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        }

        if (animate) {
            animateColorChange(btnStart, targetColor);
            animateColorChange(imgStatus, targetColor);
            // دوران بسيط للأيقونة عند التبديل
            imgStatus.animate().rotationYBy(360f).setDuration(600).start();
        } else {
            btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
            imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));
        }

        imgStatus.setImageResource(active ? 
                android.R.drawable.ic_lock_idle_lock : 
                android.R.drawable.ic_lock_power_off);
    }

    // تأثير نبضي يدوي للأيقونة لتعويض غياب Lottie
    private void startPulseAnimation() {
        imgStatus.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> imgStatus.animate().scaleX(1f).scaleY(1f).setDuration(300).start())
                .start();
    }

    private void addToLog(String message) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String entry = "> [" + timeStamp + "] " + message;

        logList.add(0, entry);
        if (logList.size() > 50) logList.remove(logList.size() - 1);

        runOnUiThread(() -> {
            logAdapter.notifyItemInserted(0);
            recyclerSecurityLog.scrollToPosition(0);
        });
    }

    private void startSystemMonitoring() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int blocked = ShieldStatus.getBlockedCount(MainActivity.this);
                String currentStr = txtBlockedCount.getText().toString();
                int currentVal = currentStr.isEmpty() ? 0 : Integer.parseInt(currentStr);
                
                if (currentVal != blocked) {
                    animateNumber(txtBlockedCount, currentVal, blocked);
                    addToLog("INTERCEPT: Security Packet Blocked.");
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
        ColorStateList currentTint = (view instanceof MaterialButton) ? 
                ((MaterialButton) view).getBackgroundTintList() : 
                ((ImageView) view).getImageTintList();
        
        int colorFrom = (currentTint != null) ? currentTint.getDefaultColor() : Color.RED;

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, targetColor);
        colorAnimation.setDuration(500);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(color));
            } else if (view instanceof ImageView) {
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
