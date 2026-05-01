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
import com.google.android.material.button.MaterialButton;
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
    
    // إضافات الشاشة الأمنية
    private RecyclerView recyclerSecurityLog;
    private LogAdapter logAdapter;
    private List<String> logList = new ArrayList<>();
    private int lastKnownCount = 0; 

    private Vibrator vibrator;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // الآن MainActivity لا تفحص التراخيص، بل تعرض الواجهة مباشرة
        // لأن SplashActivity قامت بالمهمة مسبقاً
        setContentView(R.layout.activity_main);

        initViews();
        setupTerminal(); // إعداد السجل الأمني
        setupInitialState();

        btnStart.setOnClickListener(v -> toggleShield());

        // بدء مراقبة العداد القادم من واتساب والربط بالسجل
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
        
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupTerminal() {
        // إعداد الـ RecyclerView لعرض السجل الأمني
        logAdapter = new LogAdapter(logList);
        recyclerSecurityLog.setLayoutManager(new LinearLayoutManager(this));
        recyclerSecurityLog.setAdapter(logAdapter);
        
        // رسائل ترحيبية احترافية عند التشغيل
        addToLog("SYSTEM: تم تشغيل بروتوكول الحماية النشط.");
        addToLog("SYSTEM: حالة الترخيص: [مفعل - نسخة بريميوم].");
        addToLog("SYSTEM: في انتظار رصد تهديدات من حزمة الواتساب...");
    }

    private void setupInitialState() {
        isActive = ShieldStatus.isProtectionActive(this);
        lastKnownCount = ShieldStatus.getBlockedCount(this); 
        updateUI(isActive, false);
    }

    private void toggleShield() {
        isActive = !isActive;
        ShieldStatus.setProtectionState(this, isActive);
        
        if (vibrator != null) {
            vibrator.vibrate(isActive ? 70 : 30);
        }

        updateUI(isActive, true);
        addToLog(isActive ? "PROTOCOL: تم تفعيل درع حماية الواتساب." : "PROTOCOL: تم إيقاف الحماية، النظام في وضع الاستعداد.");
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
        txtDescription.setText(active ? 
            "درع الحماية يعمل الآن. واتساب متصل عبر قناة آمنة ومراقبة." : 
            "درع الحماية متوقف حالياً. واتساب لن يرسل أو يستقبل أي بيانات لضمان خصوصيتك.");
        btnStart.setText(active ? "إيقاف الدرع النشط" : "تفعيل الدرع النشط");

        if (animate) {
            animateColorChange(btnStart, targetColor);
            animateColorChange(imgStatus, targetColor);
            animateColorChange(txtStatusMain, targetColor);
            
            imgStatus.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction(() -> {
                imgStatus.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }).start();
        } else {
            btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
            imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));
            txtStatusMain.setTextColor(targetColor);
        }

        imgStatus.setImageResource(active ? 
            android.R.drawable.ic_lock_idle_lock : 
            android.R.drawable.ic_lock_lock);
    }

    private void animateColorChange(final Object view, int targetColor) {
        int colorFrom;
        if (view instanceof MaterialButton) {
            colorFrom = ((MaterialButton) view).getBackgroundTintList() != null ? 
                    ((MaterialButton) view).getBackgroundTintList().getDefaultColor() : Color.RED;
        } else if (view instanceof TextView) {
            colorFrom = ((TextView) view).getCurrentTextColor();
        } else if (view instanceof ImageView) {
            colorFrom = ((ImageView) view).getImageTintList() != null ? 
                    ((ImageView) view).getImageTintList().getDefaultColor() : Color.RED;
        } else return;

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
                    addToLog("INTERCEPT: تم إحباط محاولة تمرير تقرير لشركة الواتساب لحظر حسابك (تقرير أمني) عدد: " + diff);
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
