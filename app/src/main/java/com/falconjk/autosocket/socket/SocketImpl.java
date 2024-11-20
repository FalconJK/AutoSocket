package com.falconjk.autosocket.socket;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketImpl {
    private static final int HEARTBEAT_INTERVAL = 5000; // 5秒
    private static final String HEARTBEAT_MESSAGE = "ping";
    private boolean isHeartbeatRunning = false;

    private final SocketListener listener;
    private ServerSocket serverSocket;
    private Socket clientSocket;

    private boolean isRunning = false;
    private Handler mainHandler;
    private ExecutorService executor;

    private InputStream inputStream;
    private OutputStream outputStream;

    public interface SocketListener {
        void isStart(boolean s);

        void isConnected(boolean c);

        void receiveMessage(String msg);

        void Error(String err);

        void findServer(String host);
    }

    public SocketImpl(SocketListener listener) {
        this.listener = listener;
        executor = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());
        listener.isConnected(false);
        listener.isStart(false);
    }

    public String getIP() {
        return GetIPAddress.getWifiIPAddress();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void startServer(int port) {
        isRunning = true;
        listener.isStart(true);
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                listener.isConnected(false);

                while (isRunning) {
                    try {
                        Socket newClient = serverSocket.accept();
                        newClient.setKeepAlive(true);
                        newClient.setTcpNoDelay(true);
                        newClient.setSoTimeout(30000);

                        // 如果已有連接，先關閉舊連接
                        closeClientSocket();

                        clientSocket = newClient;
                        setupStreams();
                        startReceiving();
                        startHeartbeat();

                    } catch (IOException e) {
                        if (isRunning) {
                            mainHandler.post(() -> listener.Error("客戶端連接失敗: " + e.getMessage()));
                            closeClientSocket();
                        }
                    }
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    listener.Error("服務器啟動失敗: " + e.getMessage());
                    stop();
                });
            }
        });
    }

    public void startClient(String serverIP, int port) {
        isRunning = true;
        listener.isStart(true);
        executor.execute(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(serverIP, port), 5000);
                setupClient(socket);
            } catch (IOException e) {
                mainHandler.post(() -> {
                    listener.Error("連接失敗: " + e.getMessage());
                    stop();
                });
            }
        });
    }

    private void closeClientSocket() {
        try {
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            listener.isConnected(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startHeartbeat() {
        isHeartbeatRunning = true;
        executor.execute(() -> {
            while (isRunning && isHeartbeatRunning) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    if (isRunning && clientSocket != null && !clientSocket.isClosed()) {
                        sendMessage(HEARTBEAT_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    public void stop() {
        isRunning = false;
        isHeartbeatRunning = false;
        listener.isConnected(false);

        try {
            closeClientSocket();
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
            executor = Executors.newCachedThreadPool();
            listener.isStart(false);
        }
    }

    public void scanForServer(int targetPort) {
        String subnet = getSubnetAddress();
        if (subnet == null) {
            mainHandler.post(() -> listener.Error("無法獲取子網路地址"));
            return;
        }
        executor.execute(() -> {
            for (int i = 1; i < 255; i++) {
//                if (isRunning) break;

                String host = subnet + "." + i;
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(host, targetPort), 100);

                    // 找到服務器，直接使用當前socket
                    mainHandler.post(() -> {
                        listener.findServer(host);
                        listener.receiveMessage("找到服務器: " + host);
                    });

                    // 使用已建立的socket進行連接
                    setupClient(socket);
                    return;

                } catch (IOException ignored) {
                    listener.Error("host:" + host);
                }
            }

            mainHandler.post(() -> listener.Error("沒有找到可用的服務器"));
        });
    }

    private void setupClient(Socket socket) {
        try {
            isRunning = true;
            listener.isStart(true);
            clientSocket = socket;
            clientSocket.setKeepAlive(true);
            clientSocket.setTcpNoDelay(true);
            clientSocket.setSoTimeout(30000);

            setupStreams();
            startReceiving();
            startHeartbeat();

        } catch (IOException e) {
            mainHandler.post(() -> {
                listener.Error("設置連接失敗: " + e.getMessage());
                stop();
            });
        }
    }

    // 獲取本地子網路地址
    private String getSubnetAddress() {
        String ip = getIP();
        if (ip == null) return null;

        int lastDot = ip.lastIndexOf('.');
        if (lastDot != -1) {
            return ip.substring(0, lastDot);
        }
        return null;
    }


    private void setupStreams() throws IOException {
        inputStream = clientSocket.getInputStream();
        outputStream = clientSocket.getOutputStream();
        listener.isConnected(true);
    }

    private void startReceiving() {
        executor.execute(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isRunning) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes, StandardCharsets.UTF_8);
                        if (!message.trim().equals(HEARTBEAT_MESSAGE)) {  // 不處理心跳消息
                            mainHandler.post(() -> listener.receiveMessage(message));
                        }
                    } else if (bytes == -1) {
                        // 連接已關閉
                        throw new IOException("Connection closed by peer");
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        mainHandler.post(() -> {
                            listener.Error("連接中斷: " + e.getMessage());
                            if (serverSocket == null)
                                stop();
                        });
                    }
                    break;
                }
            }
        });
    }

    public void sendMessage(String message) {
        if (clientSocket == null || outputStream == null || !clientSocket.isConnected()) {
            mainHandler.post(() -> listener.Error("連接已斷開，無法發送消息"));
            return;
        }

        executor.execute(() -> {
            try {
                synchronized (outputStream) {  // 同步寫入操作
                    outputStream.write((message).getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    listener.Error("發送失敗: " + e.getMessage());
                    stop();
                });
            }
        });
    }
}
