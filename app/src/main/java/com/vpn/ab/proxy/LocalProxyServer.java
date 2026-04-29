package com.vpn.ab.proxy;

import android.util.Log;
import com.vpn.ab.MainActivity;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalProxyServer extends Thread {
    private int port;
    private volatile boolean isRunning = true;
    private MainActivity activity;
    private static final String TAG = "ShieldProxy";

    public LocalProxyServer(int port, MainActivity activity) {
        this.port = port;
        this.activity = activity;
    }

    @Override
    public void run() {
        // استخدام 0.0.0.0 للسماح بالاتصال من كافة واجهات الجهاز
        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            Log.d(TAG, "🛡️ السيرفر نشط ومستقر على المنفذ: " + port);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    configureSocket(clientSocket);
                    // معالجة كل اتصال في Thread مستقل لضمان عدم توقف الدردشة
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) Log.e(TAG, "خطأ في استقبال الطلب: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ خطأ فادح: تعذر فتح المنفذ " + port);
        }
    }

    private void handleClient(Socket clientSocket) {
        Socket serverSocket = null;
        try {
            InputStream inFromClient = clientSocket.getInputStream();
            OutputStream outToClient = clientSocket.getOutputStream();
            
            // قراءة رأس الطلب (Header)
            byte[] buffer = new byte[16384]; // زيادة حجم القراءة الأولية
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

            // فحص روابط التتبع والحظر
            if (isSecurityReportServer(targetHost)) {
                Log.w(TAG, "🚫 تم صد محاولة تتبع: " + targetHost);
                if (activity != null) activity.incrementBlockedCount();
                closeQuietly(clientSocket);
                return;
            }

            // فتح اتصال مع سيرفرات واتساب الحقيقية
            serverSocket = new Socket();
            configureSocket(serverSocket);
            serverSocket.connect(new InetSocketAddress(targetHost, 443), 10000); // مهلة اتصال 10 ثواني

            // الرد على واتساب لفك تعليق "جاري الاتصال"
            if (request.startsWith("CONNECT")) {
                outToClient.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                outToClient.flush();
            } else {
                // تمرير البيانات إذا لم يكن طلب CONNECT (نادر في واتساب)
                serverSocket.getOutputStream().write(buffer, 0, bytesRead);
            }

            // الجسر الثنائي لنقل البيانات المشفرة (الدردشة والوسائط)
            final Socket finalServerSocket = serverSocket;
            Thread t1 = new Thread(() -> bridge(clientSocket, finalServerSocket));
            Thread t2 = new Thread(() -> bridge(finalServerSocket, clientSocket));
            
            t1.start();
            t2.start();

        } catch (Exception e) {
            Log.e(TAG, "⚠️ فشل النفق مع: " + e.getMessage());
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }
    }

    private void configureSocket(Socket socket) throws SocketException {
        if (socket != null) {
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true); // تقليل التأخير في إرسال الرسائل
            socket.setSoTimeout(0);     // عدم قطع الاتصال بسبب الخمول
            socket.setSendBufferSize(65536); // زيادة حجم البفر للإرسال
            socket.setReceiveBufferSize(65536); // زيادة حجم البفر للاستقبال
        }
    }

    private boolean isSecurityReportServer(String host) {
        String hostLower = host.toLowerCase();
        String[] blackList = {
            "v.whatsapp.net", "integrity.googleapis.com", 
            "crashlogs.whatsapp.net", "analytics.whatsapp.net", 
            "telemetry.whatsapp.net", "graph.facebook.com"
        };
        for (String domain : blackList) {
            if (hostLower.contains(domain)) return true;
        }
        return false;
    }

    private String extractHost(String request) {
        try {
            Pattern pattern = Pattern.compile("(^CONNECT |Host: )([^:\r\n\\s]+)", 
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(request);
            if (matcher.find()) return matcher.group(2);
        } catch (Exception e) {}
        return null;
    }

    private void bridge(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); 
             OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[65536]; // بفر كبير جداً (64KB) لثبات الوسائط
            int n;
            while (isRunning && !from.isClosed() && !to.isClosed() && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // تجاهل أخطاء الانقطاع العادي
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
