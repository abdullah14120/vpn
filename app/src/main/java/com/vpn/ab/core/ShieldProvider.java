package com.vpn.ab.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class ShieldProvider extends ContentProvider {
    // يجب أن يطابق هذا العنوان ما هو موجود في المانيفست تماماً
    public static final String AUTHORITY = "com.vpn.ab.shield_provider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * الدالة التي يستدعيها الواتساب (SmartSpy) للتحقق من حالة الدرع وقيمة العداد
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // إنشاء جدول افتراضي في الذاكرة (MatrixCursor)
        MatrixCursor cursor = new MatrixCursor(new String[]{"is_active", "blocked_count"});
        
        // التحقق من صلاحية الرخصة (صمام الأمان)
        boolean isLicensed = ShieldStatus.isLicenseValid(getContext());
        
        // التحقق هل الحماية مشغلة يدوياً من قبل المستخدم؟
        boolean active = isLicensed && ShieldStatus.isProtectionActive(getContext());
        
        // جلب العداد الحقيقي (فقط إذا كان مرخصاً)
        int count = isLicensed ? ShieldStatus.getBlockedCount(getContext()) : 0;
        
        // إضافة الصف للجدول (1 للنشط، 0 للمتوقف)
        cursor.addRow(new Object[]{active ? 1 : 0, count});
        
        // ضبط الـ NotificationUri لكي يعرف الكرسور متى يتحدث تلقائياً
        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        
        return cursor;
    }

    /**
     * الدالة التي يستدعيها الواتساب لزيادة العداد عند حجب تقرير
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // منع أي تحديث إذا لم تكن النسخة مفعلة بريميوم
        if (!ShieldStatus.isLicenseValid(getContext())) {
            return 0; 
        }

        // فحص هل الأمر هو زيادة العداد (increment)
        if (values != null && values.containsKey("increment")) {
            // تنفيذ الزيادة في المخزن (ShieldStatus)
            ShieldStatus.incrementBlockedCount(getContext());
            
            // --- الإجراء الأهم لمزامنة العداد اللحظية ---
            // إرسال إشعار لكل من يراقب هذا الـ Uri (مثل MainActivity) بأن البيانات تغيرت
            if (getContext() != null) {
                getContext().getContentResolver().notifyChange(uri, null);
            }
            return 1; // نجاح العملية
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) { return null; }
    
    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }
    
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
}
