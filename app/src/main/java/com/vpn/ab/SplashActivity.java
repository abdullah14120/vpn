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

        // تأثير الحركة للشعار
        ImageView logo = findViewById(R.id.imgLogo);
        if (logo != null) {
            Animation anim = new AlphaAnimation(0.4f, 1.0f);
            anim.setDuration(800);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            logo.startAnimation(anim);
        }

        // بدء الفحص بعد 1.5 ثانية
        new Handler(Looper.getMainLooper()).postDelayed(this::checkStatus, 1500);
    }

    private void checkStatus() {
        // 1. الفحص المحلي (لتوفير استهلاك البيانات وسرعة الفتح للمشتركين)
        if (ShieldStatus.isLicenseValid(this)) {
            goToMain();
            return;
        }

        // 2. الفحص في السيرفر (المسار الموحد Users)
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users").child(androidId);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // جلب حالة التفعيل (تأكد من مطابقة الاسم is_activated)
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    
                    if (Boolean.TRUE.equals(isActivated)) {
                        // تفعيل ناجح: حفظ محلي والدخول للدرع
                        ShieldStatus.activateLicenseLocally(SplashActivity.this);
                        goToMain();
                    } else {
                        // طلب موجود لكن لم يتم تفعيله بعد: نرسله للرئيسية لإظهار واجهة الانتظار
                        goToMain();
                    }
                } else {
                    // مستخدم جديد تماماً ليس له سجل في Users
                    goToActivation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // في حال وجود مشكلة في الإنترنت، نرسله للرئيسية 
                // MainActivity سيتكفل بمحاولة الفحص مرة أخرى أو إظهار خطأ
                goToMain();
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
