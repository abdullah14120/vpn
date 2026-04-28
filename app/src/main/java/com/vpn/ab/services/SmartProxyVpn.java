package com.vpn.ab.services;

import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import com.hadramout.system.manager.proxy.LocalProxyServer;

public class SmartProxyVpn extends VpnService {
    private ParcelFileDescriptor vpnInterface;
    private LocalProxyServer proxyServer;

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        // 1. تشغيل سيرفر الوكيل المحلي
        proxyServer = new LocalProxyServer(8080);
        proxyServer.start();

        // 2. إعداد نفق الـ VPN
        Builder builder = new Builder();
        builder.setSession("WhatsAppAntiBan")
               .addAddress("10.0.0.2", 24)
               .addAllowedApplication("com.whatsapp") // حصر العمل على واتساب
               .addRoute("0.0.0.0", 0); // توجيه كل المرور للوكيل

        vpnInterface = builder.establish();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // إغلاق السيرفر والواجهة عند الإيقاف
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
        }
    }
}
