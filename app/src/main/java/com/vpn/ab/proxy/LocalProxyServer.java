package com.vpn.ab.proxy;

import android.util.Log;
import com.vpn.ab.MainActivity;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * خادم وكيل محلي متطور مصمم لتجاوز طبقات الأمان في واتساب
 * يعتمد تقنية الرد الوهمي (Fake Handshake) وانتحال هوية السيرفر (Server Spoofing)
 */
public class LocalProxyServer extends Thread {
    private final int port;
    private volatile boolean isRunning = true;
    private final MainActivity activity;
    private static final String TAG = "ShieldProxyCore";

    public LocalProxyServer(int port, MainActivity activity) {
        this.port = port;
        this.activity = activity;
    }

    @Override
    public void run() {
        // الاستماع على 0.0.0.0 لضمان قبول الاتصالات من كافة واجهات النظام
        try (ServerSocket serverSocket = new ServerSocket(port, 100, InetAddress.getByName("0.0.0.0"))) {
            Log.d(TAG, "🛡️ درع الحماية نشط على المنفذ: " + port);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    configureSocket(clientSocket);
                    // معالجة كل اتصال في Thread منفصل لضمان عدم تأثر الدردشة بالوسائط
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) Log.e(TAG, "⚠️ خطأ في قبول الاتصال: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ فشل فادح: المنفذ " + port + " محجوز أو الصلاحيات غير كافية.");
        }
    }

    private void handleClient(Socket clientSocket) {
        Socket serverSocket = null;
        try {
            InputStream inFromClient = clientSocket.getInputStream();
            OutputStream outToClient = clientSocket.getOutputStream();
            
            // زيادة حجم البفر لقراءة الترويسات (Headers) بسرعة
            byte[] buffer = new byte[16384];
            int bytesRead = inFromClient.read(buffer);
            
            if (bytesRead <= 0) {
                closeQuietly(clientSocket);
                return;
            }

            String request = new String(buffer, 0, bytesRead);
            String targetHost = extractHost(request);

            if (targetHost == null) {
                closeQuietly(clientSocket);
                return;
            }

            // --- استراتيجية التجاوز الذكي (Silent Drop & Spoofing) ---
            if (isSecurityReportServer(targetHost)) {
                Log.w(TAG, "🚫 تم كشف وحجب محاولة تتبع إلى: " + targetHost);
                
                if (request.startsWith("CONNECT")) {
                    // خداع واتساب: نرد بأن الاتصال تم بنجاح دون فتح اتصال حقيقي بالسيرفر المحظور
                    outToClient.write("HTTP/1.1 200 Connection Established\r\n".getBytes());
                    outToClient.write("Server: nginx/1.18.0 (Ubuntu)\r\n".getBytes()); // انتحال هوية سيرفر ويب حقيقي
                    outToClient.write("Connection: keep-alive\r\n\r\n".getBytes());
                    outToClient.flush();
                }
                
                if (activity != null) activity.incrementBlockedCount();
                // لا نغلق السوكيت فوراً (لنتجنب Connection Reset)، بل نتركه يموت بهدوء
                return; 
            }

            // --- معالجة طلبات الاتصال الحقيقية ---
            serverSocket = new Socket();
            configureSocket(serverSocket);
            // مهلة اتصال قصيرة (5 ثوانٍ) لضمان سرعة الاستجابة لواتساب
            serverSocket.connect(new InetSocketAddress(targetHost, 443), 5000);

            if (request.startsWith("CONNECT")) {
                // الرد الرسمي الذي ينتظره واتساب لبدء التشفير (SSL Handshake)
                outToClient.write("HTTP/1.1 200 Connection Established\r\n".getBytes());
                outToClient.write("Proxy-Agent: ShieldProxy/3.0-Stable\r\n".getBytes());
                outToClient.write("Server: Apache/2.4.41 (Unix)\r\n\r\n".getBytes());
                outToClient.flush();
            } else {
                // تمرير البيانات مباشرة في حال كانت طلبات عادية (Non-CONNECT)
                serverSocket.getOutputStream().write(buffer, 0, bytesRead);
            }

            Log.d(TAG, "📡 قناة آمنة مفتوحة الآن مع: " + targetHost);

            // إنشاء الجسر الثنائي لنقل البيانات (الرسائل، الوسائط، المكالمات)
            final Socket finalServerSocket = serverSocket;
            Thread t1 = new Thread(() -> bridge(clientSocket, finalServerSocket));
            Thread t2 = new Thread(() -> bridge(finalServerSocket, clientSocket));
            
            t1.setPriority(Thread.MAX_PRIORITY); // منح الأولوية القصوى لنقل البيانات
            t1.start();
            t2.start();

        } catch (Exception e) {
            Log.e(TAG, "⚠️ تنبيه: انقطاع في النفق الآمن (" + e.getMessage() + ")");
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }
    }

    private void configureSocket(Socket socket) throws SocketException {
        if (socket != null) {
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true); // تقليل زمن التأخير (Latency) لإرسال الرسائل فوراً
            socket.setSoTimeout(0);     // بقاء القناة مفتوحة للأبد وعدم قطعها عند الخمول
            socket.setReceiveBufferSize(128 * 1024); // بفر استقبال ضخم (128KB) للوسائط
            socket.setSendBufferSize(128 * 1024);    // بفر إرسال ضخم (128KB)
        }
    }

    private boolean isSecurityReportServer(String host) {
        String hostLower = host.toLowerCase();
        // القائمة السوداء المحدثة لروابط التتبع والتقارير الأمنية
        String[] blackList = {
            "v.whatsapp.net",           // فحص النسخة والتوقيع
            "crashlogs.whatsapp.net",    // تقارير الانهيار
            "analytics.whatsapp.net",    // التحليل السلوكي
            "telemetry.whatsapp.net",    // التقارير عن بعد
            "graph.facebook.com",        // ربط الحسابات وتتبع ميتا
            "graph.whatsapp.com",
            "integrity.googleapis.com"   // فحص سلامة النظام (Play Integrity)
        };
        for (String domain : blackList) {
            if (hostLower.contains(domain)) return true;
        }
        return false;
    }

    private String extractHost(String request) {
        try {
            // نمط بحث متطور لاستخراج العنوان من طلبات CONNECT أو Host Header
            Pattern pattern = Pattern.compile("(^CONNECT |Host: )([^:\r\n\\s]+)", 
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(request);
            if (matcher.find()) return matcher.group(2);
        } catch (Exception ignored) {}
        return null;
    }

    private void bridge(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); 
             OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[65536]; // بفر نقل البيانات (64KB) لضمان ثبات القنوات
            int n;
            while (isRunning && !from.isClosed() && !to.isClosed() && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
            // تجاهل أخطاء الانقطاع الطبيعية عند إغلاق التطبيق
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public void stopServer() {
        isRunning = false;
        this.interrupt();
    }
}
