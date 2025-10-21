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
    private android.widget.ImageButton btnGame;  // 游戏按钮
    private android.view.View gamePanel;  // 游戏面板
    private RecyclerView recyclerViewGamePanel;  // 游戏面板的RecyclerView
    private boolean isKeyboardShowing = false;  // 键盘是否正在显示
    private boolean isGamePanelShowing = false;  // 游戏面板是否正在显示
    private boolean isAnimating = false;  // 是否正在执行动画
    private android.view.View rootLayout;  // 根布局
    private android.view.View inputLayout;  // 输入框布局（缓存引用）
    private android.view.ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;  // 全局布局监听器（备用方案）
    private int lastRootHeight = 0;  // 上次根布局高度
    private boolean isProcessingKeyboardChange = false;  // 防止重复处理键盘状态变化
    private int currentKeyboardHeight = 0;  // 当前键盘高度
    private android.animation.ValueAnimator inputLayoutAnimator;  // 输入框动画器，用于中断
    private android.animation.ValueAnimator gamePanelInputAnimator;  // 游戏面板时输入框动画器，用于中断

    private MessageAdapter messageAdapter;
    private ServerManager serverManager;
    private ClientManager clientManager;
    private Handler mainHandler;
    private com.example.hakimichat.game.GameManager gameManager;  // 游戏管理器

    private boolean isHost;
    private String serverIp;
    private String username;
    private int connectedUserCount = 1; // 自己算一个
    private boolean hasBeenKicked = false; // 标记是否已被踢出
    
    // 游戏邀请信息缓存
    private java.util.Map<String, String> gameTypeCache = new java.util.HashMap<>();  // gameId -> gameType
    
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
        
        // 设置传统的键盘检测（适配所有Android版本）
        setupKeyboardDetection();

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
     * 设置传统的键盘检测方法（兼容所有设备）
     * 通过监听根布局高度变化来检测键盘显示/隐藏
     */
    private void setupKeyboardDetection() {
        globalLayoutListener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (rootLayout == null || inputLayout == null) {
                    return;
                }
                
                android.graphics.Rect rect = new android.graphics.Rect();
                rootLayout.getWindowVisibleDisplayFrame(rect);
                int currentRootHeight = rect.height();
                
                // 首次初始化
                if (lastRootHeight == 0) {
                    lastRootHeight = currentRootHeight;
                    return;
                }
                
                int heightDiff = lastRootHeight - currentRootHeight;
                
                // 高度差异超过屏幕20%，认为是键盘显示/隐藏
                int screenHeight = rootLayout.getRootView().getHeight();
                int threshold = screenHeight / 5;  // 20%
                
                if (heightDiff > threshold) {
                    // 键盘显示
                    android.util.Log.d("RoomActivity", "传统方法检测到键盘显示，高度差: " + heightDiff);
                    onKeyboardShown(heightDiff);
                } else if (heightDiff < -threshold) {
                    // 键盘隐藏
                    android.util.Log.d("RoomActivity", "传统方法检测到键盘隐藏，高度差: " + heightDiff);
                    onKeyboardHidden();
                }
                
                lastRootHeight = currentRootHeight;
            }
        };
        
        // 延迟添加监听器，确保在 initViews 之后
        new android.os.Handler(Looper.getMainLooper()).post(() -> {
            if (rootLayout != null) {
                rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
                android.util.Log.d("RoomActivity", "传统键盘检测已启用");
            }
        });
    }
    
    /**
     * 键盘显示回调（统一处理）
     */
    private void onKeyboardShown(int keyboardHeight) {
        // 取消正在进行的输入框动画
        if (inputLayoutAnimator != null && inputLayoutAnimator.isRunning()) {
            android.util.Log.d("RoomActivity", "取消正在进行的输入框动画");
            inputLayoutAnimator.cancel();
            inputLayoutAnimator = null;
        }
        
        // 取消正在进行的游戏面板输入框动画
        if (gamePanelInputAnimator != null && gamePanelInputAnimator.isRunning()) {
            android.util.Log.d("RoomActivity", "取消正在进行的游戏面板输入框动画");
            gamePanelInputAnimator.cancel();
            gamePanelInputAnimator = null;
        }
        
        // 防止重复处理
        if (isProcessingKeyboardChange) {
            android.util.Log.d("RoomActivity", "正在处理键盘变化，跳过重复调用");
            return;
        }
        
        // 防止相同高度的重复调用
        if (isKeyboardShowing && Math.abs(currentKeyboardHeight - keyboardHeight) < 10) {
            android.util.Log.d("RoomActivity", "键盘高度未变化，跳过: " + keyboardHeight);
            return;
        }
        
        isProcessingKeyboardChange = true;
        boolean wasKeyboardShowing = isKeyboardShowing;
        isKeyboardShowing = true;
        currentKeyboardHeight = keyboardHeight;
        
        android.util.Log.d("RoomActivity", "键盘显示: " + keyboardHeight + ", 之前状态: " + wasKeyboardShowing);
        
        // 键盘显示时，无论状态如何都要确保游戏面板已隐藏（防止白框）
        if (gamePanel != null) {
            // 检查游戏面板是否可见或高度不为0
            boolean needHide = gamePanel.getVisibility() != android.view.View.GONE || 
                              gamePanel.getLayoutParams().height != 0;
            
            if (needHide || isGamePanelShowing) {
                android.util.Log.d("RoomActivity", "键盘显示，强制隐藏游戏面板 (可见性=" + gamePanel.getVisibility() + 
                                 ", 高度=" + gamePanel.getLayoutParams().height + ", isGamePanelShowing=" + isGamePanelShowing + ")");
                isGamePanelShowing = false;
                isAnimating = false;
                gamePanel.clearAnimation();
                
                // 先设置高度为0，再设置GONE，并强制刷新布局
                ViewGroup.LayoutParams panelParams = gamePanel.getLayoutParams();
                if (panelParams != null) {
                    panelParams.height = 0;
                    gamePanel.setLayoutParams(panelParams);
                }
                gamePanel.requestLayout();
                gamePanel.setVisibility(android.view.View.GONE);
            }
        }
        
        // 调整输入框位置
        if (inputLayout != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
            int currentMargin = params.bottomMargin;
            
            android.util.Log.d("RoomActivity", "当前输入框margin: " + currentMargin + ", 目标: " + keyboardHeight);
            
            // 如果是首次显示或高度差异较大，使用动画
            if (!wasKeyboardShowing || Math.abs(currentMargin - keyboardHeight) > 50) {
                inputLayoutAnimator = android.animation.ValueAnimator.ofInt(currentMargin, keyboardHeight);
                inputLayoutAnimator.setDuration(250);
                inputLayoutAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
                inputLayoutAnimator.addUpdateListener(animation -> {
                    int animatedValue = (int) animation.getAnimatedValue();
                    if (inputLayout != null) {
                        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
                        p.bottomMargin = animatedValue;
                        inputLayout.setLayoutParams(p);
                    }
                });
                inputLayoutAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        isProcessingKeyboardChange = false;
                        inputLayoutAnimator = null;
                        android.util.Log.d("RoomActivity", "键盘动画结束，输入框margin: " + keyboardHeight);
                    }
                    
                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        isProcessingKeyboardChange = false;
                        inputLayoutAnimator = null;
                        android.util.Log.d("RoomActivity", "键盘动画被取消");
                    }
                });
                inputLayoutAnimator.start();
                android.util.Log.d("RoomActivity", "输入框从 " + currentMargin + " 动画到 " + keyboardHeight);
            } else {
                // 直接设置
                params.bottomMargin = keyboardHeight;
                inputLayout.setLayoutParams(params);
                inputLayout.requestLayout();
                isProcessingKeyboardChange = false;
                android.util.Log.d("RoomActivity", "输入框直接设置: " + keyboardHeight);
            }
        } else {
            isProcessingKeyboardChange = false;
        }
    }
    
    /**
     * 键盘隐藏回调（统一处理）
     */
    private void onKeyboardHidden() {
        // 取消正在进行的输入框动画
        if (inputLayoutAnimator != null && inputLayoutAnimator.isRunning()) {
            android.util.Log.d("RoomActivity", "取消正在进行的输入框动画");
            inputLayoutAnimator.cancel();
            inputLayoutAnimator = null;
        }
        
        // 取消正在进行的游戏面板输入框动画
        if (gamePanelInputAnimator != null && gamePanelInputAnimator.isRunning()) {
            android.util.Log.d("RoomActivity", "取消正在进行的游戏面板输入框动画");
            gamePanelInputAnimator.cancel();
            gamePanelInputAnimator = null;
        }
        
        // 防止重复处理
        if (isProcessingKeyboardChange) {
            android.util.Log.d("RoomActivity", "正在处理键盘变化，跳过重复调用");
            return;
        }
        
        // 如果键盘本来就是隐藏的，跳过
        if (!isKeyboardShowing) {
            android.util.Log.d("RoomActivity", "键盘本来就是隐藏状态，跳过");
            return;
        }
        
        isProcessingKeyboardChange = true;
        boolean wasKeyboardShowing = isKeyboardShowing;
        isKeyboardShowing = false;
        // 不要清零 currentKeyboardHeight，保留记录供游戏面板使用
        
        android.util.Log.d("RoomActivity", "键盘隐藏，保留记录的键盘高度: " + currentKeyboardHeight);
        
        if (wasKeyboardShowing && inputLayout != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
            int currentMargin = params.bottomMargin;
            
            android.util.Log.d("RoomActivity", "当前输入框margin: " + currentMargin + ", 游戏面板显示: " + isGamePanelShowing);
            
            // 如果游戏面板正在显示，保持输入框位置不变
            if (isGamePanelShowing) {
                android.util.Log.d("RoomActivity", "键盘隐藏，游戏面板显示中，保持输入框位置");
                isProcessingKeyboardChange = false;
                return;
            }
            
            // 恢复输入框位置到底部
            if (!isAnimating && currentMargin > 0) {
                inputLayoutAnimator = android.animation.ValueAnimator.ofInt(currentMargin, 0);
                inputLayoutAnimator.setDuration(200);
                inputLayoutAnimator.setInterpolator(new android.view.animation.AccelerateInterpolator());
                inputLayoutAnimator.addUpdateListener(animation -> {
                    int animatedValue = (int) animation.getAnimatedValue();
                    if (inputLayout != null) {
                        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
                        p.bottomMargin = animatedValue;
                        inputLayout.setLayoutParams(p);
                    }
                });
                inputLayoutAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        isProcessingKeyboardChange = false;
                        inputLayoutAnimator = null;
                        android.util.Log.d("RoomActivity", "键盘隐藏动画结束，输入框margin: 0");
                    }
                    
                    @Override
                    public void onAnimationCancel(android.animation.Animator animation) {
                        isProcessingKeyboardChange = false;
                        inputLayoutAnimator = null;
                        android.util.Log.d("RoomActivity", "键盘隐藏动画被取消");
                    }
                });
                inputLayoutAnimator.start();
                android.util.Log.d("RoomActivity", "输入框从 " + currentMargin + " 动画到 0");
            } else {
                isProcessingKeyboardChange = false;
            }
        } else {
            isProcessingKeyboardChange = false;
        }
    }

    private void initViews() {
        rootLayout = findViewById(android.R.id.content);  // 获取根布局
        inputLayout = findViewById(R.id.inputLayout);  // 缓存输入框布局引用
        tvRoomId = findViewById(R.id.tvRoomId);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvUserCount = findViewById(R.id.tvUserCount);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnGame = findViewById(R.id.btnGame);  // 初始化游戏按钮
        gamePanel = findViewById(R.id.gamePanel);  // 初始化游戏面板
        recyclerViewGamePanel = gamePanel.findViewById(R.id.recyclerViewGamePanel);  // 初始化游戏列表
        
        // 确保游戏面板初始状态正确（防止白框问题）
        ViewGroup.LayoutParams initialParams = gamePanel.getLayoutParams();
        if (initialParams != null) {
            initialParams.height = 0;
            gamePanel.setLayoutParams(initialParams);
        }
        gamePanel.setVisibility(android.view.View.GONE);
        android.util.Log.d("RoomActivity", "游戏面板初始化为GONE，高度为0");
        
        // 设置游戏面板的RecyclerView
        recyclerViewGamePanel.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        com.example.hakimichat.game.GameListAdapter gamePanelAdapter = 
            new com.example.hakimichat.game.GameListAdapter(gameInfo -> {
                // 点击游戏后，隐藏面板并显示玩家选择对话框
                hideGamePanel();
                showPlayerSelectionDialog(gameInfo);
            });
        recyclerViewGamePanel.setAdapter(gamePanelAdapter);
        
        updateUserCount();

        // 点击在线人数弹出成员列表
        tvUserCount.setOnClickListener(v -> showMemberListDialog());
        
        // 点击游戏按钮切换游戏面板显示
        btnGame.setOnClickListener(v -> toggleGamePanel());
        
        // 点击消息列表区域，隐藏键盘和游戏面板
        recyclerViewMessages.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                // 隐藏键盘和游戏面板
                hideKeyboard();
                hideGamePanel();
            }
            return false;  // 返回 false 让 RecyclerView 继续处理触摸事件（滚动等）
        });
        
        // 点击根布局空白区域，隐藏键盘和游戏面板
        rootLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                // 检查点击位置是否在输入框布局或游戏面板外
                if (!isTouchInsideView(event, gamePanel) &&
                    !isTouchInsideView(event, findViewById(R.id.inputLayout))) {
                    // 隐藏键盘和游戏面板
                    hideKeyboard();
                    hideGamePanel();
                }
            }
            return false;
        });
    }
    
    /**
     * 判断触摸点是否在视图内
     */
    private boolean isTouchInsideView(android.view.MotionEvent event, android.view.View view) {
        if (view == null || view.getVisibility() != android.view.View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        return event.getRawX() >= x && event.getRawX() <= (x + view.getWidth()) &&
               event.getRawY() >= y && event.getRawY() <= (y + view.getHeight());
    }
    
    /**
     * 隐藏键盘
     */
    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
        etMessage.clearFocus();
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
        
        // 初始化GameManager
        gameManager = com.example.hakimichat.game.GameManager.getInstance();
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter();
        
        // 设置游戏操作监听器
        messageAdapter.setGameActionListener(new MessageAdapter.OnGameActionListener() {
            @Override
            public void onJoinGame(String gameId, String gameType) {
                handleJoinGameFromMessage(gameId, gameType);
            }

            @Override
            public void onSpectateGame(String gameId, String gameType) {
                handleSpectateGameFromMessage(gameId, gameType);
            }
        });
        
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
        
        // 监听输入框文本变化，切换发送按钮和游戏按钮的显示
        etMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateButtonVisibility();
            }
        });
        
        // 监听输入框焦点变化（仅用于日志，实际隐藏由 WindowInsets 回调处理）
        etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                android.util.Log.d("RoomActivity", "输入框获得焦点，等待 WindowInsets 回调处理游戏面板");
            }
        });
        
        // 初始化按钮显示状态
        updateButtonVisibility();
    }
    
    /**
     * 根据输入框内容更新按钮显示状态
     * 有文字时显示发送按钮，无文字时显示游戏按钮
     */
    private void updateButtonVisibility() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            // 无文字：显示游戏按钮，隐藏发送按钮
            btnSend.setVisibility(android.view.View.GONE);
            btnGame.setVisibility(android.view.View.VISIBLE);
        } else {
            // 有文字：显示发送按钮，隐藏游戏按钮
            btnSend.setVisibility(android.view.View.VISIBLE);
            btnGame.setVisibility(android.view.View.GONE);
        }
    }
    
    /**
     * 切换游戏面板显示/隐藏
     */
    private void toggleGamePanel() {
        if (gamePanel.getVisibility() == android.view.View.VISIBLE) {
            hideGamePanel();
        } else {
            showGamePanel();
        }
    }
    
    /**
     * 显示游戏面板（带动画）
     */
    private void showGamePanel() {
        if (isGamePanelShowing) {
            return;  // 已经显示，直接返回
        }
        
        // 取消正在进行的游戏面板输入框动画
        if (gamePanelInputAnimator != null && gamePanelInputAnimator.isRunning()) {
            android.util.Log.d("RoomActivity", "取消正在进行的游戏面板输入框动画");
            gamePanelInputAnimator.cancel();
            gamePanelInputAnimator = null;
        }
        
        // 取消游戏面板自身的动画
        if (gamePanel != null) {
            gamePanel.clearAnimation();
        }
        
        // 标记状态
        isAnimating = false;  // 重置动画标志
        isGamePanelShowing = true;
        
        // 隐藏键盘
        hideKeyboard();
        
        // 获取当前输入框的 margin
        ViewGroup.MarginLayoutParams currentParams = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
        int currentMargin = currentParams.bottomMargin;
        
        // 计算面板高度：优先使用记录的键盘高度，实现自动适应
        int panelHeight;
        if (currentKeyboardHeight > 0) {
            // 优先使用记录的键盘高度（最准确）
            panelHeight = currentKeyboardHeight;
            android.util.Log.d("RoomActivity", "使用记录的键盘高度作为面板高度: " + panelHeight);
        } else if (isKeyboardShowing && currentMargin > 0) {
            // 如果键盘正在显示，使用当前的 margin 作为面板高度
            panelHeight = currentMargin;
            android.util.Log.d("RoomActivity", "使用当前margin作为面板高度: " + panelHeight);
        } else if (currentMargin > 0) {
            // 如果有历史 margin（之前显示过键盘或面板），使用该值
            panelHeight = currentMargin;
            android.util.Log.d("RoomActivity", "使用历史margin作为面板高度: " + panelHeight);
        } else {
            // 没有参考值，使用屏幕高度的 40%
            android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int screenHeight = displayMetrics.heightPixels;
            panelHeight = (int) (screenHeight * 0.4);
            android.util.Log.d("RoomActivity", "使用默认高度(屏幕40%): " + panelHeight);
        }
        
        // 设置游戏面板高度
        ViewGroup.LayoutParams panelParams = gamePanel.getLayoutParams();
        panelParams.height = panelHeight;
        gamePanel.setLayoutParams(panelParams);
        
        // 判断是否从键盘切换而来：使用状态标志而非高度比较
        boolean isReplacingKeyboard = isKeyboardShowing;
        
        if (isReplacingKeyboard) {
            // 键盘→游戏面板：直接显示，不要动画，保持输入框位置不动
            gamePanel.setVisibility(android.view.View.VISIBLE);
            // 不修改 bottomMargin，保持输入框位置不变
            android.util.Log.d("RoomActivity", "键盘→游戏面板切换，保持输入框位置: " + currentMargin);
        } else {
            // 无键盘→游戏面板：使用动画
            isAnimating = true;
            gamePanel.setVisibility(android.view.View.VISIBLE);
            
            // 启动游戏面板的滑入动画
            android.view.animation.Animation slideUp = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_up_from_bottom);
            slideUp.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
                @Override
                public void onAnimationStart(android.view.animation.Animation animation) {
                }
                
                @Override
                public void onAnimationEnd(android.view.animation.Animation animation) {
                    isAnimating = false;
                }
                
                @Override
                public void onAnimationRepeat(android.view.animation.Animation animation) {
                }
            });
            
            // 使用 ValueAnimator 同步动画输入框的上移
            final int finalPanelHeight = panelHeight;
            gamePanelInputAnimator = android.animation.ValueAnimator.ofInt(currentMargin, finalPanelHeight);
            gamePanelInputAnimator.setDuration(250);  // 与动画时间一致
            gamePanelInputAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            gamePanelInputAnimator.addUpdateListener(animation -> {
                int animatedValue = (int) animation.getAnimatedValue();
                if (inputLayout != null) {
                    ViewGroup.MarginLayoutParams inputParams = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
                    inputParams.bottomMargin = animatedValue;
                    inputLayout.setLayoutParams(inputParams);
                }
            });
            gamePanelInputAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    isAnimating = false;
                    gamePanelInputAnimator = null;
                    android.util.Log.d("RoomActivity", "游戏面板显示动画结束");
                }
                
                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    isAnimating = false;
                    gamePanelInputAnimator = null;
                    android.util.Log.d("RoomActivity", "游戏面板显示动画被取消");
                }
            });
            
            // 同时启动两个动画
            gamePanel.startAnimation(slideUp);
            gamePanelInputAnimator.start();
        }
    }
    
    /**
     * 隐藏游戏面板（带动画）
     */
    private void hideGamePanel() {
        if (!isGamePanelShowing) {
            return;  // 如果面板本来就是隐藏的，直接返回
        }
        
        // 取消正在进行的游戏面板输入框动画
        if (gamePanelInputAnimator != null && gamePanelInputAnimator.isRunning()) {
            android.util.Log.d("RoomActivity", "取消正在进行的游戏面板输入框动画");
            gamePanelInputAnimator.cancel();
            gamePanelInputAnimator = null;
        }
        
        // 取消游戏面板自身的动画
        if (gamePanel != null) {
            gamePanel.clearAnimation();
        }
        
        isAnimating = true;
        
        // 获取当前的 bottomMargin
        ViewGroup.MarginLayoutParams inputParams = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
        final int currentMargin = inputParams.bottomMargin;
        
        // 启动游戏面板的滑出动画
        android.view.animation.Animation slideDown = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_down_to_bottom);
        slideDown.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationStart(android.view.animation.Animation animation) {
            }
            
            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                if (gamePanel != null) {
                    // 关键：先设置高度为0，再设置GONE，并强制刷新布局
                    ViewGroup.LayoutParams panelParams = gamePanel.getLayoutParams();
                    panelParams.height = 0;
                    gamePanel.setLayoutParams(panelParams);
                    
                    // 强制立即应用布局更改
                    gamePanel.requestLayout();
                    
                    // 然后设置为GONE
                    gamePanel.setVisibility(android.view.View.GONE);
                    
                    android.util.Log.d("RoomActivity", "动画结束隐藏：设置高度0，强制刷新，设置GONE");
                }
                isGamePanelShowing = false;
                isAnimating = false;
            }
            
            @Override
            public void onAnimationRepeat(android.view.animation.Animation animation) {
            }
        });
        
        // 启动游戏面板动画
        gamePanel.startAnimation(slideDown);
        
        // 没有键盘：动画输入框下移
        gamePanelInputAnimator = android.animation.ValueAnimator.ofInt(currentMargin, 0);
        gamePanelInputAnimator.setDuration(200);  // 与动画时间一致
        gamePanelInputAnimator.setInterpolator(new android.view.animation.AccelerateInterpolator());
        gamePanelInputAnimator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            if (inputLayout != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) inputLayout.getLayoutParams();
                params.bottomMargin = animatedValue;
                inputLayout.setLayoutParams(params);
            }
        });
        gamePanelInputAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                gamePanelInputAnimator = null;
                android.util.Log.d("RoomActivity", "游戏面板隐藏输入框动画结束");
            }
            
            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                gamePanelInputAnimator = null;
                android.util.Log.d("RoomActivity", "游戏面板隐藏输入框动画被取消");
            }
        });
        gamePanelInputAnimator.start();
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
                    } else if (message.getMessageType() == Message.TYPE_GAME_INVITE) {
                        // 游戏邀请消息
                        handleGameInvite(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_JOIN) {
                        // 加入游戏消息
                        gameManager.handleGameJoin(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_MOVE) {
                        // 游戏移动消息
                        gameManager.handleGameMove(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_STATE) {
                        // 游戏状态同步消息
                        gameManager.handleGameState(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_END) {
                        // 游戏结束消息
                        gameManager.handleGameEnd(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_QUIT) {
                        // 退出游戏消息
                        gameManager.handleGameQuit(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_RESTART) {
                        // 再来一局消息
                        gameManager.handleGameRestart(message);
                        // 在聊天区显示提示
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
        
        // 初始化GameManager
        gameManager.init(username, true, serverManager, null);
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
                    } else if (message.getMessageType() == Message.TYPE_GAME_INVITE) {
                        // 游戏邀请消息
                        handleGameInvite(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_JOIN) {
                        // 加入游戏消息
                        gameManager.handleGameJoin(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_MOVE) {
                        // 游戏移动消息
                        gameManager.handleGameMove(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_STATE) {
                        // 游戏状态同步消息
                        gameManager.handleGameState(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_END) {
                        // 游戏结束消息
                        gameManager.handleGameEnd(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_QUIT) {
                        // 玩家退出游戏
                        gameManager.handleGameQuit(message);
                    } else if (message.getMessageType() == Message.TYPE_GAME_RESTART) {
                        // 重新开始游戏
                        gameManager.handleGameRestart(message);
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
                    
                    // 重新初始化GameManager，使用验证后的昵称
                    gameManager.init(username, false, null, clientManager);
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
    
    @Override
    public void onBackPressed() {
        // 如果游戏面板正在显示，先隐藏游戏面板
        if (isGamePanelShowing) {
            hideGamePanel();
            return;
        }
        // 否则正常返回
        super.onBackPressed();
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
        
        // 移除全局布局监听器
        if (rootLayout != null && globalLayoutListener != null) {
            rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            android.util.Log.d("RoomActivity", "已移除全局布局监听器");
        }
        
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
    
    // ==================== 游戏相关方法 ====================
    
    /**
     * 显示游戏列表对话框
     */
    private void showGameListDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_game_list, null);
        builder.setView(dialogView);
        
        RecyclerView recyclerViewGames = dialogView.findViewById(R.id.recyclerViewGames);
        recyclerViewGames.setLayoutManager(new LinearLayoutManager(this));
        
        com.example.hakimichat.game.GameListAdapter adapter = 
            new com.example.hakimichat.game.GameListAdapter(gameInfo -> {
                // 点击游戏后，显示玩家选择对话框
                showPlayerSelectionDialog(gameInfo);
            });
        recyclerViewGames.setAdapter(adapter);
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    /**
     * 显示玩家选择对话框（邀请谁一起玩）
     */
    private void showPlayerSelectionDialog(com.example.hakimichat.game.GameListAdapter.GameInfo gameInfo) {
        // 获取房间成员列表（除了自己）
        java.util.List<String> availablePlayers = new java.util.ArrayList<>(memberList);
        availablePlayers.remove(username);
        
        if (availablePlayers.isEmpty()) {
            showToast("房间里没有其他玩家");
            return;
        }
        
        // 添加"所有人"选项
        String[] playerOptions = new String[availablePlayers.size() + 1];
        playerOptions[0] = "所有人（谁先加入谁玩）";
        for (int i = 0; i < availablePlayers.size(); i++) {
            playerOptions[i + 1] = availablePlayers.get(i);
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("邀请谁一起玩" + gameInfo.gameName + "?");
        builder.setItems(playerOptions, (dialog, which) -> {
            String invitedPlayer = which == 0 ? null : availablePlayers.get(which - 1);
            createAndInviteGame(gameInfo.gameType, invitedPlayer);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 创建游戏并发送邀请
     */
    private void createAndInviteGame(String gameType, String invitedPlayer) {
        com.example.hakimichat.game.Game game = gameManager.createGame(gameType);
        if (game == null) {
            showToast("创建游戏失败");
            return;
        }
        
        // 创建者自动加入游戏
        game.addPlayer(username);
        
        String gameId = ((com.example.hakimichat.game.BaseGame) game).getGameId();
        
        // 发送游戏邀请
        gameManager.sendGameInvite(gameType, gameId, invitedPlayer);
        
        // 显示系统消息
        String inviteMsg = invitedPlayer == null ? 
            "发起了" + game.getGameName() + "游戏，等待玩家加入" :
            "邀请 " + invitedPlayer + " 一起玩" + game.getGameName();
        Message systemMsg = new Message("系统", inviteMsg);
        messageAdapter.addMessage(systemMsg);
        
        // 打开游戏界面
        openGameActivity(gameId, false);
    }
    
    /**
     * 处理游戏邀请消息
     */
    private void handleGameInvite(Message message) {
        String sender = message.getSender();
        String gameType = message.getGameType();
        String gameId = message.getGameId();
        String invitedPlayer = message.getInvitedPlayer();
        
        // 缓存游戏类型，以便后续创建游戏实例
        gameTypeCache.put(gameId, gameType);
        
        // 直接显示游戏邀请卡片消息（不再显示额外的系统消息）
        messageAdapter.addMessage(message);
        
        // 如果是邀请所有人，或者邀请的就是自己，同时弹出对话框
        if (invitedPlayer == null || invitedPlayer.equals(username)) {
            showGameInviteDialog(sender, gameType, gameId, invitedPlayer);
        }
    }
    
    /**
     * 显示游戏邀请对话框
     */
    private void showGameInviteDialog(String sender, String gameType, String gameId, String invitedPlayer) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        
        // 根据gameType获取游戏名称
        String gameName = "TicTacToe".equals(gameType) ? "井字棋" : gameType;
        
        builder.setTitle("游戏邀请");
        builder.setMessage(sender + " 邀请你一起玩" + gameName + "，是否接受？");
        builder.setPositiveButton("接受", (dialog, which) -> {
            acceptGameInvite(gameId);
        });
        
        // 只有当邀请所有人时（invitedPlayer为null），才显示观战选项
        if (invitedPlayer == null) {
            builder.setNegativeButton("观战", (dialog, which) -> {
                spectateGame(gameId);
            });
        }
        
        builder.setNeutralButton("拒绝", null);
        builder.show();
    }
    
    /**
     * 接受游戏邀请
     */
    private void acceptGameInvite(String gameId) {
        // 获取或创建游戏实例
        com.example.hakimichat.game.Game game = gameManager.getGame(gameId);
        if (game == null) {
            // 从缓存中获取游戏类型
            String gameType = gameTypeCache.get(gameId);
            if (gameType == null) {
                gameType = "TicTacToe";  // 默认类型
            }
            // 创建游戏实例（使用邀请消息中的 gameId）
            game = gameManager.createGameWithId(gameType, gameId);
            if (game == null) {
                showToast("创建游戏失败");
                return;
            }
        }
        
        // 本地先添加自己到游戏中
        game.addPlayer(username);
        
        // 发送加入消息给其他人
        gameManager.acceptGameInvite(gameId);
        
        // 显示系统消息
        Message systemMsg = new Message("系统", "你加入了游戏");
        messageAdapter.addMessage(systemMsg);
        
        // 打开游戏界面
        openGameActivity(gameId, false);
    }
    
    /**
     * 观战游戏
     */
    private void spectateGame(String gameId) {
        // 获取或创建游戏实例
        com.example.hakimichat.game.Game game = gameManager.getGame(gameId);
        if (game == null) {
            // 从缓存中获取游戏类型
            String gameType = gameTypeCache.get(gameId);
            if (gameType == null) {
                gameType = "TicTacToe";  // 默认类型
            }
            // 创建游戏实例（使用邀请消息中的 gameId）
            game = gameManager.createGameWithId(gameType, gameId);
            if (game == null) {
                showToast("创建游戏失败");
                return;
            }
        }
        
        gameManager.addSpectator(gameId, username);
        
        // 显示系统消息
        Message systemMsg = new Message("系统", "你正在观战");
        messageAdapter.addMessage(systemMsg);
        
        // 以观战模式打开游戏界面
        openGameActivity(gameId, true);
    }
    
    /**
     * 打开游戏Activity
     */
    private void openGameActivity(String gameId, boolean isSpectator) {
        android.content.Intent intent = new android.content.Intent(this, 
            com.example.hakimichat.game.TicTacToeActivity.class);
        intent.putExtra(com.example.hakimichat.game.TicTacToeActivity.EXTRA_GAME_ID, gameId);
        intent.putExtra(com.example.hakimichat.game.TicTacToeActivity.EXTRA_USERNAME, username);
        intent.putExtra(com.example.hakimichat.game.TicTacToeActivity.EXTRA_IS_SPECTATOR, isSpectator);
        startActivity(intent);
    }
    
    /**
     * 从消息中点击加入游戏
     */
    private void handleJoinGameFromMessage(String gameId, String gameType) {
        // 获取或创建游戏实例
        com.example.hakimichat.game.Game game = gameManager.getGame(gameId);
        if (game == null) {
            // 创建游戏实例（使用邀请消息中的 gameId）
            game = gameManager.createGameWithId(gameType, gameId);
            if (game == null) {
                showToast("创建游戏失败");
                return;
            }
        }
        
        // 检查游戏是否已满
        if (game.getPlayers().size() >= game.getMaxPlayers()) {
            showToast("游戏人数已满，无法加入");
            return;
        }
        
        // 检查游戏是否已开始
        if (game.isGameOver() || (game instanceof com.example.hakimichat.game.BaseGame && 
            ((com.example.hakimichat.game.BaseGame)game).isGameStarted())) {
            showToast("游戏已开始，无法加入");
            return;
        }
        
        // 本地先添加自己到游戏中
        if (!game.addPlayer(username)) {
            showToast("加入游戏失败");
            return;
        }
        
        // 发送加入消息给其他人
        gameManager.acceptGameInvite(gameId);
        
        // 显示系统消息
        Message systemMsg = new Message("系统", "你加入了游戏");
        messageAdapter.addMessage(systemMsg);
        
        // 打开游戏界面
        openGameActivity(gameId, false);
    }
    
    /**
     * 从消息中点击观战游戏
     */
    private void handleSpectateGameFromMessage(String gameId, String gameType) {
        // 获取或创建游戏实例
        com.example.hakimichat.game.Game game = gameManager.getGame(gameId);
        if (game == null) {
            // 创建游戏实例（使用邀请消息中的 gameId）
            game = gameManager.createGameWithId(gameType, gameId);
            if (game == null) {
                showToast("创建游戏失败");
                return;
            }
        }
        
        gameManager.addSpectator(gameId, username);
        
        // 显示系统消息
        Message systemMsg = new Message("系统", "你正在观战");
        messageAdapter.addMessage(systemMsg);
        
        // 以观战模式打开游戏界面
        openGameActivity(gameId, true);
    }
}
