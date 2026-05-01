package com.vpn.ab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.ImageButton;
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
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        initViews();
        generateDeviceId();

        // زر نسخ المعرف
        btnCopy.setOnClickListener(v -> copyToClipboard());

        // زر إرسال الطلب إلى الأدمن (Firebase)
        btnRequestActivation.setOnClickListener(v -> sendActivationRequest());
    }

    private void initViews() {
        txtAndroidId = findViewById(R.id.txtAndroidId);
        edtUserName = findViewById(R.id.edtUserName);
        btnRequestActivation = findViewById(R.id.btnRequestActivation);
        btnCopy = findViewById(R.id.btnCopy);
    }

    private void generateDeviceId() {
        // جلب معرف الأندرويد الفريد للجهاز
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
            Toast.makeText(this, "يرجى إدخال اسمك لتمييز طلبك لدى الأدمن", Toast.LENGTH_LONG).show();
            return;
        }

        // إعداد البيانات لإرسالها لقسم "Requests" في Firebase
        DatabaseReference database = FirebaseDatabase.getInstance().getReference("Requests");
        
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userName", userName);
        requestData.put("androidId", androidId);
        requestData.put("status", "pending"); // حالة الطلب: معلق
        requestData.put("timestamp", System.currentTimeMillis());

        btnRequestActivation.setEnabled(false);
        btnRequestActivation.setText("جاري إرسال الطلب...");

        database.child(androidId).setValue(requestData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "تم إرسال طلبك للأدمن بنجاح. يرجى الانتظار للموافقة.", Toast.LENGTH_LONG).show();
                    btnRequestActivation.setText("طلبك قيد المراجعة حالياً");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "فشل الإرسال: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnRequestActivation.setEnabled(true);
                    btnRequestActivation.setText("إرسال طلب تفعيل");
                });
    }
}
