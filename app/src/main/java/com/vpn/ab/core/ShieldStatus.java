package com.vpn.ab.core;

import android.content.Context;
import android.content.SharedPreferences;

public class ShieldStatus {
    private static final String PREFS_NAME = "shield_prefs";
    private static final String KEY_ACTIVE = "is_active";
    private static final String KEY_COUNT = "blocked_count";

    // تفعيل أو إيقاف الدرع
    public static void setProtectionActive(Context context, boolean active) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        prefs.edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    // جلب الحالة (سيستدعيها الجاسوس من داخل واتساب)
    public static boolean isProtectionActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return prefs.getBoolean(KEY_ACTIVE, false);
    }

    // زيادة عداد الإحباط
    public static void incrementBlockedCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        int current = prefs.getInt(KEY_COUNT, 0);
        prefs.edit().putInt(KEY_COUNT, current + 1).apply();
    }
}
