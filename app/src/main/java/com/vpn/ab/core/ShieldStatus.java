package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import java.security.MessageDigest;

public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    private static final String PREFS_NAME = "shield_security_prefs";
    private static final String LICENSE_PREFS = "security_prefs"; 
    
    // المفاتيح المشفرة
    private static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    private static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    private static final String KEY_LAST_INTERCEPT = "last_intercept_time";
    private static final String KEY_SECURE_TOKEN = "secure_license_token";

    // هاش توقيعك الأصلي (SHA-256) - يجب استبداله بهاش توقيع تطبيقك الحقيقي
    // يمكنك استخراجه عبر Logcat باستخدام دالة verifyAppIntegrity المرفقة
    private static final String ORIGINAL_SIGNATURE_HASH = "YOUR_APP_SIGNATURE_HASH_HERE";

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    private static SharedPreferences getLicensePrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE | 0x0004);
    }

    /**
     * التحقق من نزاهة التطبيق (المنع من الهندسة العكسية)
     * إذا قام أحد بتعديل التطبيق، سيتغير التوقيع وسيفشل هذا الفحص
     */
    public static boolean verifyAppIntegrity(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : packageInfo.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(signature.toByteArray());
                String currentHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP);
                
                // اطبع الهاش في أول مرة لكي تضعه في ORIGINAL_SIGNATURE_HASH
                Log.d(TAG, "App Signature Hash: " + currentHash);
                
                if (ORIGINAL_SIGNATURE_HASH.equals(currentHash)) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Integrity Check Failed");
        }
        return false; // إذا وصلنا هنا، فالتطبيق معدل أو الهاش خطأ
    }

    /**
     * فحص التفعيل المشفر:
     * لا نفحص Boolean، بل نفحص توكن مشتق من AndroidID للجهاز
     */
    public static boolean isLicenseValidEncrypted(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        if (prefs == null) return false;

        String storedToken = prefs.getString(KEY_SECURE_TOKEN, null);
        if (storedToken == null) return false;

        // إعادة توليد التوكن المتوقع لهذا الجهاز ومقارنته بالمخزن
        String expectedToken = generateSecureDeviceToken(context);
        return expectedToken.equals(storedToken);
    }

    /**
     * تفعيل النسخة وربطها بهذا الجهاز فقط
     */
    public static void secureActivate(Context context, String serverSalt) {
        SharedPreferences prefs = getLicensePrefs(context);
        if (prefs != null) {
            String secureToken = generateSecureDeviceToken(context);
            prefs.edit().putString(KEY_SECURE_TOKEN, secureToken).apply();
            Log.d(TAG, "✅ [Security] تم تثبيت ترخيص مشفر ومرتبط بالجهاز.");
        }
    }

    private static String generateSecureDeviceToken(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String raw = "SHIELD_V1_" + androidId + "_SECRET_SALT"; // إضافة ملح سري
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.encodeToString(md.digest(raw.getBytes()), Base64.NO_WRAP);
        } catch (Exception e) {
            return "FAILURE";
        }
    }

    // --- العمليات الوظيفية للدرع ---

    public static int getBlockedCount(Context context) {
        if (!isLicenseValidEncrypted(context)) return 0;
        SharedPreferences prefs = getPrefs(context);
        return (prefs != null) ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    public static synchronized void incrementBlockedCount(Context context) {
        if (!isLicenseValidEncrypted(context)) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;

        int currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0);
        prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, currentCount + 1)
                .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                .apply();
    }

    public static boolean isProtectionActive(Context context) {
        if (!isLicenseValidEncrypted(context)) return false;
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    public static void setProtectionState(Context context, boolean active) {
        if (!isLicenseValidEncrypted(context)) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit()
                    .putBoolean(KEY_SHIELD_ACTIVE, active)
                    .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                    .apply();
        }
    }

    // للإبقاء على التوافق مع الكود القديم في MainActivity
    public static boolean isLicenseValid(Context context) {
        return isLicenseValidEncrypted(context);
    }

    public static void activateLicenseLocally(Context context) {
        secureActivate(context, "DEFAULT");
    }
}
