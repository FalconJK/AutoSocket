package com.falconjk.autosocket;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.falconjk.autosocket.socket.SocketImpl;

public class MainActivity extends Activity implements SocketImpl.SocketListener {
    private RadioGroup modeRadioGroup;
    private RadioButton serverRadio, clientRadio;
    private EditText portEditText, inputText;
    private Button startButton, sendButton;
    private TextView statusText, messageText;


    private TextView localIpText;
    private LinearLayout serverIpLayout;
    private EditText serverIpEditText;
    private SocketImpl socketImpl;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();

        localIpText.setText("本機IP: " + socketImpl.getIP());
        serverRadio.setChecked(true);

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
        localIpText = findViewById(R.id.localIpText);
        serverIpLayout = findViewById(R.id.serverIpLayout);
        serverIpEditText = findViewById(R.id.serverIpEditText);
        scrollView = findViewById(R.id.scrollView);
        socketImpl = new SocketImpl(this);
    }

    private void setupListeners() {
        startButton.setOnClickListener(v -> {
            if (!socketImpl.isRunning()) {
                startConnection();
            } else {
                socketImpl.stop();
            }
        });

        sendButton.setOnClickListener(v -> {
            String message = inputText.getText().toString();
            if (!message.isEmpty()) {
                if (socketImpl.isRunning()) {
                    socketImpl.sendMessage(message);
                    appendMessage("發送: " + message);
                    inputText.setText("");
                } else {
                    appendMessage("無法發送!!!");
                }

            }
        });
    }

    public void appendMessage(String message) {
        messageText.append(message+"\n");
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    private void startConnection() {
        int port;
        try {
            port = Integer.parseInt(portEditText.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "請輸入有效的端口號", Toast.LENGTH_SHORT).show();
            return;
        }

        if (serverRadio.isChecked()) {
            socketImpl.startServer(port);
        } else {
            String serverIP = serverIpEditText.getText().toString();
            if (serverIP.isEmpty()) {
                socketImpl.scanForServer(port);
            } else {
                socketImpl.startClient(serverIP, port);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socketImpl != null) {
            socketImpl.stop();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void isStart(boolean start) {
        runOnUiThread(() -> {
            sendButton.setEnabled(start);
            modeRadioGroup.setEnabled(!start);
            portEditText.setEnabled(!start);
            serverRadio.setEnabled(!start);
            clientRadio.setEnabled(!start);
            startButton.setText(!start ? "啟動" : "停止");
            statusText.setText("狀態: " + (start ? "已啟動" : "未啟動"));
            statusText.setTextColor(start ? Color.GREEN : Color.RED);
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void isConnected(boolean connect) {
        runOnUiThread(() -> {
            sendButton.setEnabled(connect);
            statusText.setText("狀態: " + (connect ? "已連接" : "等待連接..."));
            statusText.setTextColor(connect ? Color.GREEN : Color.BLUE);
            serverIpEditText.setEnabled(!connect);
        });
    }

    @Override
    public void receiveMessage(String msg) {
        runOnUiThread(() -> {
            appendMessage("收到: " + msg);
        });
    }

    @Override
    public void Error(String err) {
        runOnUiThread(() -> {
            appendMessage(err);
        });
    }

    @Override
    public void findServer(String host) {
        runOnUiThread(() -> {
            serverIpEditText.setText(host);
        });
    }
}
