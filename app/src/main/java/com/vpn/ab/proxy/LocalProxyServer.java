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
    private MainActivity activity;
    private static final String TAG = "LocalProxyServer";

    public LocalProxyServer(int port, MainActivity activity) {
        this.port = port;
        this.activity = activity;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Log.d(TAG, "🚀 سيرفر الدرع النشط يعمل الآن على المنفذ: " + port);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    configureSocket(clientSocket);
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) Log.e(TAG, "خطأ في استقبال الاتصال: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ فشل فتح المنفذ " + port + ". قد يكون محجوزاً من قِبل النظام.");
        }
    }

    private void handleClient(Socket clientSocket) {
        Socket serverSocket = null;
        try {
            InputStream inFromClient = clientSocket.getInputStream();
            OutputStream outToClient = clientSocket.getOutputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead = inFromClient.read(buffer);
            
            if (bytesRead <= 0) return;

            String request = new String(buffer, 0, bytesRead);
            String targetHost = extractHost(request);

            if (targetHost == null) {
                closeQuietly(clientSocket);
                return;
            }

            // --- فحص الحجب قبل البدء ---
            if (isSecurityReportServer(targetHost)) {
                Log.w(TAG, "🚫 محجوب: " + targetHost);
                if (activity != null) activity.incrementBlockedCount();
                closeQuietly(clientSocket);
                return;
            }

            // --- الخطوة الحاسمة: الرد على واتساب لفك تعليق "جاري الاتصال" ---
            boolean isConnectRequest = request.startsWith("CONNECT");
            
            // فتح الاتصال بالسيرفر الحقيقي (WhatsApp)
            serverSocket = new Socket(targetHost, 443);
            configureSocket(serverSocket);

            if (isConnectRequest) {
                // إرسال تأكيد الاتصال للعميل (واتساب/المتصفح)
                outToClient.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                outToClient.flush();
            } else {
                // إذا كان طلباً عادياً (HTTP)، نمرره للسيرفر
                OutputStream outToServer = serverSocket.getOutputStream();
                outToServer.write(buffer, 0, bytesRead);
                outToServer.flush();
            }

            Log.d(TAG, "📡 قناة مفتوحة: " + targetHost);

            // إنشاء الجسر الثنائي لنقل البيانات المشفرة
            final Socket finalServerSocket = serverSocket;
            new Thread(() -> bridge(clientSocket, finalServerSocket)).start();
            new Thread(() -> bridge(finalServerSocket, clientSocket)).start();

        } catch (Exception e) {
            Log.e(TAG, "خطأ في النفق: " + e.getMessage());
            closeQuietly(clientSocket);
            closeQuietly(serverSocket);
        }
    }

    private void configureSocket(Socket socket) throws SocketException {
        if (socket != null) {
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            socket.setTrafficClass(0x10); 
        }
    }

    private boolean isSecurityReportServer(String host) {
        String hostLower = host.toLowerCase();
        String[] blackList = {
            "v.whatsapp.net",
            "integrity.googleapis.com",
            "developer.android.com",
            "www.recaptcha.net",
            "www.gstatic.com/recaptcha",
            "crashlogs.whatsapp.net",
            "analytics.whatsapp.net",
            "telemetry.whatsapp.net",
            "android.clients.google.com",
            "graph.facebook.com",
            "graph.whatsapp.com"
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
        } catch (Exception e) {
            Log.e(TAG, "فشل استخراج الهوست");
        }
        return null;
    }

    private void bridge(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); 
             OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[32768]; 
            int n;
            while (isRunning && !from.isClosed() && !to.isClosed() && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // انقطاع طبيعي
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
