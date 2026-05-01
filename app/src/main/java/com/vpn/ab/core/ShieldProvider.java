package com.vpn.ab.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

public class ShieldProvider extends ContentProvider {
    // تأكد أن هذا الرابط مطابق تماماً لما ستكتبه في Manifest الواتساب
    private static final String AUTHORITY = "com.vpn.ab.shield_provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/status");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // استخدام synchronized لضمان عدم حدوث تضارب عند قراءة البيانات من عدة خيوط (Threads)
        MatrixCursor cursor = new MatrixCursor(new String[]{"is_active", "blocked_count"});
        
        try {
            if (getContext() != null) {
                boolean active = ShieldStatus.isProtectionActive(getContext());
                int count = ShieldStatus.getBlockedCount(getContext());
                
                // تحويل الحالة لرقم (1 للنشط، 0 للمتوقف) لسهولة التعامل في Smali
                cursor.addRow(new Object[]{active ? 1 : 0, count});
            }
        } catch (Exception e) {
            Log.e("ShieldProvider", "Query Error: " + e.getMessage());
        }
        
        return cursor;
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // هذه الدالة هي التي سيستدعيها "الجاسوس" داخل واتساب لزيادة عداد الحظر
        if (values != null && getContext() != null) {
            if (values.containsKey("increment")) {
                ShieldStatus.incrementBlockedCount(getContext());
                
                // إرسال إشارة للنظام بأن البيانات تغيرت لتحديث الواجهة (UI) تلقائياً
                getContext().getContentResolver().notifyChange(uri, null);
                return 1;
            }
        }
        return 0;
    }

    // الدوال التالية لا نحتاجها حالياً ولكن يجب أن تظل موجودة لكي يعمل الـ Provider
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd." + AUTHORITY; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
}
