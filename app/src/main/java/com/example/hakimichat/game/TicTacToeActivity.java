package com.example.hakimichat.game;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
// 使用布局中的 TextView 作为头像，不再需要 Bitmap/Canvas 导入

import androidx.appcompat.app.AppCompatActivity;

import com.example.hakimichat.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 井字棋游戏Activity
 */
public class TicTacToeActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_IS_SPECTATOR = "is_spectator";
    public static final String EXTRA_IS_SINGLE_PLAYER = "is_single_player";

    private ImageButton[][] buttons = new ImageButton[3][3];
    private TextView tvGameStatus, tvPlayerX, tvPlayerO, tvSpectators;
    private TextView ivPlayerXAvatar, ivPlayerOAvatar;
    private Button btnRestart, btnExit;
    private Button btnEmoji;
    private View emojiPalette; // now refers to the inner palette row (emojiPaletteInner)

    private String gameId;
    private String username;
    private boolean isSpectator;
    private boolean isSinglePlayer;
    private TicTacToeGame game;
    private GameManager gameManager;
    private Handler mainHandler;
    // 用于管理临时气泡
    private int prevMoveCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tictactoe);

        // 获取传入参数
        gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        username = getIntent().getStringExtra(EXTRA_USERNAME);
        isSpectator = getIntent().getBooleanExtra(EXTRA_IS_SPECTATOR, false);
        isSinglePlayer = getIntent().getBooleanExtra(EXTRA_IS_SINGLE_PLAYER, false);

        mainHandler = new Handler(Looper.getMainLooper());
        gameManager = GameManager.getInstance();
        game = (TicTacToeGame) gameManager.getGame(gameId);

        if (game == null) {
            Toast.makeText(this, "游戏不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        updateUI();
        // 如果是单机模式且现在轮到 AI，启动时需要触发 AI 落子（处理电脑先手场景）
        if (isSinglePlayer && !game.isGameOver()) {
            java.util.List<String> players = game.getPlayers();
            if (players.size() >= 2) {
                String aiName = players.get(1);
                if (aiName.equals(game.getCurrentPlayer())) {
                    mainHandler.postDelayed(this::performAIMoveIfNeeded, 300);
                }
            }
        }

        // 设置游戏状态监听器
        gameManager.setGameStateListener(gameId, new GameManager.GameStateListener() {
            @Override
            public void onGameStateChanged(String gameId, JSONObject gameState) {
                        mainHandler.post(() -> {
                            // 支持 emojiEvent：优先处理表情事件（不会改变棋盘状态），否则走常规状态更新
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
                    // 如果是对方退出导致的结束，不显示重新开始按钮
                    else if (!isSpectator && !result.contains("已退出")) {
                        btnRestart.setVisibility(Button.VISIBLE);
                    }
                });
            }
        });
    }

    private void handleIncomingEmoji(String sender, String emoji) {
        // 将该表情写入游戏对象（本局有效）
        if (game != null) {
            if (game instanceof com.example.hakimichat.game.BaseGame) {
                ((com.example.hakimichat.game.BaseGame) game).setLastEmoji(sender, emoji);
            }
        }

        // 如果发送者是玩家，显示在玩家头像附近并在头像左下角显示角标
        if (game.getPlayers().contains(sender)) {
            showEmojiNearPlayerAvatar(sender, emoji);
        } else if (game.getSpectators().contains(sender)) {
            // 观战者：随机在屏幕左右显示带昵称的气泡
            showSpectatorBubble(sender, emoji);
        } else {
            // 未知身份，仍展示为临时提示（右下）
            showSpectatorBubble(sender, emoji);
        }
    }

    private void showEmojiNearPlayerAvatar(String sender, String emoji) {
        TextView anchor = null;
        // 根据玩家顺序映射到头像
        java.util.List<String> players = game.getPlayers();
        if (players.size() >= 1 && players.get(0).equals(sender)) {
            anchor = ivPlayerXAvatar;
        } else if (players.size() >= 2 && players.get(1).equals(sender)) {
            anchor = ivPlayerOAvatar;
        }

        if (anchor == null) return;

    ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
    TextView bubble = new TextView(this);
    bubble.setText(emoji);
    bubble.setTextSize(18f);
    // 使用圆角矩形聊天气泡背景（编程方式），避免使用圆形背景
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(Color.parseColor("#FFFFFF"));
    gd.setCornerRadius(18f);
    gd.setStroke(1, Color.parseColor("#DDDDDD"));
    bubble.setBackground(gd);
    bubble.setPadding(18, 8, 18, 8);

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int anchorX = loc[0];
        int anchorY = loc[1];

        // 以 content view 左上为基准，计算偏移
        int[] rootLoc = new int[2];
        root.getLocationOnScreen(rootLoc);

        // 测量气泡尺寸，然后将气泡放在头像上方的左右角（左玩家放右上，右玩家放左上）
        bubble.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        int bubbleW = bubble.getMeasuredWidth();
        int bubbleH = bubble.getMeasuredHeight();

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 垂直放在头像上方
        lp.topMargin = anchorY - rootLoc[1] - bubbleH - 6; // 贴近头像上方

        // 左侧玩家（第一个玩家）放在头像右上角，右侧玩家放在头像左上角
        if (anchor == ivPlayerOAvatar) {
            // 右侧玩家：气泡显示在头像左上角
            lp.leftMargin = anchorX - rootLoc[0] - bubbleW - 6;
        } else {
            // 左侧玩家：气泡显示在头像右上角
            lp.leftMargin = anchorX - rootLoc[0] + anchor.getWidth() + 6;
        }

        // 边界修正，避免越出屏幕
        int screenW = getResources().getDisplayMetrics().widthPixels;
        if (lp.leftMargin < 6) lp.leftMargin = 6;
        if (lp.leftMargin + bubbleW > screenW - 6) lp.leftMargin = screenW - bubbleW - 6;

        // 若超出顶部则下移到头像下方作为兜底
        if (lp.topMargin < 6) {
            lp.topMargin = anchorY - rootLoc[1] + anchor.getHeight() + 6;
        }

        root.addView(bubble, lp);

        // 自动移除
        bubble.postDelayed(() -> {
            try { root.removeView(bubble); } catch (Exception ignored) {}
        }, 2500);
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

        // 计算玩家信息区域底部，确保观众气泡不会出现在玩家信息上方
        int minY = 100;
        try {
            int[] p1 = new int[2];
            int[] p2 = new int[2];
            ivPlayerXAvatar.getLocationOnScreen(p1);
            ivPlayerOAvatar.getLocationOnScreen(p2);
            int bottom1 = p1[1] + ivPlayerXAvatar.getHeight();
            int bottom2 = p2[1] + ivPlayerOAvatar.getHeight();
            int[] rootLoc = new int[2];
            root.getLocationOnScreen(rootLoc);
            minY = Math.max(minY, Math.max(bottom1, bottom2) - rootLoc[1] + 8);
        } catch (Exception ignored) {}

        int maxRange = Math.max(1, screenH - minY - 200);
        int y = minY + rnd.nextInt(maxRange);

        // 聊天气泡风格：浅灰背景，圆角
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#F1F1F1"));
        gd.setCornerRadius(18f);
        gd.setStroke(1, Color.parseColor("#E0E0E0"));
        bubble.setBackground(gd);

    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = y;
    lp.leftMargin = left ? 8 : getResources().getDisplayMetrics().widthPixels - 220;
        root.addView(bubble, lp);

        bubble.postDelayed(() -> {
            try { root.removeView(bubble); } catch (Exception ignored) {}
        }, 2500);
    }

    private void initViews() {
        tvGameStatus = findViewById(R.id.tvGameStatus);
        tvPlayerX = findViewById(R.id.tvPlayerX);
        tvPlayerO = findViewById(R.id.tvPlayerO);
        ivPlayerXAvatar = findViewById(R.id.ivPlayerXAvatar);
        ivPlayerOAvatar = findViewById(R.id.ivPlayerOAvatar);
        tvSpectators = findViewById(R.id.tvSpectators);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
        btnEmoji = findViewById(R.id.btnEmoji);
        // bind the inner palette row so toggling it won't reflow the layout
        emojiPalette = findViewById(R.id.emojiPaletteInner);

        // 初始化棋盘按钮
        buttons[0][0] = findViewById(R.id.btn_00);
        buttons[0][1] = findViewById(R.id.btn_01);
        buttons[0][2] = findViewById(R.id.btn_02);
        buttons[1][0] = findViewById(R.id.btn_10);
        buttons[1][1] = findViewById(R.id.btn_11);
        buttons[1][2] = findViewById(R.id.btn_12);
        buttons[2][0] = findViewById(R.id.btn_20);
        buttons[2][1] = findViewById(R.id.btn_21);
        buttons[2][2] = findViewById(R.id.btn_22);
    }

    private void setupListeners() {
        // 设置棋盘按钮点击监听
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                final int row = i;
                final int col = j;
                buttons[i][j].setOnClickListener(v -> onCellClick(row, col));
            }
        }

        // 重新开始按钮
        btnRestart.setOnClickListener(v -> {
            if (isSinglePlayer) {
                // 单机模式：只在本地重置，不发送网络消息
                if (game != null) {
                    game.reset();
                    updateUI();
                    btnRestart.setVisibility(Button.GONE);
                    // 重置后如果轮到 AI，触发 AI 落子（处理重置后电脑先手）
                    java.util.List<String> players = game.getPlayers();
                    if (players.size() >= 2) {
                        String aiName = players.get(1);
                        if (aiName.equals(game.getCurrentPlayer())) {
                            mainHandler.postDelayed(this::performAIMoveIfNeeded, 300);
                        }
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

        // 表情面板按钮绑定（12 个）
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
        // 立刻隐藏面板并提示已发送（UI 气泡在下一步实现）
    if (emojiPalette != null) emojiPalette.setVisibility(View.GONE);
            // 已取消发送后的弹窗提示，UI 会显示气泡
    }

    private void onCellClick(int row, int col) {
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
            moveData.put("row", row);
            moveData.put("col", col);

            // 通过GameManager发送移动
            gameManager.sendGameMove(gameId, username, moveData);

            // 如果是单机模式，触发 AI 回复（简单策略：选第一个空格）
            if (isSinglePlayer) {
                mainHandler.postDelayed(this::performAIMoveIfNeeded, 300);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showToast("操作失败");
        }
    }

    // 简单 AI：查找第一个空位并下子（AI 名称为 players 列表的第二项）
    private void performAIMoveIfNeeded() {
        if (!isSinglePlayer || game == null || game.isGameOver()) return;

        String current = game.getCurrentPlayer();
        if (current == null) return;

        java.util.List<String> players = game.getPlayers();
        if (players.size() < 2) return;
        String aiName = players.get(1);

        // 只有当轮到 AI 时才移动
        if (!aiName.equals(current)) return;

        // 构造当前棋盘的本地副本
        String[][] board = new String[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = game.getPiece(i, j);
            }
        }

        // 确定符号：AI 的棋子和人的棋子
        String aiPiece = game.getPlayerPiece(aiName); // "X" 或 "O"
        String humanName = players.get(0);
        String humanPiece = game.getPlayerPiece(humanName);

        java.util.Random rnd = new java.util.Random();
        // 90% 概率使用最优策略，10% 随机策略
        boolean useOptimal = rnd.nextInt(100) < 90;

        int bestRow = -1, bestCol = -1;

        if (useOptimal) {
            int[] best = findBestMove(board, aiPiece, humanPiece);
            if (best != null) {
                bestRow = best[0];
                bestCol = best[1];
            }
        }

        // 若未选到最优或选择随机分支，随机挑选空位
        if (bestRow == -1) {
            java.util.List<int[]> empties = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j].equals("")) empties.add(new int[]{i, j});
                }
            }
            if (empties.isEmpty()) return;
            int[] pick = empties.get(rnd.nextInt(empties.size()));
            bestRow = pick[0];
            bestCol = pick[1];
        }

        try {
            JSONObject move = new JSONObject();
            move.put("row", bestRow);
            move.put("col", bestCol);
            gameManager.sendGameMove(gameId, aiName, move);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Minimax: 找到对 AI 最优的落子，返回 {row, col} 或 null
    private int[] findBestMove(String[][] board, String aiPiece, String humanPiece) {
        int bestScore = Integer.MIN_VALUE;
        java.util.List<int[]> bestMoves = new java.util.ArrayList<>();

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j].equals("")) {
                    board[i][j] = aiPiece;
                    int score = minimax(board, 0, false, aiPiece, humanPiece);
                    board[i][j] = "";

                    if (score > bestScore) {
                        bestScore = score;
                        bestMoves.clear();
                        bestMoves.add(new int[]{i, j});
                    } else if (score == bestScore) {
                        // 相同的最优分值，加入候选集合
                        bestMoves.add(new int[]{i, j});
                    }
                }
            }
        }

        if (bestMoves.isEmpty()) return null;

        // 如果有多个最优落子，随机选取一个，避免每次都落同一位置
        java.util.Random rnd = new java.util.Random();
        return bestMoves.get(rnd.nextInt(bestMoves.size()));
    }

    // 递归 Minimax 实现
    private int minimax(String[][] board, int depth, boolean isMaximizing, String aiPiece, String humanPiece) {
        String winner = evaluateBoard(board, aiPiece, humanPiece);
        if (winner != null) {
            if (winner.equals(aiPiece)) return 10 - depth; // 更快获胜得分更高
            if (winner.equals(humanPiece)) return depth - 10; // 更晚被击败更好（负分）
            return 0; // 平局
        }

        if (isMaximizing) {
            int best = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j].equals("")) {
                        board[i][j] = aiPiece;
                        int val = minimax(board, depth + 1, false, aiPiece, humanPiece);
                        board[i][j] = "";
                        best = Math.max(best, val);
                    }
                }
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j].equals("")) {
                        board[i][j] = humanPiece;
                        int val = minimax(board, depth + 1, true, aiPiece, humanPiece);
                        board[i][j] = "";
                        best = Math.min(best, val);
                    }
                }
            }
            return best;
        }
    }

    // 评估当前棋盘是否有赢家或平局。返回 aiPiece/humanPiece/"DRAW"/null
    private String evaluateBoard(String[][] b, String aiPiece, String humanPiece) {
        // 行
        for (int i = 0; i < 3; i++) {
            if (!b[i][0].equals("") && b[i][0].equals(b[i][1]) && b[i][1].equals(b[i][2])) {
                return b[i][0];
            }
        }
        // 列
        for (int j = 0; j < 3; j++) {
            if (!b[0][j].equals("") && b[0][j].equals(b[1][j]) && b[1][j].equals(b[2][j])) {
                return b[0][j];
            }
        }
        // 对角
        if (!b[0][0].equals("") && b[0][0].equals(b[1][1]) && b[1][1].equals(b[2][2]))
            return b[0][0];
        if (!b[0][2].equals("") && b[0][2].equals(b[1][1]) && b[1][1].equals(b[2][0]))
            return b[0][2];

        // 检查是否还有空位
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (b[i][j].equals("")) return null; // 游戏未结束
            }
        }
        return "DRAW";
    }

    private void updateUI() {
        // 更新玩家信息
        java.util.List<String> players = game.getPlayers();
        // 左侧：玩家 X，右侧：玩家 O（类似聊天布局）
        if (players.size() >= 1) {
            String p0 = players.get(0);
            // 单机模式下，如果是 AI 名称以“电脑”开头，显示为 "电脑"
            if (p0 != null && p0.startsWith("电脑")) p0 = "电脑";
            tvPlayerX.setText("玩家X: " + (p0 != null ? p0 : ""));
        } else {
            tvPlayerX.setText("玩家X: ");
        }
        if (players.size() >= 2) {
            String p1 = players.get(1);
            if (p1 != null && p1.startsWith("电脑")) p1 = "电脑";
            tvPlayerO.setText("玩家O: " + (p1 != null ? p1 : ""));
        } else {
            tvPlayerO.setText("玩家O: ");
        }

        // 头像：使用布局中的 TextView，显示首字，AI 显示为“电”
        try {
            if (players.size() >= 1) {
                String name0 = players.get(0);
                String display = (name0 != null && name0.startsWith("电脑")) ? "电" : (name0 != null && !name0.isEmpty() ? String.valueOf(name0.charAt(0)) : "?");
                ivPlayerXAvatar.setText(display);
            } else {
                ivPlayerXAvatar.setText("");
            }

            if (players.size() >= 2) {
                String name1 = players.get(1);
                String display1 = (name1 != null && name1.startsWith("电脑")) ? "电" : (name1 != null && !name1.isEmpty() ? String.valueOf(name1.charAt(0)) : "?");
                ivPlayerOAvatar.setText(display1);
            } else {
                ivPlayerOAvatar.setText("");
            }
        } catch (Exception ignore) {
            // 忽略资源加载错误
        }

        // 更新游戏状态
        if (game.isGameOver()) {
            tvGameStatus.setText(game.getGameResult());
        } else {
            // 与 Gobang 行为一致：玩家不足两人时显示等待提示
            if (players.size() < 2) {
                tvGameStatus.setText("等待玩家加入...");
            } else if (game.getCurrentPlayer() != null) {
                String currentPiece = game.getPlayerPiece(game.getCurrentPlayer());
                tvGameStatus.setText("当前回合: " + game.getCurrentPlayer() + " (" + currentPiece + ")");
            } else {
                tvGameStatus.setText("等待玩家加入...");
            }
        }

        // 更新棋盘
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String piece = game.getPiece(i, j);
                ImageButton button = buttons[i][j];

                if (piece.equals("X")) {
                    button.setImageResource(R.drawable.blue_x);
                } else if (piece.equals("O")) {
                    button.setImageResource(R.drawable.red_o);
                } else {
                    button.setImageResource(0);
                }

                // 观战者和游戏结束时禁用按钮
                button.setEnabled(!isSpectator && !game.isGameOver());
            }
        }

        // 更新观战者列表
        java.util.List<String> spectators = game.getSpectators();
        if (!spectators.isEmpty()) {
            tvSpectators.setVisibility(TextView.VISIBLE);
            tvSpectators.setText("观战者: " + String.join(", ", spectators));
        } else {
            tvSpectators.setVisibility(TextView.GONE);
        }
    }

    private void updateFromState(JSONObject state) {
        game.setGameState(state);
        updateUI();

        // 如果游戏重新开始了（不是游戏结束状态），隐藏重新开始按钮
        if (!game.isGameOver()) {
            btnRestart.setVisibility(Button.GONE);
        }

        // 单机模式：如果现在轮到 AI，则延迟执行 AI 落子以模拟思考
        if (isSinglePlayer && !game.isGameOver()) {
            String current = game.getCurrentPlayer();
            java.util.List<String> players = game.getPlayers();
            if (players.size() >= 2) {
                String aiName = players.get(1);
                if (aiName.equals(current)) {
                    mainHandler.postDelayed(this::performAIMoveIfNeeded, 300);
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
                    // 观战者退出，移除观战者并广播更新
                    game.removeSpectator(username);
                    gameManager.broadcastGameState(gameId);
                } else {
                    // 玩家退出游戏
                    if (isSinglePlayer) {
                        // 单机模式下只在本地移除玩家并不发送网络消息
                        game.removePlayer(username);
                        // 移除本地 AI（名称以“电脑”开头）
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