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

        // تأثير نبض خفيف للشعار لتعزيز المظهر البصري
        ImageView logo = findViewById(R.id.imgLogo);
        if (logo != null) {
            Animation anim = new AlphaAnimation(0.4f, 1.0f);
            anim.setDuration(800);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(Animation.INFINITE);
            logo.startAnimation(anim);
        }

        // إعطاء وقت كافٍ (1.5 ثانية) لإظهار الهوية البصرية ثم بدء الفحص
        new Handler(Looper.getMainLooper()).postDelayed(this::checkStatus, 1500);
    }

    private void checkStatus() {
        // 1. فحص التفعيل المحلي (إذا كان الجهاز قد تم تفعيله سابقاً)
        if (ShieldStatus.isLicenseValid(this)) {
            goToMain();
            return;
        }

        // 2. إذا لم يوجد تفعيل محلي، نفحص حالته في السيرفر
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();

        // فحص عقدة المستخدمين أولاً (Users)
        rootRef.child("Users").child(androidId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isActivated = snapshot.child("is_activated").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(isActivated)) {
                        // المستخدم مفعل في السيرفر، نحفظ التفعيل محلياً وندخله
                        ShieldStatus.activateLicenseLocally(SplashActivity.this);
                        goToMain();
                    } else {
                        // المستخدم موجود لكنه غير مفعل، نذهب للرئيسية لإظهار واجهة "قيد المراجعة"
                        goToMain();
                    }
                } else {
                    // المستخدم غير موجود في عقدة Users، نفحص هل لديه طلب معلق في Requests؟
                    checkIfHasPendingRequest(rootRef, androidId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // في حال خطأ الاتصال، نرسله للتفعيل كإجراء احترازي
                goToActivation();
            }
        });
    }

    private void checkIfHasPendingRequest(DatabaseReference rootRef, String androidId) {
        rootRef.child("Requests").child(androidId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // المستخدم أرسل طلباً بالفعل وهو بانتظار الأدمن
                    goToMain();
                } else {
                    // مستخدم جديد كلياً لا يملك تفعيلاً ولا طلباً
                    goToActivation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
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
