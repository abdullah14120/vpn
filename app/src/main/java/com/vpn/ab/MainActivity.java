package com.vpn.ab;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.vpn.ab.core.ShieldStatus;

public class MainActivity extends AppCompatActivity {
    private TextView statusText, blockedCounter;
    private Button toggleButton;
    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        blockedCounter = findViewById(R.id.blockedCounter);
        toggleButton = findViewById(R.id.toggleButton);

        toggleButton.setOnClickListener(v -> {
            isActive = !isActive;
            ShieldStatus.setProtectionActive(this, isActive);
            updateUI();
        });
        
        // تحديث العداد بشكل دوري أو عبر Broadcast
        startCounterListener();
    }

    private void updateUI() {
        if (isActive) {
            statusText.setText("🛡️ درع الحماية نشط");
            toggleButton.setText("إيقاف الحماية");
        } else {
            statusText.setText("⚠️ الحماية متوقفة - واتساب معزول");
            toggleButton.setText("تفعيل الدرع");
        }
    }
}
