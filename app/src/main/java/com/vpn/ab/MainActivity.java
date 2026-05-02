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
import android.widget.Toast;
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
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        userRef = FirebaseDatabase.getInstance().getReference("Users").child(androidId);

        initViews();
        setupTerminal();
        checkLicenseAndInitialize();
    }

    private void checkLicenseAndInitialize() {
        if (ShieldStatus.isLicenseValid(this)) {
            showShieldUI(true);
            setupInitialState();
            startLicenseObserver(); 
        } else {
            showShieldUI(false);
            txtPendingStatus.setText("جاري التحقق من السيرفر...");
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                        if (Boolean.TRUE.equals(isActivated)) {
                            ShieldStatus.activateLicenseLocally(MainActivity.this);
                            showShieldUI(true);
                            setupInitialState();
                        } else {
                            txtPendingStatus.setText("طلبك قيد المراجعة لدى الإدارة...");
                        }
                        startLicenseObserver();
                    } else {
                        navigateToActivation();
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void startLicenseObserver() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(isActivated) && !ShieldStatus.isLicenseValid(MainActivity.this)) {
                        ShieldStatus.activateLicenseLocally(MainActivity.this);
                        updateUIOnActivation();
                    } else if (isActivated != null && !isActivated && ShieldStatus.isLicenseValid(MainActivity.this)) {
                        handleLicenseRevoked();
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateUIOnActivation() {
        runOnUiThread(() -> {
            showShieldUI(true);
            setupInitialState();
            addToLog("SYSTEM: تم استقبال تصريح النشاط.. درع حماية الواتساب.");
        });
    }

    private void handleLicenseRevoked() {
        ShieldStatus.setProtectionState(this, false);
        getSharedPreferences("security_prefs", MODE_PRIVATE).edit().clear().apply();
        navigateToActivation();
    }

    private void showShieldUI(boolean isLicensed) {
        int visibility = isLicensed ? View.VISIBLE : View.GONE;
        int pendingVisibility = isLicensed ? View.GONE : View.VISIBLE;
        
        btnStart.setVisibility(visibility);
        imgStatus.setVisibility(visibility);
        txtStatusMain.setVisibility(visibility);
        txtDescription.setVisibility(visibility);
        
        if (findViewById(R.id.cardStats) != null) findViewById(R.id.cardStats).setVisibility(visibility);
        if (findViewById(R.id.cardTerminal) != null) findViewById(R.id.cardTerminal).setVisibility(visibility);
        if (findViewById(R.id.lblTerminal) != null) findViewById(R.id.lblTerminal).setVisibility(visibility);
        
        if (layoutPending != null) layoutPending.setVisibility(pendingVisibility);
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
        
        btnStart.setOnClickListener(v -> toggleShield());
        requestBatteryOptimizationIgnore();
    }

    private void setupTerminal() {
        if (logAdapter == null) {
            logAdapter = new LogAdapter(logList);
            recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
            recyclerSecurityLog.setAdapter(logAdapter);
        }
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        lastKnownCount = ShieldStatus.getBlockedCount(this); 
        txtBlockedCount.setText(String.valueOf(lastKnownCount));
        updateUI(isActive, false);
        if (isActive) {
            startShieldService();
            startCounterMonitor(); 
        }
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        
        if (isActive) {
            startShieldService();
            startCounterMonitor();
            addToLog("PROTOCOL: تم تفعيل درع الحماية النشط.");
        } else {
            stopShieldService();
            handler.removeCallbacksAndMessages(null);
            addToLog("PROTOCOL: الدرع في وضع الاستعداد.");
        }

        if (vibrator != null) vibrator.vibrate(isActive ? 70 : 30);
        updateUI(isActive, true);
    }

    /**
     * العداد الاحترافي: يقوم بمراقبة ملف الشيرد بريفرنسز كل ثانية (1000ms)
     * ويحدث الواجهة ويضيف للسجل الأمني عند أي زيادة.
     */
    private void startCounterMonitor() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // جلب القيمة المخزنة التي يكتبها الواتساب عبر ShieldProvider
                int currentCount = ShieldStatus.getBlockedCount(MainActivity.this);
                
                if (currentCount > lastKnownCount) {
                    int diff = currentCount - lastKnownCount;
                    lastKnownCount = currentCount; 
                    
                    runOnUiThread(() -> {
                        txtBlockedCount.setText(String.valueOf(lastKnownCount));
                        addToLog("INTERCEPT: تم حجب " + diff + " محاولة إرسال تقرير أمني.");
                        if (vibrator != null) vibrator.vibrate(40);
                    });
                }
                
                // الاستمرار في الفحص (تكرار كل 1 ثانية)
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void addToLog(String message) {
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
        
        txtStatusMain.setText(active ? "الواتساب محمي" : "الواتساب غير محمي");
        btnStart.setText(active ? "إيقاف الدرع" : "تشغيل الدرع");
        
        if (active) {
            txtDescription.setText("درع الحماية نشط الآن. يتم فحص وتصفية كافة البيانات الصادرة لضمان عدم حظر الواتساب.");
        } else {
            txtDescription.setText("درع الحماية متوقف حالياً. واتساب لن يرسل أو يستقبل أي بيانات لضمان خصوصيتك.");
        }

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
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), Color.GRAY, targetColor);
        colorAnimation.setDuration(400);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            if (view instanceof MaterialButton) ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(color));
            else if (view instanceof TextView) ((TextView) view).setTextColor(color);
            else if (view instanceof ImageView) ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
        });
        colorAnimation.start();
    }

    private void navigateToActivation() {
        Intent intent = new Intent(this, ActivationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void startShieldService() {
        Intent serviceIntent = new Intent(this, ShieldForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private void stopShieldService() {
        stopService(new Intent(this, ShieldForegroundService.class));
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
    protected void onPause() { super.onPause(); handler.removeCallbacksAndMessages(null); }
    
    @Override
    protected void onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null); }
}
