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
        // وضع 0x0004 (MODE_MULTI_PROCESS) لضمان قراءة الواتساب للقيمة اللحظية
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    private static SharedPreferences getLicensePrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE | 0x0004);
    }

    /**
     * صمام الأمان: التحقق من أن النسخة مفعلة رسمياً.
     */
    public static boolean isLicenseValid(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        return prefs != null && prefs.getBoolean(KEY_IS_ACTIVATED, false);
    }

    /**
     * دالة التفعيل المحلي: تستدعى بواسطة MainActivity عند تلقي إشارة "قبول" من Firebase.
     */
    public static void activateLicenseLocally(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, true).apply();
            Log.d(TAG, "✅ تم تفعيل الترخيص محلياً بنجاح.");
        }
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
            Log.e(TAG, "❌ خطأ في حفظ الحالة: " + e.getMessage());
        }
    }

    /**
     * الدالة التي يستدعيها الواتساب للتحقق من الحماية.
     */
    public static boolean isProtectionActive(Context context) {
        // إذا سقط الترخيص لأي سبب، يتوقف الدرع فوراً حتى لو كان الزر مفعلاً
        if (!isLicenseValid(context)) return false;

        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    public static synchronized void incrementBlockedCount(Context context) {
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
