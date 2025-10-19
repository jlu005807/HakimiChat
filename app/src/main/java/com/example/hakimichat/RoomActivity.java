package com.example.hakimichat;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class RoomActivity extends AppCompatActivity {
    private static final String EXTRA_IS_HOST = "is_host";
    private static final String EXTRA_SERVER_IP = "server_ip";
    private static final String EXTRA_USERNAME = "username";

    private TextView tvRoomId;
    private TextView tvConnectionStatus;
    private TextView tvUserCount;
    private RecyclerView recyclerViewMessages;
    private EditText etMessage;
    private Button btnSend;

    private MessageAdapter messageAdapter;
    private ServerManager serverManager;
    private ClientManager clientManager;
    private Handler mainHandler;

    private boolean isHost;
    private String serverIp;
    private String username;
    private int connectedUserCount = 1; // 自己算一个
    private boolean hasBeenKicked = false; // 标记是否已被踢出
    
    private android.app.AlertDialog memberListDialog;
    private java.util.List<String> memberList = new java.util.ArrayList<>();
    private MemberListAdapter memberListAdapter;
    private android.widget.ListView memberListView; // 成员列表视图
    private android.widget.TextView tvMemberCountInDialog; // 对话框中的成员数量显示

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题
        ThemeManager.getInstance(this).initTheme();
        
        super.onCreate(savedInstanceState);
        
        // 设置窗口软键盘模式，确保Android 15正确处理
        // 使用 adjustResize 确保输入框不被键盘遮挡
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        
        setContentView(R.layout.activity_room);
        
        // 为Android 11+处理窗口插入
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setupWindowInsets();
        }

        initViews();
        initData();
        setupRecyclerView();
        setupListeners();
        
        if (isHost) {
            startAsHost();
        } else {
            startAsClient();
        }
    }
    
    /**
     * 设置窗口插入处理（Android 11+）
     */
    private void setupWindowInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 监听键盘显示/隐藏
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                
                // 获取输入框布局
                android.view.View inputLayout = findViewById(R.id.inputLayout);
                if (inputLayout != null && imeInsets.bottom > 0) {
                    // 键盘显示时，调整输入框位置
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
                    params.bottomMargin = imeInsets.bottom;
                    inputLayout.setLayoutParams(params);
                    inputLayout.requestLayout();
                } else if (inputLayout != null && imeInsets.bottom == 0) {
                    // 键盘隐藏时，恢复原始位置
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
                    params.bottomMargin = 0;
                    inputLayout.setLayoutParams(params);
                    inputLayout.requestLayout();
                }
                
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    private void initViews() {
        tvRoomId = findViewById(R.id.tvRoomId);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvUserCount = findViewById(R.id.tvUserCount);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        
        updateUserCount();

        // 点击在线人数弹出成员列表
        tvUserCount.setOnClickListener(v -> showMemberListDialog());
    }

    private void initData() {
        mainHandler = new Handler(Looper.getMainLooper());
        
        isHost = getIntent().getBooleanExtra(EXTRA_IS_HOST, false);
        serverIp = getIntent().getStringExtra(EXTRA_SERVER_IP);
        username = getIntent().getStringExtra(EXTRA_USERNAME);
        
        // 不在这里设置默认昵称，让 ServerManager.registerHostNickname() 
        // 或 ClientManager.checkNickname() 来生成 "哈基米" 系列昵称
        if (TextUtils.isEmpty(username)) {
            username = "";
        }
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerViewMessages.setLayoutManager(layoutManager);
        recyclerViewMessages.setAdapter(messageAdapter);
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> sendMessage());
        
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void startAsHost() {
        String roomCode = RoomCodeUtils.encodeIpToRoomCode(serverIp);
        if (roomCode != null) {
            tvRoomId.setText("房间号: " + roomCode + " (IP: " + serverIp + ")");
        } else {
            tvRoomId.setText("房间号: " + serverIp);
        }
        tvConnectionStatus.setText("状态: 等待连接...");
        
        serverManager = new ServerManager(new ServerManager.MessageListener() {
            @Override
            public void onMessageReceived(Message message) {
                mainHandler.post(() -> {
                    // 处理不同类型的消息
                    if (message.getMessageType() == Message.TYPE_USER_COUNT) {
                        // 在线人数更新消息，不显示在聊天列表中
                        connectedUserCount = message.getUserCount();
                        updateUserCount();
                        android.util.Log.d("RoomActivity", "收到人数更新: " + connectedUserCount);
                    } else if (message.getMessageType() == Message.TYPE_MEMBER_LIST) {
                        // 成员列表消息
                        android.util.Log.d("RoomActivity", "房主收到成员列表更新消息");
                        String[] members = message.getContent().split(",");
                        updateMemberList(members);
                    } else if (message.getMessageType() == Message.TYPE_KICK) {
                        // 房主不应该收到踢人消息，如果收到说明逻辑有问题
                        String targetNickname = message.getTargetNickname();
                        android.util.Log.e("RoomActivity", "!!! 房主收到踢人消息 !!! 目标: " + targetNickname + ", 房主昵称: " + username);
                        // 检查是不是自己被踢
                        if (targetNickname != null && targetNickname.equals(username)) {
                            android.util.Log.e("RoomActivity", "!!! 房主被踢出 !!! 这不应该发生");
                        }
                    } else if (message.getMessageType() == Message.TYPE_HISTORY) {
                        // 历史消息（房主不应该收到历史消息，但为了代码健壮性还是处理一下）
                        android.util.Log.d("RoomActivity", "收到历史消息: " + message.getContent());
                        messageAdapter.addMessage(message);
                        recyclerViewMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    } else {
                        // 普通消息，添加到列表
                        messageAdapter.addMessage(message);
                        recyclerViewMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onClientConnected(String clientInfo) {
                mainHandler.post(() -> {
                    // 房主端：1(自己) + 连接的客户端数量
                    int clientCount = serverManager.getConnectedClientCount();
                    connectedUserCount = 1 + clientCount;
                    android.util.Log.d("RoomActivity", "客户端连接: " + clientInfo + ", 客户端数: " + clientCount + ", 总人数: " + connectedUserCount);
                    updateUserCount();
                    tvConnectionStatus.setText("状态: 已连接 (房主)");
                    showToast("新用户加入");
                    
                    // 广播在线人数给所有客户端
                    broadcastUserCount();
                });
            }

            @Override
            public void onClientDisconnected(String clientInfo) {
                mainHandler.post(() -> {
                    // 房主端：1(自己) + 连接的客户端数量
                    int clientCount = serverManager.getConnectedClientCount();
                    connectedUserCount = 1 + clientCount;
                    android.util.Log.d("RoomActivity", "客户端断开: " + clientInfo + ", 客户端数: " + clientCount + ", 总人数: " + connectedUserCount);
                    updateUserCount();
                    showToast("用户离开");
                    
                    // 广播在线人数给所有客户端
                    broadcastUserCount();
                });
            }
        });
        
        serverManager.startServer();
        
        // 注册房主昵称
        String validatedUsername = serverManager.registerHostNickname(username);
        if (!validatedUsername.equals(username)) {
            username = validatedUsername;
            showToast("你的昵称: " + username);
        }
        
        connectedUserCount = 1; // 房主自己
        updateUserCount();
        tvConnectionStatus.setText("状态: 在线 (房主)");
        
        // 初始化房主的成员列表
        memberList.clear();
        memberList.add(username);
    }

    private void startAsClient() {
        String roomCode = RoomCodeUtils.encodeIpToRoomCode(serverIp);
        if (roomCode != null) {
            tvRoomId.setText("房间号: " + roomCode);
        } else {
            tvRoomId.setText("连接到: " + serverIp);
        }
        tvConnectionStatus.setText("状态: 连接中...");
        
        clientManager = new ClientManager(new ClientManager.MessageListener() {
            @Override
            public void onMessageReceived(Message message) {
                mainHandler.post(() -> {
                    // 如果已被踢出，忽略所有消息
                    if (hasBeenKicked) {
                        android.util.Log.w("RoomActivity", "已被踢出，忽略消息");
                        return;
                    }
                    
                    // 处理不同类型的消息
                    if (message.getMessageType() == Message.TYPE_USER_COUNT) {
                        // 在线人数更新消息，不显示在聊天列表中
                        connectedUserCount = message.getUserCount();
                        updateUserCount();
                        android.util.Log.d("RoomActivity", "客户端收到人数更新: " + connectedUserCount);
                    } else if (message.getMessageType() == Message.TYPE_MEMBER_LIST) {
                        android.util.Log.d("RoomActivity", "客户端收到成员列表更新消息");
                        String[] members = message.getContent().split(",");
                        updateMemberList(members);
                    } else if (message.getMessageType() == Message.TYPE_KICK) {
                        // 被踢出通知：只做前端提示与禁用，等待断开回调统一收尾
                        String targetNickname = message.getTargetNickname();
                        android.util.Log.d("RoomActivity", "客户端收到踢人消息, 目标: " + targetNickname + ", 我的昵称: " + username);
                        if (targetNickname != null && targetNickname.equals(username)) {
                            hasBeenKicked = true;
                            showToast("你已被房主踢出房间");
                            etMessage.setEnabled(false);
                            btnSend.setEnabled(false);
                            etMessage.setText("");
                        }
                    } else if (message.getMessageType() == Message.TYPE_HISTORY) {
                        // 历史消息，添加到列表
                        android.util.Log.d("RoomActivity", "收到历史消息: " + message.getContent());
                        messageAdapter.addMessage(message);
                        recyclerViewMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    } else {
                        // 普通消息，添加到列表
                        messageAdapter.addMessage(message);
                        recyclerViewMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onConnected() {
                mainHandler.post(() -> {
                    connectedUserCount = 2; // 初始假设房主+自己，等待房主广播实际人数
                    updateUserCount();
                    tvConnectionStatus.setText("状态: 已连接");
                    showToast("已连接到房间");
                    
                    // 发送昵称检查请求
                    if (clientManager != null) {
                        clientManager.checkNickname(username);
                        android.util.Log.d("RoomActivity", "发送昵称检查: " + username);
                    }
                });
            }

            @Override
            public void onNicknameValidated(String validatedNickname) {
                mainHandler.post(() -> {
                    // 更新本地昵称为验证后的昵称
                    username = validatedNickname;
                    android.util.Log.d("RoomActivity", "昵称验证完成: " + validatedNickname);
                    showToast("你的昵称: " + validatedNickname);
                });
            }

            @Override
            public void onDisconnected() {
                mainHandler.post(() -> {
                    if (hasBeenKicked) {
                        android.util.Log.d("RoomActivity", "被踢出后收到断开回调，关闭房间页面");
                        finish();
                        return;
                    }
                    tvConnectionStatus.setText("状态: 已断开");
                    showToast("与房间断开连接");
                });
            }

            @Override
            public void onConnectionError(String error) {
                mainHandler.post(() -> {
                    // 如果已被踢出，不显示错误提示
                    if (hasBeenKicked) {
                        android.util.Log.d("RoomActivity", "已被踢出，忽略连接错误回调");
                        return;
                    }
                    tvConnectionStatus.setText("状态: 连接失败");
                    showToast(error);
                });
            }
        });
        
        clientManager.connect(serverIp);
    }

    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        
        if (TextUtils.isEmpty(content)) {
            showToast("请输入消息");
            return;
        }
        
        // 检查消息长度
        if (content.length() > AppConstants.MAX_MESSAGE_LENGTH) {
            showToast("消息不能超过" + AppConstants.MAX_MESSAGE_LENGTH + "个字");
            return;
        }

        // 检查是否已被踢出
        if (hasBeenKicked) {
            showToast("你已被踢出房间，无法发送消息");
            return;
        }

        // 检查连接状态
        if (isHost) {
            if (serverManager == null) {
                showToast("服务器未启动");
                return;
            }
        } else {
            if (clientManager == null || !clientManager.isConnected()) {
                showToast("未连接到服务器");
                return;
            }
        }

        // 创建本地显示的消息（isSentByMe = true）
        Message localMessage = new Message(username, content);
        localMessage.setSentByMe(true);
        localMessage.setHost(isHost); // 设置是否为房主
        messageAdapter.addMessage(localMessage);
        recyclerViewMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        
        // 创建发送给其他客户端的消息（isSentByMe = false）
        Message networkMessage = new Message(username, content);
        networkMessage.setSentByMe(false);
        networkMessage.setHost(isHost); // 设置是否为房主
        if (isHost) {
            serverManager.broadcastMessage(networkMessage);
        } else {
            clientManager.sendMessage(networkMessage);
        }
        
        etMessage.setText("");
    }

    private void updateUserCount() {
        if (tvUserCount != null) {
            tvUserCount.setText("在线人数: " + connectedUserCount);
        }
    }
    
    /**
     * 房主端广播在线人数给所有客户端
     */
    private void broadcastUserCount() {
        if (isHost && serverManager != null) {
            Message userCountMessage = Message.createUserCountMessage(connectedUserCount);
            serverManager.broadcastMessage(userCountMessage);
            android.util.Log.d("RoomActivity", "广播在线人数: " + connectedUserCount);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    // 更新成员列表
    private void updateMemberList(String[] members) {
        memberList.clear();
        if (members != null) {
            for (String m : members) {
                if (!TextUtils.isEmpty(m)) memberList.add(m);
            }
        }
        android.util.Log.d("RoomActivity", "更新成员列表: " + memberList.toString() + ", 适配器存在: " + (memberListAdapter != null) + ", 对话框显示: " + (memberListDialog != null && memberListDialog.isShowing()));
        
        // 直接更新UI（因为调用者已经在主线程中）
        if (memberListAdapter != null) {
            android.util.Log.d("RoomActivity", "调用 notifyDataSetChanged()");
            memberListAdapter.notifyDataSetChanged();
            
            // 强制刷新 ListView
            if (memberListView != null) {
                android.util.Log.d("RoomActivity", "调用 ListView.invalidateViews()");
                memberListView.invalidateViews();
            }
        }
        
        // 更新对话框中的成员数量显示
        if (tvMemberCountInDialog != null) {
            tvMemberCountInDialog.setText("(" + memberList.size() + ")");
            android.util.Log.d("RoomActivity", "对话框成员数量已更新: " + memberList.size());
        }
    }

    // 弹出成员列表对话框
    private void showMemberListDialog() {
        if (memberListDialog != null && memberListDialog.isShowing()) {
            memberListDialog.dismiss();
            return;
        }
        
        android.util.Log.d("RoomActivity", "打开成员列表对话框，当前成员: " + memberList.toString());
        
        // 如果是房主，获取最新成员列表
        if (isHost && serverManager != null) {
            java.util.List<String> members = serverManager.getMemberList();
            memberList.clear();
            memberList.addAll(members);
            android.util.Log.d("RoomActivity", "房主刷新成员列表: " + memberList.toString());
        }
        // 如果是客户端，请求最新成员列表
        else if (!isHost && clientManager != null && clientManager.isConnected()) {
            android.util.Log.d("RoomActivity", "客户端请求最新成员列表");
            // 发送请求成员列表的消息
            Message requestMsg = new Message("", "");
            requestMsg.setMessageType(Message.TYPE_MEMBER_LIST);
            clientManager.sendMessage(requestMsg);
        }
        
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_member_list, null);
        memberListView = dialogView.findViewById(R.id.listViewMembers);
        android.widget.Button btnClose = dialogView.findViewById(R.id.btnClose);
        tvMemberCountInDialog = dialogView.findViewById(R.id.tvMemberCount);

        // 更新成员数量显示
        tvMemberCountInDialog.setText("(" + memberList.size() + ")");

        // 使用自定义适配器
        memberListAdapter = new MemberListAdapter();
        memberListView.setAdapter(memberListAdapter);
        
        android.util.Log.d("RoomActivity", "成员列表对话框已创建，适配器已设置");
        
        btnClose.setOnClickListener(v -> {
            if (memberListDialog != null) {
                memberListDialog.dismiss();
            }
        });
        
        memberListDialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        memberListDialog.setOnDismissListener(dialog -> {
            android.util.Log.d("RoomActivity", "成员列表对话框已关闭，清理引用");
            memberListDialog = null;
            memberListAdapter = null;
            memberListView = null;
            tvMemberCountInDialog = null;
        });
        
        memberListDialog.show();
    }

    // 房主踢人
    private void kickMember(String nickname) {
        if (isHost && serverManager != null && !TextUtils.isEmpty(nickname)) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("踢出成员")
                .setMessage("确定要踢出 \"" + nickname + "\" 吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    android.util.Log.d("RoomActivity", "房主开始踢人: " + nickname + ", 房主昵称: " + username);
                    // 只调用ServerManager的kickMember方法，它会直接发送给被踢的客户端
                    serverManager.kickMember(nickname);
                    showToast("已踢出: " + nickname);
                    
                    // 立即更新本地成员列表
                    if (memberList.contains(nickname)) {
                        memberList.remove(nickname);
                    }
                    
                    // 如果成员列表对话框正在显示，刷新列表和成员数量
                    if (memberListAdapter != null) {
                        memberListAdapter.notifyDataSetChanged();
                    }
                    
                    // 更新对话框中的成员数量显示
                    if (tvMemberCountInDialog != null) {
                        tvMemberCountInDialog.setText("(" + memberList.size() + ")");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        }
    }
    
    // 成员列表适配器
    private class MemberListAdapter extends android.widget.BaseAdapter {
        @Override
        public int getCount() {
            int count = memberList.size();
            android.util.Log.d("MemberListAdapter", "getCount() called, count=" + count);
            return count;
        }

        @Override
        public String getItem(int position) {
            return memberList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_member, parent, false);
                holder = new ViewHolder();
                holder.tvAvatar = convertView.findViewById(R.id.tvAvatar);
                holder.tvMemberName = convertView.findViewById(R.id.tvMemberName);
                holder.tvHostBadge = convertView.findViewById(R.id.tvHostBadge);
                holder.tvMeBadge = convertView.findViewById(R.id.tvMeBadge);
                holder.btnKick = convertView.findViewById(R.id.btnKick);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String memberName = getItem(position);
            holder.tvMemberName.setText(memberName);

            // 设置头像（显示昵称的第一个字符）
            if (memberName != null && !memberName.isEmpty()) {
                holder.tvAvatar.setText(memberName.substring(0, 1));
            } else {
                holder.tvAvatar.setText("?");
            }

            // 第一个成员是房主，显示房主标识
            if (position == 0) {
                holder.tvHostBadge.setVisibility(android.view.View.VISIBLE);
            } else {
                holder.tvHostBadge.setVisibility(android.view.View.GONE);
            }

            // 如果是当前用户自己，显示"我"的标识
            if (memberName.equals(username)) {
                holder.tvMeBadge.setVisibility(android.view.View.VISIBLE);
            } else {
                holder.tvMeBadge.setVisibility(android.view.View.GONE);
            }

            // 如果当前用户是房主且不是自己，显示踢出按钮
            if (isHost && !memberName.equals(username)) {
                holder.btnKick.setVisibility(android.view.View.VISIBLE);
                holder.btnKick.setOnClickListener(v -> kickMember(memberName));
            } else {
                holder.btnKick.setVisibility(android.view.View.GONE);
                holder.btnKick.setOnClickListener(null);
            }

            return convertView;
        }

        class ViewHolder {
            android.widget.TextView tvAvatar;
            android.widget.TextView tvMemberName;
            android.widget.TextView tvHostBadge;
            android.widget.TextView tvMeBadge;
            android.widget.Button btnKick;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        android.util.Log.d("RoomActivity", "onDestroy 被调用");
        
        if (serverManager != null) {
            try {
                serverManager.stopServer();
            } catch (Exception e) {
                android.util.Log.e("RoomActivity", "停止服务器时出错", e);
            }
        }
        
        // 如果是被踢出，ClientManager 已经断开了，不需要再次调用
        if (clientManager != null && !hasBeenKicked) {
            try {
                clientManager.disconnect();
            } catch (Exception e) {
                android.util.Log.e("RoomActivity", "断开客户端时出错", e);
            }
        }
    }

    public static String getExtraIsHost() {
        return EXTRA_IS_HOST;
    }

    public static String getExtraServerIp() {
        return EXTRA_SERVER_IP;
    }

    public static String getExtraUsername() {
        return EXTRA_USERNAME;
    }
}
