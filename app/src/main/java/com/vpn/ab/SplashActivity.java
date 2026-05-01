package com.vpn.ab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // تأثير نبض خفيف للشعار
        ImageView logo = findViewById(R.id.imgLogo);
        Animation anim = new AlphaAnimation(0.4f, 1.0f);
        anim.setDuration(800);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        logo.startAnimation(anim);

        checkStatus();
    }

    private void checkStatus() {
        SharedPreferences prefs = getSharedPreferences("security_prefs", MODE_PRIVATE);
        
        // 1. فحص التفعيل المحلي
        if (prefs.getBoolean("is_activated", false)) {
            goToMain();
            return;
        }

        // 2. فحص التفعيل من السيرفر
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users").child(androidId);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && Boolean.TRUE.equals(snapshot.child("active").getValue(Boolean.class))) {
                    prefs.edit().putBoolean("is_activated", true).apply();
                    goToMain();
                } else {
                    goToActivation();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // في حال انقطاع النت، نطلب التفعيل كإجراء حماية
                goToActivation();
            }
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void goToActivation() {
        startActivity(new Intent(this, ActivationActivity.class));
        finish();
    }
}
