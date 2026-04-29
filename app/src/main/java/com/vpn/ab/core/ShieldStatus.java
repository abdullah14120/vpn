package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * ShieldStatus: المحرك المركزي لإدارة حالة الأمان والتواصل بين العمليات.
 * النسخة المحدثة لضمان التوافق التام مع MainActivity و WhatsApp Spy.
 */
public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    private static final String PREFS_NAME = "shield_security_prefs";
    
    // المفاتيح البرمجية الثابتة (Keys)
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_LAST_INTERCEPT = "last_intercept_time";

    /**
     * الحصول على نسخة SharedPreferences تدعم تعدد العمليات.
     * القيمة 0x0004 تعني MODE_MULTI_PROCESS.
     */
    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    /**
     * تحديث حالة الدرع - هذه الدالة المطلوبة في MainActivity.
     * تم تسميتها setProtectionState لحل مشكلة "Symbol Not Found".
     */
    public static void setProtectionState(Context context, boolean active) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;
        
        try {
            prefs.edit()
                    .putBoolean(KEY_SHIELD_ACTIVE, active)
                    .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                    .apply();
            Log.d(TAG, "🛡️ حالة الدرع الآن: " + (active ? "نشط (ON)" : "معزول (OFF)"));
        } catch (Exception e) {
            Log.e(TAG, "❌ خطأ في كتابة الإعدادات: " + e.getMessage());
        }
    }

    /**
     * للتحقق من حالة الدرع (يستخدمها الجاسوس داخل واتساب).
     */
    public static boolean isProtectionActive(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        return prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    /**
     * زيادة عداد التقارير المحبطة (يستدعيها الجاسوس عند اصطياد تقرير).
     */
    public static void incrementBlockedCount(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;

        int currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0);
        
        prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, currentCount + 1)
                .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                .apply();
        
        Log.i(TAG, "🎯 تم إحباط تهديد أمني جديد. الإجمالي: " + (currentCount + 1));
    }

    /**
     * جلب إجمالي المحاولات المحبطة (لعرضها في الواجهة).
     */
    public static int getBlockedCount(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return (prefs != null) ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    /**
     * تصفير العداد (عند الحاجة).
     */
    public static void resetStats(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply();
        }
    }
}
