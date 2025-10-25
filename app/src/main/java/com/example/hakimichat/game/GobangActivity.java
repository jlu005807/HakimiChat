package com.example.hakimichat.game;

import android.graphics.Point;
import android.widget.ImageView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hakimichat.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GobangActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_IS_SPECTATOR = "is_spectator";
    public static final String EXTRA_IS_SINGLE_PLAYER = "is_single_player";
    public static final String EXTRA_IS_STRICT_MODE = "is_strict_mode";

    private GobangBoardView boardView;
    private TextView tvGameStatus, tvBlackPlayer, tvWhitePlayer, tvSpectators;
    private TextView ivBlackAvatar, ivWhiteAvatar;
    private Button btnUndo, btnRestart, btnExit;
    private Button btnEmoji;
    private View emojiPalette;
    private int prevMoveCount = -1;

    private String gameId;
    private String username;
    private boolean isSpectator;
    private boolean isSinglePlayer;
    private boolean isStrictMode;
    private GobangGame game;
    private GameManager gameManager;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gobang);

        // 获取传入参数
        gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        username = getIntent().getStringExtra(EXTRA_USERNAME);
        isSpectator = getIntent().getBooleanExtra(EXTRA_IS_SPECTATOR, false);
        isSinglePlayer = getIntent().getBooleanExtra(EXTRA_IS_SINGLE_PLAYER, false);
        isStrictMode = getIntent().getBooleanExtra(EXTRA_IS_STRICT_MODE, false);

        mainHandler = new Handler(Looper.getMainLooper());
        gameManager = GameManager.getInstance();
        game = (GobangGame) gameManager.getGame(gameId);

        if (game == null) {
            Toast.makeText(this, "游戏不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 设置严格模式
        if (isStrictMode) {
            game.setStrictMode(true);
        }

        initViews();
        setupListeners();
        updateUI();

        // 如果是单机模式且轮到AI先手，触发AI移动
        if (isSinglePlayer && game.canStart()) {
            String currentPlayer = game.getCurrentPlayer();
            if (currentPlayer != null && !currentPlayer.equals(username)) {
                mainHandler.postDelayed(this::performAIMove, 500);
            }
        }

        // 设置游戏状态监听器
        gameManager.setGameStateListener(gameId, new GameManager.GameStateListener() {
            @Override
            public void onGameStateChanged(String gameId, JSONObject gameState) {
                        mainHandler.post(() -> {
                            if (gameState != null && gameState.has("emojiEvent")) {
                                try {
                                    JSONObject evt = gameState.getJSONObject("emojiEvent");
                                    String sender = evt.optString("sender", null);
                                    String emoji = evt.optString("emoji", null);
                                    if (sender != null && emoji != null) {
                                        handleIncomingEmoji(sender, emoji);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                updateFromState(gameState);
                            }
                        });
            }

            @Override
            public void onGameEnded(String gameId, String result) {
                mainHandler.post(() -> {
                    showToast(result);

                    // 如果是发起者退出，自动关闭Activity
                    if (result.contains("发起者退出")) {
                        finish();
                        return;
                    }

                    updateUI();

                    // 如果是"已离开游戏"的通知，隐藏重新开始按钮
                    if (result.contains("已离开游戏")) {
                        btnRestart.setVisibility(Button.GONE);
                    }
                    // 只有当双方都在且游戏正常结束时才显示重新开始按钮
                    else if (!isSpectator && !result.contains("已退出")) {
                        btnRestart.setVisibility(Button.VISIBLE);
                    }
                });
            }
        });
    }

    private void handleIncomingEmoji(String sender, String emoji) {
        if (game != null) {
            if (game instanceof com.example.hakimichat.game.BaseGame) {
                ((com.example.hakimichat.game.BaseGame) game).setLastEmoji(sender, emoji);
            }
        }

        // 如果发送者是玩家，显示在玩家头像附近并设置角标
        String black = game.getBlackPlayerName();
        String white = game.getWhitePlayerName();
        if (sender != null && (sender.equals(black) || sender.equals(white))) {
            showEmojiNearPlayerAvatar(sender, emoji);
        } else if (game.getSpectators().contains(sender)) {
            showSpectatorBubble(sender, emoji);
        } else {
            showSpectatorBubble(sender, emoji);
        }
    }

    private void showEmojiNearPlayerAvatar(String sender, String emoji) {
        TextView anchor = null;
        String black = game.getBlackPlayerName();
        String white = game.getWhitePlayerName();
        if (sender != null && sender.equals(black)) anchor = ivBlackAvatar;
        else if (sender != null && sender.equals(white)) anchor = ivWhiteAvatar;
        if (anchor == null) return;

    ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
    TextView bubble = new TextView(this);
    bubble.setText(emoji);
    bubble.setTextSize(18f);
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor("#FFFFFF"));
    gd.setCornerRadius(18f);
    gd.setStroke(1, Color.parseColor("#DDDDDD"));
    bubble.setBackground(gd);
    bubble.setPadding(18, 8, 18, 8);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);

        // 测量气泡并放置在头像上方的左右角（左玩家放右上，右玩家放左上）
        bubble.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        int bubbleW = bubble.getMeasuredWidth();
        int bubbleH = bubble.getMeasuredHeight();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 垂直放在头像上方
        lp.topMargin = loc[1] - rootLoc[1] - bubbleH - 6;

        // 如果是右侧玩家（白方），气泡放在头像左上角；否则放右上角
        if (anchor == ivWhiteAvatar) {
            lp.leftMargin = loc[0] - rootLoc[0] - bubbleW - 6;
        } else {
            lp.leftMargin = loc[0] - rootLoc[0] + anchor.getWidth() + 6;
        }

        // 边界修正
        int screenW = getResources().getDisplayMetrics().widthPixels;
        if (lp.leftMargin < 6) lp.leftMargin = 6;
        if (lp.leftMargin + bubbleW > screenW - 6) lp.leftMargin = screenW - bubbleW - 6;

        if (lp.topMargin < 6) {
            lp.topMargin = loc[1] - rootLoc[1] + anchor.getHeight() + 6; // 兜底到头像下方
        }

        root.addView(bubble, lp);
        bubble.postDelayed(() -> { try { root.removeView(bubble); } catch (Exception ignored) {} }, 2500);
    }

    private void setAvatarBadge(String sender, String emoji) {
        // 已取消头像角标（需求变更）
    }

    private void showSpectatorBubble(String sender, String emoji) {
        ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
        TextView bubble = new TextView(this);
        bubble.setText(sender + ": " + emoji);
        bubble.setTextSize(14f);
        bubble.setBackgroundResource(com.example.hakimichat.R.drawable.bg_avatar_circle);
        bubble.setPadding(12, 8, 12, 8);

        java.util.Random rnd = new java.util.Random();
        boolean left = rnd.nextBoolean();
        int screenH = getResources().getDisplayMetrics().heightPixels;

        // 计算玩家信息底部，保证观战气泡不会遮挡玩家区域
        int minY = 100;
        try {
            int[] p1 = new int[2];
            int[] p2 = new int[2];
            ivBlackAvatar.getLocationOnScreen(p1);
            ivWhiteAvatar.getLocationOnScreen(p2);
            int bottom1 = p1[1] + ivBlackAvatar.getHeight();
            int bottom2 = p2[1] + ivWhiteAvatar.getHeight();
            int[] rootLoc = new int[2];
            root.getLocationOnScreen(rootLoc);
            minY = Math.max(minY, Math.max(bottom1, bottom2) - rootLoc[1] + 8);
        } catch (Exception ignored) {}

        int maxRange = Math.max(1, screenH - minY - 200);
        int y = minY + rnd.nextInt(maxRange);

        // 聊天气泡风格：浅灰背景，圆角
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F1F1F1"));
        bg.setCornerRadius(18f);
        bg.setStroke(1, Color.parseColor("#E0E0E0"));
        bubble.setBackground(bg);

    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = y;
    lp.leftMargin = left ? 8 : getResources().getDisplayMetrics().widthPixels - 220;
    root.addView(bubble, lp);
        bubble.postDelayed(() -> { try { root.removeView(bubble); } catch (Exception ignored) {} }, 2500);
    }

    private void initViews() {
        boardView = findViewById(R.id.gobangBoard);
        tvGameStatus = findViewById(R.id.tvGameStatus);
        tvBlackPlayer = findViewById(R.id.tvBlackPlayer);
    ivBlackAvatar = findViewById(R.id.ivBlackAvatar);
        tvWhitePlayer = findViewById(R.id.tvWhitePlayer);
    ivWhiteAvatar = findViewById(R.id.ivWhiteAvatar);
        tvSpectators = findViewById(R.id.tvSpectators);
        btnUndo = findViewById(R.id.btnUndo);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
    btnEmoji = findViewById(R.id.btnEmoji);
    // bind inner palette row so toggling doesn't reflow layout
    emojiPalette = findViewById(R.id.emojiPaletteInner);
    }

    // 头像由布局中的 TextView 展示，这里不再生成 Bitmap

    private void setupListeners() {
        // 棋盘点击监听
        boardView.setOnBoardClickListener((x, y) -> onCellClick(x, y));

        // 悔棋按钮（仅单机模式可见）
        btnUndo.setOnClickListener(v -> {
            if (game != null && game.isAiEnabled()) {
                if (game.canUndo() && game.undoMove()) {
                    updateUI();
                    showToast("已悔棋");
                } else {
                    showToast("当前无法悔棋");
                }
            } else {
                showToast("真人对战不支持悔棋");
            }
        });

        // 重新开始按钮
        btnRestart.setOnClickListener(v -> {
            if (isSinglePlayer) {
                // 单机模式：只在本地重置
                if (game != null) {
                    game.reset();
                    updateUI();
                    btnRestart.setVisibility(Button.GONE);
                    
                    // 如果是AI先手，触发AI移动
                    String currentPlayer = game.getCurrentPlayer();
                    if (currentPlayer != null && !currentPlayer.equals(username)) {
                        mainHandler.postDelayed(this::performAIMove, 500);
                    }
                }
            } else {
                if (gameManager.canRestartGame(gameId, username)) {
                    gameManager.restartGame(gameId);
                    btnRestart.setVisibility(Button.GONE);
                } else {
                    showToast("只有玩家可以重新开始游戏");
                }
            }
        });

        // 退出按钮
        btnExit.setOnClickListener(v -> finish());

        // 表情按钮：切换面板显示
        if (btnEmoji != null) {
            btnEmoji.setOnClickListener(v -> {
                if (emojiPalette != null) {
                    emojiPalette.setVisibility(emojiPalette.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
            });
        }

        // 绑定表情面板按钮
        bindEmojiClick(R.id.emoji_1);
        bindEmojiClick(R.id.emoji_2);
        bindEmojiClick(R.id.emoji_3);
        bindEmojiClick(R.id.emoji_4);
        bindEmojiClick(R.id.emoji_5);
        bindEmojiClick(R.id.emoji_6);
        bindEmojiClick(R.id.emoji_7);
        bindEmojiClick(R.id.emoji_8);
        bindEmojiClick(R.id.emoji_9);
        bindEmojiClick(R.id.emoji_10);
        bindEmojiClick(R.id.emoji_11);
        bindEmojiClick(R.id.emoji_12);
    }

    private void bindEmojiClick(int resId) {
        try {
            View v = findViewById(resId);
            if (v instanceof TextView) {
                ((TextView) v).setOnClickListener(view -> {
                    String emoji = ((TextView) view).getText().toString();
                    sendEmoji(emoji);
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void sendEmoji(String emoji) {
        // 仅允许玩家或观战者发送
        boolean isPlayer = game.getPlayers().contains(username);
        boolean isSpec = game.getSpectators().contains(username);
        if (!isPlayer && !isSpec) {
            showToast("只有玩家或观战者可以发送表情");
            if (emojiPalette != null) emojiPalette.setVisibility(View.GONE);
            return;
        }

        gameManager.sendGameEmoji(gameId, username, emoji);
    if (emojiPalette != null) emojiPalette.setVisibility(View.GONE);
        // 取消发送后的吐司提示，使用气泡显示反馈
    }

    private void onCellClick(int x, int y) {
        if (isSpectator) {
            showToast("观战者不能操作");
            return;
        }

        // 检查游戏是否已开始
        if (!game.canStart()) {
            showToast("等待玩家加入");
            return;
        }

        // 检查当前玩家
        String currentPlayer = game.getCurrentPlayer();
        if (currentPlayer == null) {
            showToast("游戏尚未开始");
            return;
        }

        if (!currentPlayer.equals(username)) {
            showToast("还没轮到你");
            return;
        }

        // 创建移动数据
        try {
            JSONObject moveData = new JSONObject();
            moveData.put("x", x);
            moveData.put("y", y);

            // 通过GameManager发送移动
            gameManager.sendGameMove(gameId, username, moveData);

            // 如果是单机模式，触发AI回复
            if (isSinglePlayer) {
                mainHandler.postDelayed(this::performAIMove, 500);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showToast("操作失败");
        }
    }

    private void performAIMove() {
        if (!isSinglePlayer || game == null || game.isGameOver()) return;

        String current = game.getCurrentPlayer();
        if (current == null) return;

        java.util.List<String> players = game.getPlayers();
        if (players.size() < 2) return;
        
        // 找到AI玩家（不是当前用户的那个）
        String aiName = null;
        for (String player : players) {
            if (!player.equals(username)) {
                aiName = player;
                break;
            }
        }
        
        if (aiName == null) return;

        // 只有当轮到AI时才移动
        if (!aiName.equals(current)) return;

        Point aiMove = game.getAiMove();
        if (aiMove != null) {
            try {
                JSONObject moveData = new JSONObject();
                moveData.put("x", aiMove.x);
                moveData.put("y", aiMove.y);
                gameManager.sendGameMove(gameId, aiName, moveData);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateUI() {
        // 更新玩家信息 - 使用实际的黑白方分配
        String blackPlayer = game.getBlackPlayerName();
        String whitePlayer = game.getWhitePlayerName();
        
        if (blackPlayer != null) {
            tvBlackPlayer.setText("黑方: " + blackPlayer);
        } else {
            tvBlackPlayer.setText("黑方: ");
        }
        
        if (whitePlayer != null) {
            tvWhitePlayer.setText("白方: " + whitePlayer);
        } else {
            tvWhitePlayer.setText("白方: ");
        }

        // 头像显示：将头像绘制为昵称首字符（类似聊天），若为 AI（以"电脑"开头）也显示首字
        try {
            int sizeDp = 44; // 与布局中宽高一致
            float density = getResources().getDisplayMetrics().density;
            int sizePx = Math.round(sizeDp * density);

            if (blackPlayer != null) {
                String displayName = blackPlayer.startsWith("电脑") ? "电" : (blackPlayer.isEmpty() ? "?" : String.valueOf(blackPlayer.charAt(0)));
                ivBlackAvatar.setText(displayName);
            } else {
                ivBlackAvatar.setText("");
            }

            if (whitePlayer != null) {
                String displayName = whitePlayer.startsWith("电脑") ? "电" : (whitePlayer.isEmpty() ? "?" : String.valueOf(whitePlayer.charAt(0)));
                ivWhiteAvatar.setText(displayName);
            } else {
                ivWhiteAvatar.setText("");
            }
        } catch (Exception ignore) {
            // 忽略资源加载错误，不影响核心逻辑
        }

        // 更新游戏状态
        if (game.isGameOver()) {
            tvGameStatus.setText(game.getGameResult());
        } else {
            // 如果玩家不足两人，显示等待玩家加入
            java.util.List<String> players = game.getPlayers();
            if (players.size() < 2) {
                tvGameStatus.setText("等待玩家加入...");
            } else if (game.getCurrentPlayer() != null) {
                String piece = game.getCurrentPlayer().equals(blackPlayer) ? "黑子" : "白子";
                tvGameStatus.setText("当前回合: " + game.getCurrentPlayer() + " (" + piece + ")");
            } else {
                tvGameStatus.setText("等待玩家加入...");
            }
        }

        // 更新棋盘
        try {
            JSONObject state = game.getGameState();
            JSONArray boardArray = state.getJSONArray("board");
            int[][] board = new int[15][15];
            for (int i = 0; i < 15; i++) {
                JSONArray row = boardArray.getJSONArray(i);
                for (int j = 0; j < 15; j++) {
                    board[i][j] = row.getInt(j);
                }
            }
            Point lastMove = null;
            if (state.getInt("moveCount") > 0) {
                // 从moveHistory获取lastMove，简化处理，假设GobangGame有提供
                // 这里暂时不处理lastMove动画
            }
            boardView.updateBoard(board, lastMove);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 控制棋盘是否可点击
        boardView.setEnabled(!isSpectator && !game.isGameOver());

        // 更新观战者列表
        java.util.List<String> spectators = game.getSpectators();
        if (!spectators.isEmpty()) {
            tvSpectators.setVisibility(TextView.VISIBLE);
            tvSpectators.setText("观战者: " + String.join(", ", spectators));
        } else {
            tvSpectators.setVisibility(TextView.GONE);
        }

        // 显示悔棋按钮（仅人机对战模式）
        if (game != null && game.isAiEnabled() && !game.isGameOver()) {
            btnUndo.setVisibility(Button.VISIBLE);
            // 根据canUndo()动态启用/禁用按钮
            boolean canUndo = game.canUndo();
            btnUndo.setEnabled(canUndo);
            // 禁用时降低透明度，启用时恢复
            btnUndo.setAlpha(canUndo ? 1.0f : 0.5f);
        } else {
            btnUndo.setVisibility(Button.GONE);
        }
    }

    private void updateFromState(JSONObject state) {
        game.setGameState(state);
        updateUI();

        // 如果游戏重新开始了，隐藏重新开始按钮
        if (!game.isGameOver()) {
            btnRestart.setVisibility(Button.GONE);
        }

        // 单机模式：如果现在轮到AI，则延迟执行AI落子
        if (isSinglePlayer && !game.isGameOver()) {
            String current = game.getCurrentPlayer();
            java.util.List<String> players = game.getPlayers();
            if (players.size() >= 2) {
                String aiName = players.get(1);
                if (aiName.equals(current)) {
                    mainHandler.postDelayed(this::performAIMove, 500);
                }
            }
        }

        // 更新 moveCount 跟踪（可用于后续逻辑）
        try {
            int moveCount = state.optInt("moveCount", -1);
            prevMoveCount = moveCount;
        } catch (Exception ignored) {}
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理游戏状态监听器
        if (gameId != null) {
            gameManager.removeGameStateListener(gameId);

            if (game != null) {
                if (isSpectator) {
                    // 观战者退出
                    game.removeSpectator(username);
                    gameManager.broadcastGameState(gameId);
                } else {
                    // 玩家退出游戏
                    if (isSinglePlayer) {
                        // 单机模式下只在本地移除
                        game.removePlayer(username);
                        java.util.List<String> ps = game.getPlayers();
                        for (String p : new java.util.ArrayList<>(ps)) {
                            if (p != null && p.startsWith("电脑")) {
                                game.removePlayer(p);
                            }
                        }
                    } else {
                        // 非单机：通知网络
                        gameManager.quitGame(gameId, username);
                    }
                }
            }
        }
    }
}
