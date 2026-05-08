package com.vpn.ab;

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
    private View cardStats, cardTerminal, lblTerminal;

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
        // تم توحيد المسار مع تطبيق الأدمن
        userRef = FirebaseDatabase.getInstance().getReference("Requests").child(androidId);

        initViews();
        setupTerminal();
        
        // التحقق من نزاهة التطبيق (الآن يعيد true دائماً في نسخة الـ Debug)
        if (!ShieldStatus.verifyAppIntegrity(this)) {
            finishAffinity();
            return;
        }

        startActivationMonitor();
    }

    /**
     * هذا هو "المحرك" الرئيسي: يراقب السيرفر لحظياً.
     * إذا قام الأدمن بالتفعيل، تفتح الواجهة فوراً.
     */
    private void startActivationMonitor() {
        // 1. الفحص المحلي السريع (إذا كان قد تفعّل سابقاً)
        if (ShieldStatus.isLicenseValidEncrypted(this)) {
            renderActiveUI();
            return;
        }

        renderWaitingUI();

        // 2. المراقب اللحظي للسيرفر
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    
                    if (Boolean.TRUE.equals(isActivated)) {
                        // التفعيل المحلي وتحديث الواجهة
                        ShieldStatus.secureActivate(MainActivity.this, "SERVER_CONFIRMED");
                        renderActiveUI();
                        // إزالة المستمع بعد النجاح لتوفير الموارد
                        userRef.removeEventListener(this);
                    } else {
                        txtPendingStatus.setText("⏳ طلبك قيد المراجعة.. سيفتح الدرع تلقائياً فور تفعيله.");
                    }
                } else {
                    // إذا لم يوجد طلب أصلاً، ننتقل لواجهة إرسال الطلب
                    navigateToActivation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                txtPendingStatus.setText("❌ خطأ في الاتصال بالسيرفر.");
            }
        });
    }

    private void renderActiveUI() {
        runOnUiThread(() -> {
            if (layoutPending != null) layoutPending.setVisibility(View.GONE);
            
            btnStart.setVisibility(View.VISIBLE);
            imgStatus.setVisibility(View.VISIBLE);
            txtStatusMain.setVisibility(View.VISIBLE);
            txtDescription.setVisibility(View.VISIBLE);
            cardStats.setVisibility(View.VISIBLE);
            cardTerminal.setVisibility(View.VISIBLE);
            lblTerminal.setVisibility(View.VISIBLE);
            
            setupInitialState();
        });
    }

    private void renderWaitingUI() {
        runOnUiThread(() -> {
            btnStart.setVisibility(View.GONE);
            imgStatus.setVisibility(View.GONE);
            txtStatusMain.setVisibility(View.GONE);
            txtDescription.setVisibility(View.GONE);
            cardStats.setVisibility(View.GONE);
            cardTerminal.setVisibility(View.GONE);
            lblTerminal.setVisibility(View.GONE);

            if (layoutPending != null) layoutPending.setVisibility(View.VISIBLE);
        });
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
        
        cardStats = findViewById(R.id.cardStats);
        cardTerminal = findViewById(R.id.cardTerminal);
        lblTerminal = findViewById(R.id.lblTerminal);
        
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
        
        addToLog(isActive ? "SHIELD: تم تفعيل طبقة الحماية النشطة." : "SHIELD: طبقة الحماية متوقفة حالياً.");
        
        if (isActive) startCounterMonitor();
        else handler.removeCallbacksAndMessages(null);
    }

    private void updateUI(boolean active, boolean animate) {
        int colorRed = Color.parseColor("#FF5252");
        int colorGreen = Color.parseColor("#4CAF50");
        int targetColor = active ? colorGreen : colorRed;

        txtStatusMain.setText(active ? "الدرع نشط" : "الدرع متوقف");
        txtStatusMain.setTextColor(targetColor);
        
        txtDescription.setText(active ? "يتم الآن مراقبة وحجب محاولات الفحص الأمني." : "تنبيه: تطبيقك معرض للاكتشاف الآن.");
        
        btnStart.setText(active ? "إيقاف الحماية" : "تشغيل الحماية");
        btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
        imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));

        if (animate) {
            ValueAnimator anim = ValueAnimator.ofFloat(1f, 1.15f, 1f);
            anim.setDuration(400);
            anim.addUpdateListener(animation -> {
                float scale = (float) animation.getAnimatedValue();
                imgStatus.setScaleX(scale);
                imgStatus.setScaleY(scale);
            });
            anim.start();
        }
    }

    private void startCounterMonitor() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int currentCount = ShieldStatus.getBlockedCount(MainActivity.this);
                if (currentCount > lastKnownCount) {
                    addToLog("DEFENSE: تم حجب محاولة فحص (Reporting Packets).");
                    txtBlockedCount.setText(String.valueOf(currentCount));
                    if (vibrator != null) vibrator.vibrate(50);
                    lastKnownCount = currentCount;
                }
                handler.postDelayed(this, 2000);
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

    private void navigateToActivation() {
        Intent intent = new Intent(this, ActivationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
