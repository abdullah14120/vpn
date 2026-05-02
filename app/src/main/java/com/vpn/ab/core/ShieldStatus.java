package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    
    // أسماء ملفات التخزين (يجب أن تطابق القديم تماماً)
    private static final String PREFS_NAME = "shield_security_prefs";
    private static final String LICENSE_PREFS = "security_prefs"; 
    
    // المفاتيح (Keys)
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_LAST_INTERCEPT = "last_intercept_time";
    public static final String KEY_IS_ACTIVATED = "is_activated";

    /**
     * التعديل الذهبي: استخدام وضع 0x0004 (MODE_MULTI_PROCESS) 
     * هذا ما يسمح للواتساب (عملية خارجية) بتحديث العداد وتطبيقك (عملية داخلية) برؤيته فوراً.
     */
    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    private static SharedPreferences getLicensePrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE | 0x0004);
    }

    // فحص هل التطبيق مفعل رسمياً
    public static boolean isLicenseValid(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        return prefs != null && prefs.getBoolean(KEY_IS_ACTIVATED, false);
    }

    // تفعيل التطبيق محلياً (بعد موافقة Firebase)
    public static void activateLicenseLocally(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, true).apply();
            Log.d(TAG, "✅ [License] تم حقن ترخيص النشاط بنجاح.");
        }
    }

    // جلب عدد المحاولات المحجوبة للعداد
    public static int getBlockedCount(Context context) {
        if (!isLicenseValid(context)) return 0;
        SharedPreferences prefs = getPrefs(context);
        return (prefs != null) ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    /**
     * هذه الدالة هي التي يستدعيها الـ ShieldProvider عندما يحجب الواتساب تقريراً.
     * تم استخدام synchronized لضمان عدم ضياع أي عدّة عند حدوث هجمات متزامنة.
     */
    public static synchronized void incrementBlockedCount(Context context) {
        if (!isLicenseValid(context)) return;

        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;

        int currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0);
        prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, currentCount + 1)
                .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                .apply();
        
        Log.i(TAG, "🛡️ [Intercept] تم إحباط تهديد جديد. الإجمالي: " + (currentCount + 1));
    }

    // فحص هل زر الحماية مفعل في الواجهة
    public static boolean isProtectionActive(Context context) {
        if (!isLicenseValid(context)) return false;
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    // حفظ حالة زر الحماية (تشغيل/إيقاف)
    public static void setProtectionState(Context context, boolean active) {
        if (!isLicenseValid(context)) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit()
                    .putBoolean(KEY_SHIELD_ACTIVE, active)
                    .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                    .apply();
        }
    }

    // تصفير العداد (إذا رغبت في ذلك)
    public static void resetStats(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply();
        }
    }
}
