package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * ShieldStatus: المحرك المركزي لإدارة حالة الأمان والتواصل بين العمليات.
 * مصمم ليكون سريعاً جداً وخفيف الاستهلاك للذاكرة (Low Overhead).
 */
public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    private static final String PREFS_NAME = "shield_security_prefs";
    
    // المفاتيح البرمجية (Keys)
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_LAST_INTERCEPT = "last_intercept_time";

    /**
     * الحصول على نسخة SharedPreferences تدعم تعدد العمليات.
     */
    private static SharedPreferences getPrefs(Context context) {
        // MODE_MULTI_PROCESS يضمن تحديث البيانات بين تطبيقك وواتساب لحظياً
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    /**
     * تحديث حالة الدرع (يتم استدعاؤها من واجهة تطبيقك).
     */
    public static void setProtectionActive(Context context, boolean active) {
        try {
            getPrefs(context).edit()
                    .putBoolean(KEY_SHIELD_ACTIVE, active)
                    .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                    .apply();
            Log.d(TAG, "🛡️ تم تغيير حالة الدرع إلى: " + (active ? "نشط" : "متوقف"));
        } catch (Exception e) {
            Log.e(TAG, "❌ فشل حفظ حالة الدرع: " + e.getMessage());
        }
    }

    /**
     * التحقق من حالة الدرع (يستدعيها الجاسوس من داخل واتساب).
     */
    public static boolean isProtectionActive(Context context) {
        return getPrefs(context).getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    /**
     * زيادة عداد التقارير المحبطة (يتم استدعاؤها من داخل واتساب عند كل إحباط).
     */
    public static void incrementBlockedCount(Context context) {
        SharedPreferences prefs = getPrefs(context);
        int currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0);
        
        prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, currentCount + 1)
                .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                .apply();
        
        Log.i(TAG, "🎯 تم إحباط تقرير أمني جديد. الإجمالي الحالي: " + (currentCount + 1));
    }

    /**
     * جلب إجمالي المحاولات المحبطة (لعرضها في واجهة التطبيق).
     */
    public static int getBlockedCount(Context context) {
        return getPrefs(context).getInt(KEY_BLOCKED_COUNT, 0);
    }

    /**
     * تصفير العداد (اختياري).
     */
    public static void resetStats(Context context) {
        getPrefs(context).edit()
                .putInt(KEY_BLOCKED_COUNT, 0)
                .apply();
    }
}
