package com.vpn.ab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
        
        // ربط عناصر الانتظار الجديدة
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
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "تم نسخ المعرف بنجاح", Toast.LENGTH_SHORT).show();
    }

    private void sendActivationRequest() {
        String userName = edtUserName.getText().toString().trim();

        if (userName.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال اسمك أو رقمك لتمييز طلبك", Toast.LENGTH_LONG).show();
            return;
        }

        // 1. إخفاء الزر وإظهار أيقونة الانتظار
        btnRequestActivation.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);
        txtLoading.setText("جاري إرسال طلبك للسيرفر...");

        DatabaseReference database = FirebaseDatabase.getInstance().getReference("Requests");
        
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userName", userName);
        requestData.put("androidId", androidId);
        requestData.put("status", "pending");
        requestData.put("timestamp", System.currentTimeMillis());

        database.child(androidId).setValue(requestData)
                .addOnSuccessListener(aVoid -> {
                    // 2. تحديث النص عند النجاح ليطابق منطق الـ MainActivity
                    txtLoading.setText("تم الإرسال! يرجى الانتظار حتى يوافق الأدمن.");
                    Toast.makeText(this, "تم إرسال الطلب بنجاح", Toast.LENGTH_LONG).show();
                    
                    // تحويل المستخدم لواجهة الانتظار في MainActivity تلقائياً
                    new android.os.Handler().postDelayed(() -> {
                        startActivity(new Intent(ActivationActivity.this, MainActivity.class));
                        finish();
                    }, 2000);
                })
                .addOnFailureListener(e -> {
                    // 3. في حال الفشل، نعيد إظهار الزر للمحاولة مرة أخرى
                    Toast.makeText(this, "فشل الإرسال: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnRequestActivation.setVisibility(View.VISIBLE);
                    layoutLoading.setVisibility(View.GONE);
                });
    }
}
