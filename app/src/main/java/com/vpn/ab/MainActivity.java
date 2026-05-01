package com.vpn.ab;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.vpn.ab.core.LogAdapter; 
import com.vpn.ab.core.ShieldStatus;
import com.vpn.ab.core.ShieldForegroundService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ImageView imgStatus;
    private TextView txtStatusMain, txtDescription, txtBlockedCount;
    private MaterialButton btnStart;
    
    private LinearLayout layoutPending;
    private TextView txtPendingStatus;

    private RecyclerView recyclerSecurityLog;
    private LogAdapter logAdapter;
    private List<String> logList = new ArrayList<>();
    private int lastKnownCount = 0; 

    private Vibrator vibrator;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActive = false;
    private DatabaseReference userRef;
    private DatabaseReference requestRef;
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        userRef = FirebaseDatabase.getInstance().getReference("Users").child(androidId);
        requestRef = FirebaseDatabase.getInstance().getReference("Requests").child(androidId);

        // 1. الفحص الذكي: إذا لم يكن مفعل محلياً، نتحقق من السيرفر قبل الطرد
        if (!ShieldStatus.isLicenseValid(this)) {
            checkIfRequestExists();
        } else {
            initializeMainUI();
        }
    }

    private void initializeMainUI() {
        setContentView(R.layout.activity_main);
        initViews();
        setupTerminal();
        startLicenseObserver();
        setupInitialState();
        requestBatteryOptimizationIgnore();
        btnStart.setOnClickListener(v -> toggleShield());
    }

    private void checkIfRequestExists() {
        // التحقق هل للمستخدم طلب تفعيل في السيرفر؟
        requestRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // الطلب موجود، نفتح الواجهة الرئيسية ونعرض حالة الانتظار
                    setContentView(R.layout.activity_main);
                    initViews();
                    setupTerminal();
                    showShieldUI(false);
                    txtPendingStatus.setText(R.string.request_pending);
                    startLicenseObserver(); // نراقب اللحظة التي يوافق فيها الأدمن
                } else {
                    // لا يوجد طلب ولا تفعيل، نرسله لواجهة التفعيل
                    navigateToActivation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                navigateToActivation();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ShieldStatus.isLicenseValid(this)) {
            if (txtBlockedCount != null) {
                refreshStats();
                startCounterMonitor();
            }
        }
    }

    private void navigateToActivation() {
        Intent intent = new Intent(this, ActivationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initViews() {
        imgStatus = findViewById(R.id.imgStatus);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtDescription = findViewById(R.id.txtDescription);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        btnStart = findViewById(R.id.btnStart);
        recyclerSecurityLog = findViewById(R.id.recyclerSecurityLog);
        layoutPending = findViewById(R.id.layoutPending); 
        txtPendingStatus = findViewById(R.id.txtPendingStatus);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupTerminal() {
        if (logAdapter == null) {
            logAdapter = new LogAdapter(logList);
            recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
            recyclerSecurityLog.setAdapter(logAdapter);
        }
        addToLog("SYSTEM: تم تفعيل بروتوكول الرصد الأمني.");
    }

    private void refreshStats() {
        int currentSavedCount = ShieldStatus.getBlockedCount(this);
        txtBlockedCount.setText(String.valueOf(currentSavedCount));
        lastKnownCount = currentSavedCount;
    }

    private void checkLicenseState() {
        if (ShieldStatus.isLicenseValid(this)) {
            showShieldUI(true);
            setupInitialState();
        } else {
            showShieldUI(false);
            txtPendingStatus.setText(R.string.request_pending);
        }
    }

    private void showShieldUI(boolean isLicensed) {
        int visibility = isLicensed ? View.VISIBLE : View.GONE;
        int pendingVisibility = isLicensed ? View.GONE : View.VISIBLE;
        
        btnStart.setVisibility(visibility);
        imgStatus.setVisibility(visibility);
        txtStatusMain.setVisibility(visibility);
        txtDescription.setVisibility(visibility);
        
        // التأكد من وجود البطاقات في XML
        View cardStats = findViewById(R.id.cardStats);
        View cardTerminal = findViewById(R.id.cardTerminal);
        View lblTerminal = findViewById(R.id.lblTerminal);
        
        if (cardStats != null) cardStats.setVisibility(visibility);
        if (cardTerminal != null) cardTerminal.setVisibility(visibility);
        if (lblTerminal != null) lblTerminal.setVisibility(visibility);
        
        if (layoutPending != null) layoutPending.setVisibility(pendingVisibility);
    }

    private void startLicenseObserver() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    if (isActivated != null && isActivated) {
                        if (!ShieldStatus.isLicenseValid(MainActivity.this)) {
                            ShieldStatus.activateLicenseLocally(MainActivity.this);
                            runOnUiThread(() -> {
                                showShieldUI(true);
                                setupInitialState();
                                addToLog("SYSTEM: تم استقبال تصريح النشاط.. الدرع مفعّل.");
                            });
                        }
                    } else if (ShieldStatus.isLicenseValid(MainActivity.this)) {
                        // إذا تم سحب الترخيص والأدمن أزال التفعيل من قاعدة البيانات
                        ShieldStatus.setProtectionState(MainActivity.this, false);
                        // هنا لا نطرده فوراً بل نمسح التفعيل المحلي ونعيد فحصه
                        getSharedPreferences("security_prefs", MODE_PRIVATE).edit().clear().apply();
                        navigateToActivation();
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        refreshStats();
        updateUI(isActive, false);
        if (isActive) startShieldService();
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        
        if (isActive) {
            startShieldService();
            addToLog("PROTOCOL: تم تشغيل الخدمة الدائمة.");
        } else {
            stopShieldService();
            addToLog("PROTOCOL: النظام في وضع الاستعداد.");
        }

        if (vibrator != null) vibrator.vibrate(isActive ? 70 : 30);
        updateUI(isActive, true);
    }

    private void startShieldService() {
        Intent serviceIntent = new Intent(this, ShieldForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopShieldService() {
        stopService(new Intent(this, ShieldForegroundService.class));
    }

    private void startCounterMonitor() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int currentCount = ShieldStatus.getBlockedCount(MainActivity.this);
                if (currentCount > lastKnownCount) {
                    int diff = currentCount - lastKnownCount;
                    addToLog("INTERCEPT: تم إحباط " + diff + " محاولة تجسس جديدة.");
                    txtBlockedCount.setText(String.valueOf(currentCount));
                    if (vibrator != null) vibrator.vibrate(40);
                    lastKnownCount = currentCount;
                }
                handler.postDelayed(this, 2000);
            }
        }, 1000);
    }

    private void addToLog(String message) {
        if (logAdapter == null) return;
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String entry = "> [" + timeStamp + "] " + message;
        
        runOnUiThread(() -> {
            logList.add(0, entry);
            logAdapter.notifyItemInserted(0);
            recyclerSecurityLog.scrollToPosition(0);
        });
    }

    private void updateUI(boolean active, boolean animate) {
        int colorRed = Color.parseColor("#FF5252");
        int colorGreen = Color.parseColor("#4CAF50");
        int targetColor = active ? colorGreen : colorRed;
        
        txtStatusMain.setText(active ? R.string.status_protected : R.string.status_unprotected);
        btnStart.setText(active ? R.string.btn_stop_shield : R.string.btn_start_shield);

        if (animate) {
            animateColorChange(btnStart, targetColor);
            animateColorChange(imgStatus, targetColor);
            animateColorChange(txtStatusMain, targetColor);
        } else {
            btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
            imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));
            txtStatusMain.setTextColor(targetColor);
        }
    }

    private void animateColorChange(final Object view, int targetColor) {
        int colorFrom = Color.RED;
        if (view instanceof MaterialButton) {
            ColorStateList list = ((MaterialButton) view).getBackgroundTintList();
            if (list != null) colorFrom = list.getDefaultColor();
        } else if (view instanceof TextView) {
            colorFrom = ((TextView) view).getCurrentTextColor();
        } else if (view instanceof ImageView) {
            ColorStateList list = ((ImageView) view).getImageTintList();
            if (list != null) colorFrom = list.getDefaultColor();
        }

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, targetColor);
        colorAnimation.setDuration(400);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            if (view instanceof MaterialButton) ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(color));
            else if (view instanceof TextView) ((TextView) view).setTextColor(color);
            else if (view instanceof ImageView) ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
        });
        colorAnimation.start();
    }

    private void requestBatteryOptimizationIgnore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try { startActivity(intent); } catch (Exception e) {}
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
