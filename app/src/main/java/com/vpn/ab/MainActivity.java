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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.android.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;
import com.vpn.ab.core.LogAdapter;
import com.vpn.ab.core.ShieldStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // عناصر الواجهة المحدثة
    private View statusIndicator;
    private ImageView imgStatus;
    private TextView txtStatusMain, txtStatusMini, txtBlockedCount, txtThreatCount;
    private MaterialButton btnStart;
    
    // المكونات الجديدة (Lottie & RecyclerView)
    private LottieAnimationView lottieShield;
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
        
        // مراقبة العدادات وتحديث السجل الحي
        startSystemMonitoring();
    }

    private void initViews() {
        statusIndicator = findViewById(R.id.statusIndicator);
        imgStatus = findViewById(R.id.imgStatus);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtStatusMini = findViewById(R.id.txtStatusMini);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        txtThreatCount = findViewById(R.id.txtThreatCount);
        btnStart = findViewById(R.id.btnStart);
        
        // ربط المكونات المضافة حديثاً
        lottieShield = findViewById(R.id.lottieShield);
        recyclerSecurityLog = findViewById(R.id.recyclerSecurityLog);
        
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupRecyclerView() {
        // إعداد المحول والقائمة للسجل الأمني
        logAdapter = new LogAdapter(logList);
        recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSecurityLog.setAdapter(logAdapter);
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
        btnStart.setText(active ? "تعطيل بروتوكول الحماية" : "تفعيل بروتوكول الحماية");

        if (active) {
            lottieShield.playAnimation();
            lottieShield.setVisibility(View.VISIBLE);
            statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN));
        } else {
            lottieShield.cancelAnimation();
            lottieShield.setVisibility(View.INVISIBLE);
            statusIndicator.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
        }

        if (animate) {
            animateColorChange(btnStart, targetColor);
            animateColorChange(imgStatus, targetColor);
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
        String entry = "> [" + timeStamp + "] " + message;

        // إضافة السطر الجديد في أعلى القائمة لتسهيل الرؤية
        logList.add(0, entry);
        
        // الحفاظ على أداء التطبيق عبر مسح الأسطر القديمة جداً
        if (logList.size() > 50) {
            logList.remove(logList.size() - 1);
        }

        // تحديث الواجهة من خلال الـ Adapter
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
                
                // تحديث العداد بتأثير الحركة إذا تغيرت القيمة
                String currentStr = txtBlockedCount.getText().toString();
                int currentVal = currentStr.isEmpty() ? 0 : Integer.parseInt(currentStr);
                
                if (currentVal != blocked) {
                    animateNumber(txtBlockedCount, currentVal, blocked);
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
