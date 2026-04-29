package com.vpn.ab.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class ShieldProvider extends ContentProvider {
    private static final String AUTHORITY = "com.vpn.ab.shield_provider";

    @Override
    public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // ننشئ جدولاً في الذاكرة يحتوي على الحالة والعداد
        MatrixCursor cursor = new MatrixCursor(new String[]{"is_active", "blocked_count"});
        
        // نجلب البيانات من ShieldStatus الذي كتبناه سابقاً
        boolean active = ShieldStatus.isProtectionActive(getContext());
        int count = ShieldStatus.getBlockedCount(getContext());
        
        cursor.addRow(new Object[]{active ? 1 : 0, count});
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // عندما يريد الواتساب زيادة العداد، سيرسل تحديثاً هنا
        if (values != null && values.containsKey("increment")) {
            ShieldStatus.incrementBlockedCount(getContext());
            return 1;
        }
        return 0;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
}
