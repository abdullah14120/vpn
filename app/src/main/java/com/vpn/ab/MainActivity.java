package com.vpn.ab;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
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
        
        // --- القاعدة الذهبية للتثبيت ---
        checkFinalActivation();
    }

    private void checkFinalActivation() {
        // 1. الفحص المحلي الصامت: إذا كان مفعلاً سابقاً، افتح الواجهة فوراً وللأبد
        if (ShieldStatus.isLicenseValid(this)) {
            renderActiveUI();
            return;
        }

        // 2. إذا لم يكن مفعلاً محلياً، نظهر واجهة الانتظار ونفحص السيرفر "لمرة واحدة فقط"
        renderWaitingUI();
        
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(isActivated)) {
                        // الموافقة تمت! نثبت التفعيل محلياً للأبد
                        ShieldStatus.activateLicenseLocally(MainActivity.this);
                        renderActiveUI();
                    } else {
                        // الطلب موجود ولكن بانتظار الأدمن
                        txtPendingStatus.setText("⏳ طلبك بانتظار تفعيل الإدارة.. لا تغلق التطبيق.");
                    }
                } else {
                    // لا يوجد طلب أصلاً -> طرد لواجهة التفعيل
                    navigateToActivation();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void renderActiveUI() {
        // إخفاء واجهة الانتظار وإظهار الدرع للأبد
        if (layoutPending != null) layoutPending.setVisibility(View.GONE);
        
        btnStart.setVisibility(View.VISIBLE);
        imgStatus.setVisibility(View.VISIBLE);
        txtStatusMain.setVisibility(View.VISIBLE);
        txtDescription.setVisibility(View.VISIBLE);
        
        if (findViewById(R.id.cardStats) != null) findViewById(R.id.cardStats).setVisibility(View.VISIBLE);
        if (findViewById(R.id.cardTerminal) != null) findViewById(R.id.cardTerminal).setVisibility(View.VISIBLE);
        if (findViewById(R.id.lblTerminal) != null) findViewById(R.id.lblTerminal).setVisibility(View.VISIBLE);
        
        setupInitialState();
    }

    private void renderWaitingUI() {
        // إخفاء كل شيء وإظهار الانتظار فقط
        btnStart.setVisibility(View.GONE);
        imgStatus.setVisibility(View.GONE);
        txtStatusMain.setVisibility(View.GONE);
        txtDescription.setVisibility(View.GONE);
        
        if (findViewById(R.id.cardStats) != null) findViewById(R.id.cardStats).setVisibility(View.GONE);
        if (findViewById(R.id.cardTerminal) != null) findViewById(R.id.cardTerminal).setVisibility(View.GONE);
        if (findViewById(R.id.lblTerminal) != null) findViewById(R.id.lblTerminal).setVisibility(View.GONE);

        if (layoutPending != null) layoutPending.setVisibility(View.VISIBLE);
    }

    // باقي الدوال (initViews, startCounterMonitor, addToLog) كما هي في مشروعك القديم الناجح
    
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
    }

    private void setupTerminal() {
        logAdapter = new LogAdapter(logList);
        recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSecurityLog.setAdapter(logAdapter);
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        lastKnownCount = ShieldStatus.getBlockedCount(this); 
        txtBlockedCount.setText(String.valueOf(lastKnownCount));
        updateUI(isActive, false);
        if (isActive) startCounterMonitor();
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        if (vibrator != null) vibrator.vibrate(isActive ? 70 : 30);
        updateUI(isActive, true);
        addToLog(isActive ? "PROTOCOL: تم تفعيل درع الحماية." : "PROTOCOL: الحماية في وضع الاستعداد.");
        if (isActive) startCounterMonitor();
        else handler.removeCallbacksAndMessages(null);
    }

    private void startCounterMonitor() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int currentCount = ShieldStatus.getBlockedCount(MainActivity.this);
                if (currentCount > lastKnownCount) {
                    int diff = currentCount - lastKnownCount;
                    addToLog("INTERCEPT: تم حجب " + diff + " محاولة حظر (تقرير أمني).");
                    txtBlockedCount.setText(String.valueOf(currentCount));
                    if (vibrator != null) vibrator.vibrate(40);
                    lastKnownCount = currentCount;
                }
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
        if (!animate) {
            btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
            imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));
            txtStatusMain.setTextColor(targetColor);
        }
    }

    private void navigateToActivation() {
        Intent intent = new Intent(this, ActivationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
