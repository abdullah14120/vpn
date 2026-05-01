package com.vpn.ab.core;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.annotation.RequiresApi;
import com.vpn.ab.R;

/**
 * ShieldTileService: خدمة التحكم السريع من لوحة الإشعارات.
 * تتيح للمستخدم رؤية حالة الدرع وتغييرها بلمسة واحدة.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class ShieldTileService extends TileService {

    // يتم استدعاؤها عند إضافة الزر أو سحب لوحة الإعدادات لأسفل
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    // يتم استدعاؤها عند النقر على الزر
    @Override
    public void onClick() {
        super.onClick();
        
        // جلب الحالة الحالية وعكسها
        boolean isActive = ShieldStatus.isProtectionActive(this);
        boolean newState = !isActive;
        
        // حفظ الحالة الجديدة في المحرك المركزي
        ShieldStatus.setProtectionState(this, newState);
        
        // تحديث شكل الزر فوراً
        updateTile();
    }

    /**
     * تحديث شكل وألوان الزر بناءً على حالة الدرع
     */
    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean isActive = ShieldStatus.isProtectionActive(this);

        if (isActive) {
            // حالة النشاط: لون مشع وأيقونة قفل مغلق
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("الدرع: نشط");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_idle_lock));
            }
        } else {
            // حالة التوقف: لون باهت وأيقونة قفل مفتوح
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("الدرع: متوقف");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tile.setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_power_off));
            }
        }

        // إرسال التحديث للنظام
        tile.updateTile();
    }
}
