package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * كلاس إدارة الحالة الأمنية - صمام الأمان المركزي للمشروع.
 * مصمم ليعمل في بيئة متعددة العمليات (Multi-Process) لضمان التوافق مع الواتساب.
 */
public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    private static final String PREFS_NAME = "shield_security_prefs";
    private static final String LICENSE_PREFS = "security_prefs"; // ملف التراخيص المشفر
    
    // مفاتيح التخزين
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_LAST_INTERCEPT = "last_intercept_time";
    public static final String KEY_IS_ACTIVATED = "is_activated"; // مفتاح تفعيل النسخة البريميوم

    /**
     * جلب ملف الإعدادات العامة (العدادات والحالة).
     * نستخدم 0x0004 (MODE_MULTI_PROCESS) لضمان أن التغييرات في تطبيقك يراها الواتساب فوراً.
     */
    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    /**
     * جلب ملف الترخيص.
     */
    private static SharedPreferences getLicensePrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(LICENSE_PREFS, Context.MODE_PRIVATE | 0x0004);
    }

    /**
     * صمام الأمان المركزي: التحقق من أن النسخة مفعلة رسمياً من قبل الأدمن.
     */
    public static boolean isLicenseValid(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        return prefs != null && prefs.getBoolean(KEY_IS_ACTIVATED, false);
    }

    /**
     * دالة التفعيل المحلي: تستدعى فور استقبال إشارة القبول من Firebase.
     */
    public static void activateLicenseLocally(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, true).apply();
            Log.d(TAG, "🛡️ [License] تم حقن ترخيص النشاط بنجاح.");
        }
    }

    /**
     * التحكم في تشغيل/إيقاف الدرع.
     */
    public static void setProtectionState(Context context, boolean active) {
        // حماية: لا يمكن تفعيل الزر إذا لم تكن النسخة مرخصة
        if (!isLicenseValid(context)) {
            Log.e(TAG, "❌ [Security] محاولة تشغيل الدرع على نسخة غير مفعلة!");
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
            Log.e(TAG, "❌ [Error] فشل حفظ حالة الدرع: " + e.getMessage());
        }
    }

    /**
     * الدالة التي يستدعيها الواتساب (عبر السمالي المحقون).
     */
    public static boolean isProtectionActive(Context context) {
        // إذا سقط الترخيص أو تم حذفه، يتوقف الرصد فوراً في الواتساب
        if (!isLicenseValid(context)) return false;

        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    /**
     * زيادة عداد التقارير المحظورة.
     * يتم استدعاؤها من كلاس SmartSpy المحقون في الواتساب.
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

    /**
     * جلب إجمالي المحظورات لعرضها في واجهة المستخدم.
     */
    public static int getBlockedCount(Context context) {
        if (!isLicenseValid(context)) return 0;
        SharedPreferences prefs = getPrefs(context);
        return (prefs != null) ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    /**
     * تصفير الإحصائيات (اختياري).
     */
    public static void resetStats(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply();
        }
    }
}
