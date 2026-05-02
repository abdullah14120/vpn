package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ShieldStatus {

    public static final String KEY_BLOCKED_COUNT = "reports_blocked_count";
    public static final String KEY_IS_ACTIVATED = "is_activated";
    public static final String KEY_SHIELD_ACTIVE = "shield_active_state";
    
    private static final String LICENSE_PREFS = "security_prefs";
    private static final String PREFS_NAME = "shield_security_prefs";
    private static final String TAG = "ShieldCore";

    // --- التعديل الجوهري: استخدام وضع MULTI_PROCESS (القيمة 4) ---
    private static SharedPreferences getPrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, 4); // 4 = MODE_MULTI_PROCESS
    }

    private static SharedPreferences getLicensePrefs(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(LICENSE_PREFS, 4);
    }

    public static boolean isLicenseValid(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        return prefs != null && prefs.getBoolean(KEY_IS_ACTIVATED, false);
    }

    public static void activateLicenseLocally(Context context) {
        SharedPreferences prefs = getLicensePrefs(context);
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_IS_ACTIVATED, true).apply();
            Log.d(TAG, "✅ تم تفعيل الترخيص محلياً.");
        }
    }

    public static int getBlockedCount(Context context) {
        if (!isLicenseValid(context)) return 0;
        SharedPreferences prefs = getPrefs(context);
        return prefs != null ? prefs.getInt(KEY_BLOCKED_COUNT, 0) : 0;
    }

    public static synchronized void incrementBlockedCount(Context context) {
        if (!isLicenseValid(context)) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            int current = prefs.getInt(KEY_BLOCKED_COUNT, 0);
            prefs.edit().putInt(KEY_BLOCKED_COUNT, current + 1).apply();
            Log.i(TAG, "🛡️ [Intercept] تم إحباط تهديد. الإجمالي: " + (current + 1));
        }
    }

    public static boolean isProtectionActive(Context context) {
        if (!isLicenseValid(context)) return false;
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(KEY_SHIELD_ACTIVE, false);
    }

    public static void setProtectionState(Context context, boolean active) {
        if (!isLicenseValid(context)) return;
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_SHIELD_ACTIVE, active).apply();
        }
    }

    public static void resetStats(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) {
            prefs.edit().putInt(KEY_BLOCKED_COUNT, 0).apply();
        }
    }
}
