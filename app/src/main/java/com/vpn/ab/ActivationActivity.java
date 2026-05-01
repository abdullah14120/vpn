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
    private LinearLayout layoutLoading; // حاوية الانتظار
    private TextView txtLoading;
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        initViews();
        generateDeviceId();

        btnCopy.setOnClickListener(v -> copyToClipboard());
        btnRequestActivation.setOnClickListener(v -> sendActivationRequest());
    }

    private void initViews() {
        txtAndroidId = findViewById(R.id.txtAndroidId);
        edtUserName = findViewById(R.id.edtUserName);
        btnRequestActivation = findViewById(R.id.btnRequestActivation);
        btnCopy = findViewById(R.id.btnCopy);
        
        // ربط عناصر الانتظار المضافة في ملف الـ XML
        layoutLoading = findViewById(R.id.layoutLoading);
        txtLoading = findViewById(R.id.txtLoading);
    }

    private void generateDeviceId() {
        // جلب معرف الأندرويد الفريد للجهاز
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        txtAndroidId.setText(androidId);
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Device ID", androidId);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.id_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendActivationRequest() {
        String userName = edtUserName.getText().toString().trim();

        if (userName.isEmpty()) {
            Toast.makeText(this, R.string.enter_name_error, Toast.LENGTH_LONG).show();
            return;
        }

        // 1. إخفاء الزر وإظهار أيقونة الانتظار بشكل احترافي
        btnRequestActivation.setVisibility(View.GONE);
        if (layoutLoading != null) {
            layoutLoading.setVisibility(View.VISIBLE);
            txtLoading.setText(R.string.loading_request);
        }

        // إعداد المرجع في Firebase (Requests)
        DatabaseReference database = FirebaseDatabase.getInstance().getReference("Requests");
        
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userName", userName);
        requestData.put("androidId", androidId);
        requestData.put("status", "pending"); // حالة الطلب الافتراضية
        requestData.put("timestamp", System.currentTimeMillis());

        database.child(androidId).setValue(requestData)
                .addOnSuccessListener(aVoid -> {
                    // 2. تحديث النص عند النجاح لتعريف المستخدم بالخطوة القادمة
                    if (txtLoading != null) {
                        txtLoading.setText(R.string.request_sent_success);
                    }
                    Toast.makeText(this, R.string.request_sent_success, Toast.LENGTH_LONG).show();
                    
                    // تحويل المستخدم لـ MainActivity بعد ثانيتين ليجد واجهة الانتظار هناك
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(ActivationActivity.this, MainActivity.class);
                        // مسح سجل الواجهات السابقة لضمان عدم العودة لصفحة التفعيل بذر الرجوع
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }, 2000);
                })
                .addOnFailureListener(e -> {
                    // 3. في حال فشل الاتصال، نعيد الزر ونخفي التحميل للمحاولة ثانية
                    Toast.makeText(this, getString(R.string.request_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnRequestActivation.setVisibility(View.VISIBLE);
                    if (layoutLoading != null) {
                        layoutLoading.setVisibility(View.GONE);
                    }
                });
    }
}
