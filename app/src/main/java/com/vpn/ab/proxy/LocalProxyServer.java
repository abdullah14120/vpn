package com.vpn.ab.proxy;

import android.util.Log;
import com.vpn.ab.MainActivity;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalProxyServer extends Thread {
    private final int port;
    private volatile boolean isRunning = true;
    private final MainActivity activity;
    private static final String TAG = "PureProxy";

    public LocalProxyServer(int port, MainActivity activity) {
        this.port = port;
        this.activity = activity;
    }

    @Override
    public void run() {
        // الاستماع على كافة الواجهات لضمان وصول طلبات الواتساب
        try (ServerSocket serverSocket = new ServerSocket(port, 100, InetAddress.getByName("0.0.0.0"))) {
            Log.i(TAG, "🚀 وكيل خام شفاف يعمل الآن على المنفذ: " + port);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // إعداد المقبس ليكون سريعاً جداً وغير قابل للقطع
                    clientSocket.setKeepAlive(true);
                    clientSocket.setTcpNoDelay(true);
                    
                    new Thread(() -> handleConnection(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) Log.e(TAG, "خطأ في استقبال الاتصال: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ فشل فتح المنفذ: " + e.getMessage());
        }
    }

    private void handleConnection(Socket clientSocket) {
        Socket remoteSocket = null;
        try {
            InputStream inFromClient = clientSocket.getInputStream();
            OutputStream outToClient = clientSocket.getOutputStream();
            
            byte[] buffer = new byte[16384];
            int bytesRead = inFromClient.read(buffer);
            if (bytesRead <= 0) return;

            String request = new String(buffer, 0, bytesRead);
            String host = extractHost(request);

            if (host == null) {
                clientSocket.close();
                return;
            }

            // الاتصال بالسيرفر المستهدف (واتساب) مباشرة وبسرعة
            remoteSocket = new Socket();
            remoteSocket.setKeepAlive(true);
            remoteSocket.setTcpNoDelay(true);
            remoteSocket.connect(new InetSocketAddress(host, 443), 5000);

            // الرد ببروتوكول HTTP Standard الذي يطلبه واتساب
            if (request.startsWith("CONNECT")) {
                outToClient.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                outToClient.flush();
            } else {
                remoteSocket.getOutputStream().write(buffer, 0, bytesRead);
            }

            // جسر بيانات ثنائي الاتجاه (بأعلى سرعة ممكنة)
            final Socket finalRemote = remoteSocket;
            Thread t1 = new Thread(() -> bridge(clientSocket, finalRemote));
            Thread t2 = new Thread(() -> bridge(finalRemote, clientSocket));
            
            t1.start();
            t2.start();

        } catch (Exception e) {
            closeSocket(clientSocket);
            closeSocket(remoteSocket);
        }
    }

    private void bridge(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); 
             OutputStream out = to.getOutputStream()) {
            byte[] buffer = new byte[32768]; // 32KB بفر متوازن
            int n;
            while (isRunning && !from.isClosed() && !to.isClosed() && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeSocket(from);
            closeSocket(to);
        }
    }

    private String extractHost(String request) {
        try {
            Pattern pattern = Pattern.compile("(^CONNECT |Host: )([^:\r\n\\s]+)", 
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(request);
            if (matcher.find()) return matcher.group(2);
        } catch (Exception ignored) {}
        return null;
    }

    private void closeSocket(Socket s) {
        try { if (s != null) s.close(); } catch (IOException ignored) {}
    }

    public void stopServer() {
        isRunning = false;
        this.interrupt();
    }
}
