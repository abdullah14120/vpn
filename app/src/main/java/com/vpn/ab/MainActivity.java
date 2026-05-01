package com.vpn.ab;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ImageView imgStatus, iconShield;
    private TextView txtStatusMain, txtDescription, txtBlockedCount;
    private MaterialButton btnStart;
    
    // عناصر واجهة الانتظار
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
        
        // البدء بمراقبة حالة الترخيص من السيرفر (للتفعيل اللحظي)
        startLicenseObserver();
        
        // التحقق من حالة العرض (هل هو مفعل أم ينتظر؟)
        checkLicenseState();

        btnStart.setOnClickListener(v -> toggleShield());
        startCounterMonitor();
    }

    private void initViews() {
        imgStatus = findViewById(R.id.imgStatus);
        iconShield = findViewById(R.id.iconShield);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtDescription = findViewById(R.id.txtDescription);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        btnStart = findViewById(R.id.btnStart);
        recyclerSecurityLog = findViewById(R.id.recyclerSecurityLog);
        
        // ربط عناصر الانتظار (تأكد من وجودها في XML أو استخدم findViewById)
        layoutPending = findViewById(R.id.layoutPending); 
        txtPendingStatus = findViewById(R.id.txtPendingStatus);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void checkLicenseState() {
        if (ShieldStatus.isLicenseValid(this)) {
            // إذا كان مرخصاً، نظهر واجهة الدرع ونخفي الانتظار
            showShieldUI(true);
            setupInitialState();
        } else {
            // إذا لم يكن مرخصاً، نظهر واجهة الانتظار ونخفي أزرار الدرع
            showShieldUI(false);
            txtPendingStatus.setText("⏳ في انتظار موافقة الأدمن على طلبك...");
        }
    }

    private void showShieldUI(boolean isLicensed) {
        int visibility = isLicensed ? View.VISIBLE : View.GONE;
        int pendingVisibility = isLicensed ? View.GONE : View.VISIBLE;

        btnStart.setVisibility(visibility);
        imgStatus.setVisibility(visibility);
        txtStatusMain.setVisibility(visibility);
        txtDescription.setVisibility(visibility);
        
        if (layoutPending != null) layoutPending.setVisibility(pendingVisibility);
    }

    private void startLicenseObserver() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    if (isActivated != null && isActivated) {
                        // تفعيل محلي فوراً
                        ShieldStatus.activateLicenseLocally(MainActivity.this);
                        
                        // تحديث الواجهة فوراً دون إعادة تشغيل
                        runOnUiThread(() -> {
                            showShieldUI(true);
                            setupInitialState();
                            addToLog("SYSTEM: تم تفعيل الترخيص بنجاح من قبل الأدمن.");
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // باقي الدوال (setupTerminal, setupInitialState, toggleShield, addToLog, updateUI, animateColorChange, startCounterMonitor) تبقى كما هي...
    // مع إضافة دالة setupInitialState للتأكد من الحالة
    
    private void setupTerminal() {
        logAdapter = new LogAdapter(logList);
        recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSecurityLog.setAdapter(logAdapter);
        addToLog("SYSTEM: تم تشغيل بروتوكول حماية الواتساب.");
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        lastKnownCount = ShieldStatus.getBlockedCount(this); 
        updateUI(isActive, false);
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        if (vibrator != null) vibrator.vibrate(isActive ? 70 : 30);
        updateUI(isActive, true);
        addToLog(isActive ? "PROTOCOL: تم تفعيل درع الحماية." : "PROTOCOL: الحماية في وضع الاستعداد.");
    }

    private void addToLog(String message) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String entry = "> [" + timeStamp + "] " + message;
        logList.add(0, entry);
        runOnUiThread(() -> {
            logAdapter.notifyItemInserted(0);
            recyclerSecurityLog.scrollToPosition(0);
        });
    }

    private void updateUI(boolean active, boolean animate) {
        int colorRed = Color.parseColor("#FF5252");
        int colorGreen = Color.parseColor("#4CAF50");
        int targetColor = active ? colorGreen : colorRed;

        txtStatusMain.setText(active ? "الواتساب محمي" : "الواتساب غير محمي");
        btnStart.setText(active ? "إيقاف الدرع النشط" : "تفعيل الدرع النشط");

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
        if (view instanceof MaterialButton) colorFrom = ((MaterialButton) view).getBackgroundTintList().getDefaultColor();
        else if (view instanceof TextView) colorFrom = ((TextView) view).getCurrentTextColor();
        else if (view instanceof ImageView) colorFrom = ((ImageView) view).getImageTintList().getDefaultColor();

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

    private void startCounterMonitor() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int currentCount = ShieldStatus.getBlockedCount(MainActivity.this);
                if (currentCount > lastKnownCount) {
                    int diff = currentCount - lastKnownCount;
                    addToLog("INTERCEPT: تم إحباط محاولة حظر حسابك (تقرير أمني) عدد: " + diff);
                    txtBlockedCount.setText(String.valueOf(currentCount));
                    if (vibrator != null) vibrator.vibrate(40);
                    lastKnownCount = currentCount;
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
