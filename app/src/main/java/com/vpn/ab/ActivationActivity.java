package com.vpn.ab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class ActivationActivity extends AppCompatActivity {

    private TextView txtAndroidId;
    private EditText edtUserName;
    private MaterialButton btnRequestActivation;
    private ImageButton btnCopy;
    private LinearLayout layoutLoading; 
    private TextView txtLoading;
    private String androidId;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        initViews();
        generateDeviceId();

        // تهيئة Firebase والإشارة للمسار الموحد "Users"
        mDatabase = FirebaseDatabase.getInstance().getReference("Users");

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnRequestActivation.setOnClickListener(v -> sendActivationRequest());
    }

    private void initViews() {
        txtAndroidId = findViewById(R.id.txtAndroidId);
        edtUserName = findViewById(R.id.edtUserName);
        btnRequestActivation = findViewById(R.id.btnRequestActivation);
        btnCopy = findViewById(R.id.btnCopy);
        layoutLoading = findViewById(R.id.layoutLoading);
        txtLoading = findViewById(R.id.txtLoading);
    }

    private void generateDeviceId() {
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        txtAndroidId.setText(androidId);
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Device ID", androidId);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "تم نسخ المعرف", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendActivationRequest() {
        String userName = edtUserName.getText().toString().trim();

        if (userName.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال الاسم أولاً", Toast.LENGTH_LONG).show();
            return;
        }

        // إخفاء الزر وإظهار حالة التحميل
        btnRequestActivation.setVisibility(View.GONE);
        if (layoutLoading != null) {
            layoutLoading.setVisibility(View.VISIBLE);
            txtLoading.setText("جاري إرسال طلب التفعيل...");
        }

        // إعداد البيانات لتتوافق مع الـ Model الخاص بالأدمن
        Map<String, Object> userData = new HashMap<>();
        userData.put("userName", userName);
        userData.put("androidId", androidId);
        userData.put("is_activated", false); // الحقل الأهم: المستخدم يبدأ غير مفعل
        userData.put("timestamp", System.currentTimeMillis());

        // الإرسال إلى مجلد Users مباشرة
        mDatabase.child(androidId).setValue(userData)
                .addOnSuccessListener(aVoid -> {
                    if (txtLoading != null) {
                        txtLoading.setText("تم إرسال الطلب بنجاح!");
                    }
                    
                    Toast.makeText(this, "يرجى الانتظار حتى يتم قبول طلبك من الإدارة", Toast.LENGTH_LONG).show();
                    
                    // التوجه لـ MainActivity للمراقبة اللحظية بعد ثانيتين
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(ActivationActivity.this, MainActivity.class);
                        // منع العودة لهذه الصفحة بذر الرجوع
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }, 2000);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "فشل الإرسال: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnRequestActivation.setVisibility(View.VISIBLE);
                    if (layoutLoading != null) {
                        layoutLoading.setVisibility(View.GONE);
                    }
                });
    }
}
