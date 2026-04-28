package com.vpn.ab.proxy;

import android.util.Log;
import com.vpn.ab.MainActivity;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalProxyServer extends Thread {
    private int port;
    private boolean isRunning = true;
    private MainActivity activity; // الربط مع الواجهة للتفاعل اللحظي
    private static final String TAG = "LocalProxyServer";

    // تحديث الكونستركتور لاستقبال الـ Activity
    public LocalProxyServer(int port, MainActivity activity) {
        this.port = port;
        this.activity = activity;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Log.d(TAG, "🚀 سيرفر الوكيل الآمن يعمل على المنفذ: " + port);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    configureSocket(clientSocket);
                    // تشغيل خيط معالجة لكل اتصال لضمان استقرار الدردشة والقنوات
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) Log.e(TAG, "خطأ في استقبال الاتصال: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ تعذر فتح المنفذ " + port + ". تأكد من عدم وجود تطبيق آخر يستخدمه.");
        }
    }

    private void handleClient(Socket clientSocket) {
        Socket serverSocket = null;
        try {
            InputStream inFromClient = clientSocket.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead = inFromClient.read(buffer);
            
            if (bytesRead <= 0) return;

            String request = new String(buffer, 0, bytesRead);
            String targetHost = extractHost(request);

            if (targetHost == null) {
                closeQuietly(clientSocket);
                return;
            }

            // --- نظام الحماية (الحجب الذكي والربط مع الواجهة) ---
            if (isSecurityReportServer(targetHost)) {
                Log.w(TAG, "🚫 تم حجب محاولة إرسال تقرير أمني إلى: " + targetHost);
                
                // تحديث عداد الحجب في الواجهة والتنبيه بالنجاح
                if (activity != null) {
                    activity.incrementBlockedCount();
                }
                
                closeQuietly(clientSocket);
                return;
            }

            Log.d(TAG, "📡 تمرير بيانات آمنة إلى: " + targetHost);

            // فتح قناة الاتصال بالسيرفر الحقيقي (WhatsApp/Google Media)
            serverSocket = new Socket(targetHost, 443);
            configureSocket(serverSocket);

            OutputStream outToServer = serverSocket.getOutputStream();
            outToServer.write(buffer, 0, bytesRead);
            outToServer.flush();

            // إنشاء الجسر الثنائي لنقل البيانات الخام (الرسائل، الوسائط، القنوات)
            final Socket finalServerSocket = serverSocket;
            new Thread(() -> bridge(clientSocket, finalServerSocket)).start();
            new Thread(() -> bridge(finalServerSocket, clientSocket)).start();

        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء معالجة القنوات: " + e.getMessage());
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }
    }

    // --- إعدادات المقبس (Socket) لضمان عدم انقطاع المكالمات والدردشة ---
    private void configureSocket(Socket socket) throws SocketException {
        if (socket != null) {
            socket.setKeepAlive(true);    // الحفاظ على الاتصال حياً
            socket.setTcpNoDelay(true);   // إرسال البيانات فوراً دون تأخير (مهم للرسائل)
            socket.setSoTimeout(0);       // عدم قطع الاتصال بسبب الخمول (0 يعني للأبد)
            socket.setTrafficClass(0x10); // تحسين جودة الخدمة (IP_TOS) لسرعة النقل
        }
    }

    // --- القائمة السوداء المحدثة بناءً على تحليلات ملفات الـ DEX ---
    private boolean isSecurityReportServer(String host) {
        String hostLower = host.toLowerCase();
        String[] blackList = {
            "v.whatsapp.net",           // فحص التوقيع والنسخة
            "integrity.googleapis.com",  // Play Integrity API
            "developer.android.com",     // مراجع الأكواد الأمنية
            "www.recaptcha.net",         // فحص السلوك البشري (Bot)
            "www.gstatic.com/recaptcha",
            "crashlogs.whatsapp.net",    // سجلات الانهيار
            "analytics.whatsapp.net",    // التحليلات السلوكية
            "telemetry.whatsapp.net",    // التقارير عن بعد
            "android.clients.google.com",// فحص أجهزة الأندرويد
            "graph.facebook.com",        // تقارير الشركة الأم (ميتا)
            "graph.whatsapp.com"
        };
        
        for (String domain : blackList) {
            if (hostLower.contains(domain)) return true;
        }
        return false;
    }

    private String extractHost(String request) {
        try {
            // نمط البحث عن الهوست في طلبات الـ HTTP CONNECT والـ Host
            Pattern pattern = Pattern.compile("(^CONNECT |Host: )([^:\r\n\\s]+)", 
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(request);
            if (matcher.find()) return matcher.group(2);
        } catch (Exception e) {
            Log.e(TAG, "فشل استخراج العنوان المستهدف");
        }
        return null;
    }

    private void bridge(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); 
             OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[32768]; // زيادة حجم البفر (32KB) لضمان سرعة تحميل الوسائط
            int n;
            while (isRunning && !from.isClosed() && !to.isClosed() && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // انقطاع طبيعي عند إنهاء الجلسة أو فقدان الشبكة
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public void stopServer() {
        isRunning = false;
        this.interrupt();
    }
}
