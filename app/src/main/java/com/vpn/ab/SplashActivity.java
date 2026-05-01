package com.vpn.ab;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.vpn.ab.core.ShieldStatus;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // تأثير نبض خفيف للشعار
        ImageView logo = findViewById(R.id.imgLogo);
        if (logo != null) {
            Animation anim = new AlphaAnimation(0.4f, 1.0f);
            anim.setDuration(800);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            logo.startAnimation(anim);
        }

        // إعطاء وقت قصير (1.5 ثانية) لإظهار الشعار ثم الفحص
        new Handler(Looper.getMainLooper()).postDelayed(this::checkStatus, 1500);
    }

    private void checkStatus() {
        // 1. فحص التفعيل المحلي (عن طريق الكلاس المركزي لضمان الدقة)
        if (ShieldStatus.isLicenseValid(this)) {
            goToMain();
            return;
        }

        // 2. فحص التفعيل من السيرفر (في حال تم تفعيل المستخدم وهو مغلق التطبيق)
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users").child(androidId);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // نتحقق من القيمة is_activated كما في تطبيق الأدمن
                    Boolean serverActive = snapshot.child("is_activated").getValue(Boolean.class);
                    
                    if (Boolean.TRUE.equals(serverActive)) {
                        // حفظ التفعيل محلياً لكي لا يحتاج للإنترنت المرة القادمة
                        ShieldStatus.activateLicenseLocally(SplashActivity.this);
                        goToMain();
                    } else {
                        goToActivation();
                    }
                } else {
                    // مستخدم جديد تماماً
                    goToActivation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // في حال انقطاع النت ولم يكن مفعل محلياً، نطلب التفعيل كإجراء حماية
                goToActivation();
            }
        });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToActivation() {
        Intent intent = new Intent(this, ActivationActivity.class);
        startActivity(intent);
        finish();
    }
}
