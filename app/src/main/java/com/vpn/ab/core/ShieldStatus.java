package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

/**
 * ShieldStatus: المحرك المركزي لإدارة حالة الأمان والتواصل بين العمليات.
 * النسخة الاحترافية المجهزة لدعم واجهة Dashboard والـ Terminal Log.
 */
public class ShieldStatus {

    private static final String TAG = "ShieldCore";
    private static final String PREFS_NAME = "shield_security_prefs";
    
    // السلطة الخاصة بالـ Provider للربط بين العمليات
    private static final String AUTHORITY = "com.vpn.ab.shield_provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/status");

    // المفاتيح البرمجية الثابتة
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_LAST_INTERCEPT = "last_intercept_time";

    /**
     * الحصول على نسخة SharedPreferences تدعم تعدد العمليات.
     */
    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        // القيمة 0x0004 تضمن قراءة أحدث البيانات حتى لو كانت من عملية (Process) أخرى
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | 0x0004); 
    }

    /**
     * تحديث حالة الدرع من MainActivity.
     */
    public static void setProtectionState(Context context, boolean active) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;
        
        try {
            prefs.edit()
                    .putBoolean(KEY_SHIELD_ACTIVE, active)
                    .putLong(KEY_LAST_INTERCEPT, System.currentTimeMillis())
                    .apply();
            
            // إخطار النظام بتغير الحالة لتحديث الأيقونات فوراً
            notifyUpdate(context);
            
            Log.d(TAG, "🛡️ SHIELD_LOG: State changed to " + (active ? "ACTIVE" : "STANDBY"));
        } catch (Exception e) {
            Log.e(TAG, "❌ SHIELD_ERROR: Prefs write failure: " + e.getMessage());
        }
    }

    /**
     * للتحقق من حالة الدرع (يستخدمها الجاسوس داخل واتساب Smali).
     */
    public static boolean isProtectionActive(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return false;
        return prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    /**
     * زيادة عداد الحظر - يتم استدعاؤها من داخل الواتساب عند رصد تهديد.
     */
    public static synchronized void incrementBlockedCount(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs == null) return;

        int currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0);
        prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, currentCount + 1)
                .apply();
        
        // إرسال إشارة للـ MainActivity لتحديث العداد وإضافة سطر في الـ Terminal Log
        notifyUpdate(context);
        
        Log.i(TAG, "🎯 INTERCEPTED: Security threat neutralized. Total: " + (currentCount + 1));
    }

    /**
     * جلب إجمالي المحاولات المحبطة للواجهة.
     */
    public static int getBlockedCount(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return (prefs != null) ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    /**
     * تصفير الإحصائيات.
     */
    public static void resetStats(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply();
            notifyUpdate(context);
        }
    }

    /**
     * دالة التنبيه اللحظي - الجسر السريع لتحديث الواجهة.
     */
    private static void notifyUpdate(Context context) {
        try {
            if (context != null) {
                context.getContentResolver().notifyChange(CONTENT_URI, null);
            }
        } catch (Exception ignored) {
            // تجاهل الخطأ في حالة عدم وجود صلاحيات مؤقتة
        }
    }
}
