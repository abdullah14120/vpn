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
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.vpn.ab.core.ShieldStatus;

public class MainActivity extends AppCompatActivity {

    private ImageView imgStatus, iconShield;
    private TextView txtStatusMain, txtDescription, txtBlockedCount;
    private MaterialButton btnStart;
    private Vibrator vibrator;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupInitialState();

        // مستمع لزر التفعيل
        btnStart.setOnClickListener(v -> toggleShield());

        // بدء مراقبة العداد القادم من واتساب
        startCounterMonitor();
    }

    private void initViews() {
        imgStatus = findViewById(R.id.imgStatus);
        iconShield = findViewById(R.id.iconShield);
        txtStatusMain = findViewById(R.id.txtStatusMain);
        txtDescription = findViewById(R.id.txtDescription);
        txtBlockedCount = findViewById(R.id.txtBlockedCount);
        btnStart = findViewById(R.id.btnStart);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setupInitialState() {
        // جلب الحالة الأخيرة المخزنة
        isActive = ShieldStatus.isProtectionActive(this);
        updateUI(isActive, false);
    }

    private void toggleShield() {
        isActive = !isActive;
        
        // حفظ الحالة لكي يراها "الجاسوس" داخل واتساب
        ShieldStatus.setProtectionState(this, isActive);
        
        // تأثير اهتزاز احترافي
        if (vibrator != null) {
            vibrator.vibrate(isActive ? 70 : 30);
        }

        updateUI(isActive, true);
    }

    private void updateUI(boolean active, boolean animate) {
        int colorRed = Color.parseColor("#FF5252");
        int colorGreen = Color.parseColor("#4CAF50");
        int targetColor = active ? colorGreen : colorRed;

        // تحديث النصوص
        txtStatusMain.setText(active ? "النظام محمي" : "النظام معزول");
        txtDescription.setText(active ? 
            "درع الحماية يعمل الآن. واتساب متصل عبر قناة آمنة ومراقبة." : 
            "درع الحماية متوقف حالياً. واتساب لن يرسل أو يستقبل أي بيانات لضمان خصوصيتك.");
        btnStart.setText(active ? "إيقاف الدرع النشط" : "تفعيل الدرع النشط");

        if (animate) {
            // تحريك تغيير الألوان بسلاسة (Safe Color Change)
            animateColorChange(btnStart, targetColor);
            animateColorChange(imgStatus, targetColor);
            animateColorChange(txtStatusMain, targetColor);
            
            // تحريك الأيقونة (Pulse Animation)
            imgStatus.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction(() -> {
                imgStatus.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }).start();
        } else {
            btnStart.setBackgroundTintList(ColorStateList.valueOf(targetColor));
            imgStatus.setImageTintList(ColorStateList.valueOf(targetColor));
            txtStatusMain.setTextColor(targetColor);
        }

        // تغيير الأيقونة
        imgStatus.setImageResource(active ? 
            android.R.drawable.ic_lock_idle_lock : 
            android.R.drawable.ic_lock_lock);
    }

    private void animateColorChange(final Object view, int targetColor) {
        int colorFrom;

        // فحص النوع بطريقة آمنة لجلب اللون الحالي
        if (view instanceof MaterialButton) {
            colorFrom = ((MaterialButton) view).getBackgroundTintList() != null ? 
                    ((MaterialButton) view).getBackgroundTintList().getDefaultColor() : Color.RED;
        } else if (view instanceof TextView) {
            colorFrom = ((TextView) view).getCurrentTextColor();
        } else if (view instanceof ImageView) {
            colorFrom = ((ImageView) view).getImageTintList() != null ? 
                    ((ImageView) view).getImageTintList().getDefaultColor() : Color.RED;
        } else {
            return;
        }

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, targetColor);
        colorAnimation.setDuration(400);
        colorAnimation.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            
            // تطبيق اللون الجديد بناءً على نوع العنصر الفعلي
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setBackgroundTintList(ColorStateList.valueOf(color));
            } else if (view instanceof TextView) {
                ((TextView) view).setTextColor(color);
            } else if (view instanceof ImageView) {
                ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
            }
        });
        colorAnimation.start();
    }

    private void startCounterMonitor() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int count = ShieldStatus.getBlockedCount(MainActivity.this);
                if (!txtBlockedCount.getText().toString().equals(String.valueOf(count))) {
                    txtBlockedCount.setText(String.valueOf(count));
                    if (vibrator != null) vibrator.vibrate(20);
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
