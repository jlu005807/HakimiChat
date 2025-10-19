package com.example.hakimichat;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientManager {
    private static final String TAG = "ClientManager";
    private static final int SERVER_PORT = AppConstants.SERVER_PORT;
    private static final int TIMEOUT = AppConstants.CONNECTION_TIMEOUT;
    
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private ExecutorService executorService;
    private MessageListener messageListener;
    private boolean isConnected;

    public interface MessageListener {
        void onMessageReceived(Message message);
        void onConnected();
        void onDisconnected();
        void onConnectionError(String error);
        void onNicknameValidated(String validatedNickname); // 昵称验证完成回调
    }

    public ClientManager(MessageListener listener) {
        this.messageListener = listener;
        this.executorService = Executors.newCachedThreadPool();
        this.isConnected = false;
    }

    public void connect(String serverIp) {
        executorService.execute(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, SERVER_PORT), TIMEOUT);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                
                outputStream = new ObjectOutputStream(socket.getOutputStream());
                outputStream.flush();
                inputStream = new ObjectInputStream(socket.getInputStream());
                
                isConnected = true;
                Log.d(TAG, "Connected to server: " + serverIp);
                
                if (messageListener != null) {
                    messageListener.onConnected();
                }
                
                // Start listening for messages
                startListening();
                
            } catch (IOException e) {
                Log.e(TAG, "Connection error", e);
                isConnected = false;
                if (messageListener != null) {
                    messageListener.onConnectionError("无法连接到服务器: " + e.getMessage());
                }
            }
        });
    }

    private void startListening() {
        executorService.execute(() -> {
            try {
                while (isConnected && socket != null && !socket.isClosed()) {
                    Message message = (Message) inputStream.readObject();
                    
                    // 处理昵称验证结果
                    if (message.getMessageType() == Message.TYPE_NICKNAME_RESULT) {
                        String validatedNickname = message.getValidatedNickname();
                        Log.d(TAG, "收到验证后的昵称: " + validatedNickname);
                        if (messageListener != null) {
                            messageListener.onNicknameValidated(validatedNickname);
                        }
                        continue;
                    }
                    
                    // 处理被踢消息 - 仅通知 UI，不做本地断开
                    if (message.getMessageType() == Message.TYPE_KICK) {
                        String targetNickname = message.getTargetNickname();
                        Log.d(TAG, "收到踢出消息(仅通知UI): targetNickname=" + targetNickname);
                        if (messageListener != null) {
                            messageListener.onMessageReceived(message);
                        }
                        // 不在这里断开，等待服务器主动关闭连接，触发 onDisconnected()
                        continue;
                    }
                    
                    Log.d(TAG, "Received message: " + message.getContent());
                    
                    // 发送消息前检查监听器是否还存在
                    if (messageListener != null) {
                        try {
                            messageListener.onMessageReceived(message);
                        } catch (Exception e) {
                            Log.e(TAG, "回调 onMessageReceived 时出错", e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "消息接收循环异常", e);
                disconnect();
            } finally {
                Log.d(TAG, "消息接收循环已退出");
            }
        });
    }
    
    /**
     * 发送昵称检查请求
     */
    public void checkNickname(String nickname) {
        Message nicknameCheck = Message.createNicknameCheckMessage(nickname);
        sendMessage(nicknameCheck);
        Log.d(TAG, "发送昵称检查请求: " + nickname);
    }

    public synchronized void sendMessage(Message message) {
        executorService.execute(() -> {
            synchronized (this) {
                try {
                    if (outputStream != null && isConnected && !socket.isClosed()) {
                        outputStream.writeObject(message);
                        outputStream.flush();
                        outputStream.reset(); // 防止对象缓存问题
                        Log.d(TAG, "Message sent: " + message.getContent());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error sending message", e);
                    // 只有在连接状态下才断开并通知
                    if (isConnected && messageListener != null) {
                        disconnect();
                    }
                }
            }
        });
    }

    public void disconnect() {
        isConnected = false;
        try {
            Log.d(TAG, "断开连接 - 开始关闭资源");
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.flush();
                outputStream.close();
                outputStream = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            executorService.shutdown();
            Log.d(TAG, "断开连接 - 所有资源已关闭");
            
            if (messageListener != null) {
                messageListener.onDisconnected();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting", e);
        }
    }

    // 移除静默断开逻辑，统一通过 disconnect() 做收尾

    public boolean isConnected() {
        return isConnected && socket != null && !socket.isClosed();
    }
}
