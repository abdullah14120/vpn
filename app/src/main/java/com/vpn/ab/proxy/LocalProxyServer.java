package com.vpn.ab.proxy;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalProxyServer extends Thread {
    private int port;
    private boolean isRunning = true;
    private static final String TAG = "LocalProxyServer";

    public LocalProxyServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Log.d(TAG, "سيرفر الوكيل (الربط اليدوي) يعمل على منفذ: " + port);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // تفعيل Keep-Alive للاتصال القادم من واتساب
                    configureSocket(clientSocket);
                    
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) Log.e(TAG, "خطأ في استقبال الاتصال: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "تعذر فتح المنفذ " + port + ". تأكد أنه غير مستخدم.");
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
                clientSocket.close();
                return;
            }

            // --- نظام الحماية (الحجب الذكي) ---
            if (isSecurityReportServer(targetHost)) {
                Log.w(TAG, "🚫 محاولة إرسال تقرير أمني محجوبة: " + targetHost);
                clientSocket.close();
                return;
            }

            Log.d(TAG, "📡 تمرير بيانات آمنة إلى: " + targetHost);

            // الاتصال بالسيرفر الحقيقي
            serverSocket = new Socket(targetHost, 443);
            configureSocket(serverSocket); // تفعيل Keep-Alive للسيرفر أيضاً

            OutputStream outToServer = serverSocket.getOutputStream();
            outToServer.write(buffer, 0, bytesRead);
            outToServer.flush();

            // إنشاء الجسر الثنائي لنقل البيانات
            final Socket finalServerSocket = serverSocket;
            new Thread(() -> bridge(clientSocket, finalServerSocket)).start();
            new Thread(() -> bridge(finalServerSocket, clientSocket)).start();

        } catch (Exception e) {
            Log.e(TAG, "خطأ في معالجة القنوات: " + e.getMessage());
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }
    }

    // --- دالة Keep-Alive والتحسين ---
    private void configureSocket(Socket socket) throws SocketException {
        if (socket != null) {
            socket.setKeepAlive(true); // الحفاظ على الاتصال حياً
            socket.setTcpNoDelay(true); // تقليل التأخير (Latency) لإرسال الرسائل فوراً
            socket.setSoTimeout(0); // عدم إنهاء الاتصال بسبب الخمول (مهم للمكالمات)
        }
    }

    private boolean isSecurityReportServer(String host) {
        String[] blackList = {
            "v.whatsapp.net",
            "crashlogs.whatsapp.net",
            "analytics.whatsapp.net",
            "telemetry.whatsapp.net",
            "integrity.googleapis.com",
            "android.clients.google.com"
        };
        for (String domain : blackList) {
            if (host.toLowerCase().contains(domain)) return true;
        }
        return false;
    }

    private String extractHost(String request) {
        try {
            Pattern pattern = Pattern.compile("(^CONNECT |Host: )([^:\r\n\\s]+)", 
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(request);
            if (matcher.find()) return matcher.group(2);
        } catch (Exception e) {
            Log.e(TAG, "فشل استخراج الهوست");
        }
        return null;
    }

    private void bridge(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); 
             OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[16384]; // حجم أكبر للوسائط
            int n;
            while (isRunning && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // انقطاع طبيعي عند إغلاق التطبيق أو المحادثة
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public void stopServer() {
        isRunning = false;
        this.interrupt();
    }
}
