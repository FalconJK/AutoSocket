package com.falconjk.autosocket;
import android.app.Activity;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private RadioGroup modeRadioGroup;
    private RadioButton serverRadio, clientRadio;
    private EditText portEditText, inputText;
    private Button startButton, sendButton;
    private TextView statusText, messageText;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private boolean isRunning = false;
    private ExecutorService executor;
    private Handler mainHandler;

    private InputStream inputStream;
    private OutputStream outputStream;

    private TextView localIpText;
    private LinearLayout serverIpLayout;
    private EditText serverIpEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();

        executor = Executors.newCachedThreadPool();
        mainHandler = new Handler(Looper.getMainLooper());

        // 顯示設備IP
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());

        // 初始狀態設定
        sendButton.setEnabled(false);

        localIpText = findViewById(R.id.localIpText);
        serverIpLayout = findViewById(R.id.serverIpLayout);
        serverIpEditText = findViewById(R.id.serverIpEditText);

        // 顯示本機IP
        localIpText.setText("本機IP: " + ip);

        // 設置RadioGroup的監聽器
        modeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            serverIpLayout.setVisibility(checkedId == R.id.clientRadio ? View.VISIBLE : View.GONE);
        });
    }

    private void initializeViews() {
        modeRadioGroup = findViewById(R.id.modeRadioGroup);
        serverRadio = findViewById(R.id.serverRadio);
        clientRadio = findViewById(R.id.clientRadio);
        portEditText = findViewById(R.id.portEditText);
        inputText = findViewById(R.id.inputText);
        startButton = findViewById(R.id.startButton);
        sendButton = findViewById(R.id.sendButton);
        statusText = findViewById(R.id.statusText);
        messageText = findViewById(R.id.messageText);
    }

    private void setupListeners() {
        startButton.setOnClickListener(v -> {
            if (!isRunning) {
                startConnection();
            } else {
                stopConnection();
            }
        });

        sendButton.setOnClickListener(v -> {
            String message = inputText.getText().toString();
            if (!message.isEmpty()) {
                sendMessage(message);
                inputText.setText("");
            }
        });
    }

    private void startConnection() {
        int port;
        try {
            port = Integer.parseInt(portEditText.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "請輸入有效的端口號", Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = true;
        startButton.setText("停止");
        modeRadioGroup.setEnabled(false);
        portEditText.setEnabled(false);

        if (serverRadio.isChecked()) {
            startServer(port);
        } else {
            startClient(port);
        }
    }

    private void startServer(int port) {
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                updateStatus("等待客戶端連接...", Color.BLUE);

                clientSocket = serverSocket.accept();
                setupStreams();
                updateStatus("已連接", Color.GREEN);

                startReceiving();

            } catch (IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "服務器啟動失敗: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    stopConnection();
                });
            }
        });
    }

    private void startClient(int port) {
        String serverIP = serverIpEditText.getText().toString();
        if (serverIP.isEmpty()) {
            Toast.makeText(this, "請輸入服務器IP", Toast.LENGTH_SHORT).show();
            stopConnection();
            return;
        }

        executor.execute(() -> {
            try {
                clientSocket = new Socket(serverIP, port);
                setupStreams();
                updateStatus("已連接", Color.GREEN);
                startReceiving();
            } catch (IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "連接失敗: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    stopConnection();
                });
            }
        });
    }

    private void setupStreams() throws IOException {
        inputStream = clientSocket.getInputStream();
        outputStream = clientSocket.getOutputStream();
        mainHandler.post(() -> sendButton.setEnabled(true));
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
                        appendMessage("收到: " + message);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        mainHandler.post(() -> {
                            Toast.makeText(MainActivity.this, "連接中斷",
                                    Toast.LENGTH_SHORT).show();
                            stopConnection();
                        });
                    }
                    break;
                }
            }
        });
    }

    private void sendMessage(String message) {
        executor.execute(() -> {
            try {
                outputStream.write(message.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                appendMessage("發送: " + message);
            } catch (IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "發送失敗: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void stopConnection() {
        isRunning = false;
        sendButton.setEnabled(false);
        modeRadioGroup.setEnabled(true);
        portEditText.setEnabled(true);
        startButton.setText("啟動");
        updateStatus("未連接", Color.RED);

        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        serverSocket = null;
        clientSocket = null;
        inputStream = null;
        outputStream = null;
    }

    private void updateStatus(String status, int color) {
        mainHandler.post(() -> {
            statusText.setText("狀態: " + status);
            statusText.setTextColor(color);
        });
    }

    private void appendMessage(String message) {
        mainHandler.post(() -> {
            messageText.append(message + "\n");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopConnection();
        executor.shutdown();
    }
}
