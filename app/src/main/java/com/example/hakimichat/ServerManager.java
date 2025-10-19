package com.example.hakimichat;

import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerManager {

    private static final String TAG = "ServerManager";
    private static final int SERVER_PORT = AppConstants.SERVER_PORT;
    private static final int MAX_HISTORY_SIZE = AppConstants.MAX_HISTORY_SIZE; // 最多保存30条历史消息
    
    private ServerSocket serverSocket;
    private CopyOnWriteArrayList<ClientHandler> clients;
    private CopyOnWriteArrayList<String> usedNicknames; // 已使用的昵称列表
    private CopyOnWriteArrayList<Message> messageHistory; // 历史消息列表
    private ExecutorService executorService;
    private MessageListener messageListener;
    private boolean isRunning;
    private int visitorCounter = 1; // 访客计数器
    private String hostNickname; // 房主昵称

    public interface MessageListener {
        void onMessageReceived(Message message);
        void onClientConnected(String clientInfo);
        void onClientDisconnected(String clientInfo);
    }

    public ServerManager(MessageListener listener) {
        this.messageListener = listener;
        this.clients = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.usedNicknames = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.messageHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.executorService = Executors.newCachedThreadPool();
        this.isRunning = false;
    }
    
    /**
     * 验证并调整昵称，确保唯一性
     */
    private String validateNickname(String nickname) {
        String baseName;
        boolean isDefaultNickname = false;
        
        // 如果昵称为空，生成默认昵称
        if (nickname == null || nickname.trim().isEmpty()) {
            baseName = "哈基米";
            isDefaultNickname = true;
        } else {
            // 验证昵称长度
            String trimmedNickname = nickname.trim();
            if (trimmedNickname.length() > AppConstants.MAX_NICKNAME_LENGTH) {
                // 昵称超长，截断到最大长度
                trimmedNickname = trimmedNickname.substring(0, AppConstants.MAX_NICKNAME_LENGTH);
                Log.d(TAG, "昵称过长，已截断: " + nickname + " -> " + trimmedNickname);
            }
            
            // 如果用户指定了昵称并且不在已用列表中，则直接使用
            if (!usedNicknames.contains(trimmedNickname)) {
                usedNicknames.add(trimmedNickname);
                Log.d(TAG, "昵称验证: " + trimmedNickname + " 可以直接使用");
                return trimmedNickname;
            }
            
            // 如果用户指定了带数字的昵称，如"哈基米2"，先尝试提取基础昵称
            baseName = trimmedNickname;
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(.*?)(\\d+)$");
            java.util.regex.Matcher matcher = pattern.matcher(trimmedNickname);
            
            if (matcher.matches()) {
                // 如果找到了数字后缀，提取基础昵称
                baseName = matcher.group(1);
            }
        }
        
        // 先尝试基础昵称本身（如"哈基米"）
        if (!usedNicknames.contains(baseName)) {
            usedNicknames.add(baseName);
            // 如果是默认昵称并使用了基础名称，增加计数器
            if (isDefaultNickname) {
                visitorCounter++;
            }
            Log.d(TAG, "昵称验证: " + nickname + " -> " + baseName + " (使用基础昵称)");
            return baseName;
        }
        
        // 然后按顺序寻找第一个可用的数字后缀
        int suffix = 1;
        String newNickname;
        while (true) {
            newNickname = baseName + suffix;
            if (!usedNicknames.contains(newNickname)) {
                usedNicknames.add(newNickname);
                // 如果是默认昵称，更新计数器以避免重复
                if (isDefaultNickname) {
                    visitorCounter = Math.max(visitorCounter, suffix + 1);
                }
                Log.d(TAG, "昵称验证: " + nickname + " -> " + newNickname + " (找到可用编号 " + suffix + ")");
                return newNickname;
            }
            suffix++;
        }
    }
    
    /**
     * 移除昵称
     */
    private void removeNickname(String nickname) {
        if (nickname != null) {
            boolean removed = usedNicknames.remove(nickname);
            Log.d(TAG, "移除昵称: " + nickname + (removed ? " 成功" : " 失败（不存在）"));
        }
    }
    
    /**
     * 注册房主昵称
     */
    public String registerHostNickname(String nickname) {
        // 直接复用验证昵称的逻辑，确保一致性
        String validatedNickname = validateNickname(nickname);
        this.hostNickname = validatedNickname;
        Log.d(TAG, "房主昵称注册: " + nickname + " -> " + validatedNickname);
        
        return validatedNickname;
    }
    
    /**
     * 获取所有在线成员昵称列表（包括房主）
     */
    public List<String> getMemberList() {
        List<String> members = new ArrayList<>();
        if (hostNickname != null) {
            members.add(hostNickname); // 房主排第一
        }
        for (ClientHandler client : clients) {
            if (client.clientNickname != null) {
                members.add(client.clientNickname);
            }
        }
        return members;
    }
    
    /**
     * 广播成员列表给所有客户端
     */
    public void broadcastMemberList() {
        List<String> members = getMemberList();
        String[] memberArray = members.toArray(new String[0]);
        Message memberListMsg = Message.createMemberListMessage(memberArray);
        
        // 广播给所有客户端
        broadcastMessage(memberListMsg);
        
        // 同时通知房主端更新（因为broadcastMessage不会发送给房主自己）
        if (messageListener != null) {
            messageListener.onMessageReceived(memberListMsg);
        }
        
        Log.d(TAG, "广播成员列表(包括房主): " + members.toString());
    }
    
    /**
     * 添加消息到历史记录
     */
    private void addToHistory(Message message) {
        // 只保存普通消息到历史记录
        if (message.getMessageType() == Message.TYPE_NORMAL) {
            messageHistory.add(message);
            // 保持历史记录在30条以内
            while (messageHistory.size() > MAX_HISTORY_SIZE) {
                messageHistory.remove(0);
            }
            Log.d(TAG, "消息已添加到历史记录，当前历史消息数: " + messageHistory.size());
        }
    }
    
    /**
     * 获取历史消息列表
     */
    public List<Message> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }
    
    /**
     * 踢出指定昵称的成员
     */
    public void kickMember(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            Log.w(TAG, "踢出成员失败: 昵称为空");
            return;
        }
        
        Log.d(TAG, "准备踢出成员: " + nickname);
        ClientHandler targetClient = null;
        
        // 先找到目标客户端
        for (ClientHandler client : clients) {
            if (nickname.equals(client.clientNickname)) {
                targetClient = client;
                break;
            }
        }
        
        if (targetClient == null) {
            Log.w(TAG, "踢出成员失败: 未找到昵称为 " + nickname + " 的客户端");
            return;
        }
        
        // 简化逻辑：在后台线程中发送一次踢出消息后立即关闭连接
        final ClientHandler clientToKick = targetClient;
        executorService.execute(() -> {
            try {
                Message kickMsg = Message.createKickMessage(nickname);
                Log.d(TAG, "创建踢人消息: targetNickname=" + kickMsg.getTargetNickname() + ", messageType=" + kickMsg.getMessageType());
                clientToKick.sendMessage(kickMsg); // 内部已 flush
                Log.d(TAG, "已发送踢出消息给: " + nickname);
            } catch (Exception e) {
                Log.e(TAG, "发送踢出消息时出错: " + nickname, e);
            } finally {
                try {
                    Log.d(TAG, "立即关闭被踢客户端连接: " + nickname);
                    clientToKick.close(); // close 内部会移除、通知并广播成员列表
                } catch (Exception closeErr) {
                    Log.e(TAG, "关闭被踢客户端连接时出错: " + nickname, closeErr);
                }
            }
        });
    }

    public void startServer() {
        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                isRunning = true;
                Log.d(TAG, "Server started on port: " + SERVER_PORT);

                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setKeepAlive(true);
                        clientSocket.setTcpNoDelay(true);
                        
                        ClientHandler clientHandler = new ClientHandler(clientSocket);
                        clients.add(clientHandler);
                        executorService.execute(clientHandler);
                        Log.d(TAG, "Client connected: " + clientSocket.getInetAddress());
                        
                        if (messageListener != null) {
                            messageListener.onClientConnected(clientSocket.getInetAddress().toString());
                        }
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Server error", e);
            }
        });
    }

    public void broadcastMessage(Message message) {
        Log.d(TAG, "Broadcasting message to " + clients.size() + " clients");
        
        // 将普通消息添加到历史记录
        addToHistory(message);
        
        executorService.execute(() -> {
            for (ClientHandler client : clients) {
                try {
                    client.sendMessage(message);
                } catch (Exception e) {
                    Log.e(TAG, "Error broadcasting to client", e);
                }
            }
        });
    }

    public void stopServer() {
        isRunning = false;
        try {
            for (ClientHandler client : clients) {
                try {
                    client.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing client", e);
                }
            }
            clients.clear();
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            executorService.shutdown();
            Log.d(TAG, "Server stopped");
        } catch (IOException e) {
            Log.e(TAG, "Error stopping server", e);
        }
    }

    public int getPort() {
        return SERVER_PORT;
    }

    public int getConnectedClientCount() {
        return clients.size();
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream outputStream;
        private ObjectInputStream inputStream;
        private String clientNickname; // 客户端昵称

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.outputStream = new ObjectOutputStream(socket.getOutputStream());
                this.inputStream = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                Log.e(TAG, "Error creating streams", e);
            }
        }

        @Override
        public void run() {
            try {
                while (isRunning && !socket.isClosed()) {
                    Message message = (Message) inputStream.readObject();
                    
                    // 处理昵称检查消息
                    if (message.getMessageType() == Message.TYPE_NICKNAME_CHECK) {
                        String requestedNickname = message.getSender();
                        String validatedNickname = validateNickname(requestedNickname);
                        this.clientNickname = validatedNickname;
                        // 发送验证结果回客户端
                        Message resultMessage = Message.createNicknameResultMessage(validatedNickname);
                        sendMessage(resultMessage);
                        Log.d(TAG, "昵称验证完成: " + requestedNickname + " -> " + validatedNickname);
                        
                        // 发送历史消息给新加入的用户
                        sendHistoryToClient();
                        
                        // 昵称验证成功后广播成员列表
                        broadcastMemberList();
                        continue;
                    }
                    // 客户端不应该发送踢人消息，踢人由房主通过kickMember()方法直接调用
                    // 如果收到踢人消息，说明是错误的消息，忽略
                    if (message.getMessageType() == Message.TYPE_KICK) {
                        Log.w(TAG, "客户端发送踢人消息，忽略");
                        continue;
                    }
                    // 客户端请求成员列表
                    if (message.getMessageType() == Message.TYPE_MEMBER_LIST) {
                        broadcastMemberList();
                        continue;
                    }
                    // 处理其他类型消息
                    Log.d(TAG, "Received message: " + message.getContent());
                    if (messageListener != null && message.getMessageType() != Message.TYPE_NICKNAME_RESULT) {
                        messageListener.onMessageReceived(message);
                    }
                    // 转发给所有其他客户端（不包括昵称相关消息）
                    if (message.getMessageType() == Message.TYPE_NORMAL) {
                        // 将客户端的消息保存到历史记录
                        addToHistory(message);
                        
                        for (ClientHandler client : clients) {
                            if (client != this) {
                                try {
                                    client.sendMessage(message);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error forwarding message", e);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in client handler", e);
            } finally {
                close();
            }
        }

        public synchronized void sendMessage(Message message) {
            try {
                if (outputStream != null && !socket.isClosed()) {
                    outputStream.writeObject(message);
                    outputStream.flush();
                    outputStream.reset(); // 防止对象缓存问题
                    Log.d(TAG, "Message sent to client: " + message.getContent());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error sending message to client", e);
                close();
            }
        }
        
        /**
         * 发送历史消息给客户端
         */
        private void sendHistoryToClient() {
            List<Message> history = getMessageHistory();
            if (history.isEmpty()) {
                Log.d(TAG, "没有历史消息需要发送");
                return;
            }
            
            Log.d(TAG, "发送 " + history.size() + " 条历史消息给新用户: " + clientNickname);
            for (Message historyMsg : history) {
                // 创建历史消息副本并标记为历史消息类型
                Message historyCopy = new Message(historyMsg.getSender(), historyMsg.getContent());
                historyCopy.setTimestamp(historyMsg.getTimestamp());
                historyCopy.setHost(historyMsg.isHost());
                historyCopy.setMessageType(Message.TYPE_HISTORY);
                historyCopy.setSentByMe(false);
                
                sendMessage(historyCopy);
            }
            Log.d(TAG, "历史消息发送完成");
        }

        private boolean isClosed = false;
        
        public synchronized void close() {
            if (isClosed) {
                Log.d(TAG, "客户端已经关闭，跳过重复关闭");
                return;
            }
            
            isClosed = true;
            Log.d(TAG, "开始关闭客户端: " + clientNickname);
            
            String clientInfo = null;
            if (socket != null && !socket.isClosed()) {
                try {
                    clientInfo = socket.getInetAddress().toString();
                } catch (Exception e) {
                    Log.e(TAG, "Error getting client info", e);
                }
            }
            
            // 立即从客户端列表中移除
            clients.remove(this);
            
            // 移除昵称
            removeNickname(clientNickname);
            
            Log.d(TAG, "Client removed. Remaining clients: " + clients.size());
            
            // 强制关闭所有资源
            try {
                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing input stream", e);
            }
            
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                    outputStream = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing output stream", e);
            }
            
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    socket = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing socket", e);
            }
            
            // 在关闭后通知断开连接
            if (messageListener != null && clientInfo != null) {
                Log.d(TAG, "Notifying client disconnected: " + clientInfo);
                messageListener.onClientDisconnected(clientInfo);
            }
            
            // 客户端断开后广播最新成员列表
            if (isRunning) {
                broadcastMemberList();
            }
        }
    }
}
