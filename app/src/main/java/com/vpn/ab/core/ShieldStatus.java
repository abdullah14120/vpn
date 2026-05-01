package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    private static final String PREFS_NAME = "shield_security_prefs";
    private static final String LICENSE_PREFS = "security_prefs"; // ملف التراخيص
    
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_LAST_INTERCEPT = "last_intercept_time";
    public static final String KEY_IS_ACTIVATED = "is_activated"; // مفتاح التفعيل

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    /**
     * صمام الأمان: التحقق من أن النسخة مفعلة رسمياً.
     * هذا هو الشرط الذي يمنع الاستخدام غير القانوني.
     */
    public static boolean isLicenseValid(Context context) {
        if (context == null) return false;
        SharedPreferences licensePrefs = context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE | 0x0004);
        return licensePrefs.getBoolean(KEY_IS_ACTIVATED, false);
    }

    public static void setProtectionState(Context context, boolean active) {
        // لا نسمح بتغيير الحالة إذا لم يكن هناك ترخيص
        if (!isLicenseValid(context)) {
            Log.e(TAG, "❌ محاولة تفعيل بدون ترخيص رسمي!");
            return;
        }

        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;
        
        try {
            prefs.edit()
                    .putBoolean(KEY_SHIELD_ACTIVE, active)
                    .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "❌ خطأ: " + e.getMessage());
        }
    }

    /**
     * الدالة التي يستدعيها الواتساب. 
     * الآن أصبحت محمية بصمام أمان مزدوج (حالة المفتاح + حالة الترخيص).
     */
    public static boolean isProtectionActive(Context context) {
        // إذا لم يكن الترخيص صالحاً، نرد بـ false فوراً للواتساب
        if (!isLicenseValid(context)) return false;

        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        return prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    public static synchronized void incrementBlockedCount(Context context) {
        // لا نحتسب أي إحباط إذا كان الترخيص غير موجود
        if (!isLicenseValid(context)) return;

        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;

        int currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0);
        prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, currentCount + 1)
                .apply();
    }

    public static int getBlockedCount(Context context) {
        if (!isLicenseValid(context)) return 0;
        SharedPreferences prefs = getPrefs(context);
        return (prefs != null) ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    public static void resetStats(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply();
        }
    }
}
