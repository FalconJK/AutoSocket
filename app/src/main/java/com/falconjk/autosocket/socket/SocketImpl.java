package com.falconjk.autosocket.socket;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketImpl {

    private final SocketListener listener;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    
    private boolean isRunning = false;
    private Handler mainHandler;
    private ExecutorService executor;

    private InputStream inputStream;
    private OutputStream outputStream;
    
    public interface SocketListener{
        public void isStart(boolean s);

        public void isConnected(boolean c);
        
        public void receiveMessage(String msg);

        public void Error(String err);
    }

    public SocketImpl(SocketListener listener) {
        this.listener = listener;
        executor = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());
        listener.isStart(false);
        listener.isConnected(false);
    }
    
    public String getIP(){
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
//                updateStatus("等待客戶端連接...", Color.BLUE);

                clientSocket = serverSocket.accept();
                setupStreams();
//                updateStatus("已連接", Color.GREEN);

                startReceiving();

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
                clientSocket = new Socket(serverIP, port);
                setupStreams();
//                updateStatus("已連接", Color.GREEN);
                startReceiving();
            } catch (IOException e) {
                mainHandler.post(() -> {
                    listener.Error("連接失敗: " + e.getMessage());
                    stop();
                });
            }
        });
    }

    public void stop() {
        isRunning = false;
        listener.isConnected(false);

        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            serverSocket = null;
            clientSocket = null;
            inputStream = null;
            outputStream = null;

            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
            executor = Executors.newCachedThreadPool();  // 重新初始化
            listener.isStart(false);
        }
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
                        listener.receiveMessage(message);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        mainHandler.post(() -> {
                            listener.Error("連接中斷");
                            stop();
                        });
                    }
                    break;
                }
            }
        });
    }

    public void sendMessage(String message) {
        executor.execute(() -> {
            try {
                outputStream.write(message.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                mainHandler.post(() -> {
                    listener.Error("發送失敗: " + e.getMessage());
                });
            }
        });
    }
}
