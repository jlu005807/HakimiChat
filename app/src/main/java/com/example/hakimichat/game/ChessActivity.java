package com.example.hakimichat.game;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hakimichat.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChessActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_IS_SPECTATOR = "is_spectator";
    private ChessBoardView boardView;
    private TextView tvGameStatus, tvWhitePlayer, tvBlackPlayer, tvSpectators;
    private TextView ivWhiteAvatar, ivBlackAvatar;
    private Button btnUndo, btnRestart, btnExit;
    private Button btnEmoji;
    private View emojiPalette;

    private String gameId;
    private String username;
    private boolean isSpectator;
    private ChessGame game;
    private GameManager gameManager;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chess);

    // 获取传入参数
    gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
    username = getIntent().getStringExtra(EXTRA_USERNAME);
    isSpectator = getIntent().getBooleanExtra(EXTRA_IS_SPECTATOR, false);

        mainHandler = new Handler(Looper.getMainLooper());
        gameManager = GameManager.getInstance();
        game = (ChessGame) gameManager.getGame(gameId);

        if (game == null) {
            Toast.makeText(this, "游戏不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            initViews();
            setupListeners();
            updateUI();
        } catch (Exception e) {
            // 记录并提示，而不是直接崩溃，便于获取真实堆栈信息
            e.printStackTrace();
            android.util.Log.e("ChessActivity", "初始化时发生异常", e);
            Toast.makeText(this, "进入棋盘时发生错误：" + e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            // 关闭界面以避免不稳定状态
            finish();
            return;
        }


        // 设置游戏状态监听器（支持 emojiEvent）
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
                        btnRestart.setVisibility(android.view.View.GONE);
                    }
                });
            }
        });
    }

    private void initViews() {
        boardView = findViewById(R.id.chessBoardView);
        tvGameStatus = findViewById(R.id.tvGameStatus);
        tvWhitePlayer = findViewById(R.id.tvWhitePlayer);
        tvBlackPlayer = findViewById(R.id.tvBlackPlayer);
        ivWhiteAvatar = findViewById(R.id.ivWhiteAvatar);
        ivBlackAvatar = findViewById(R.id.ivBlackAvatar);
        tvSpectators = findViewById(R.id.tvSpectators);
        btnUndo = findViewById(R.id.btnUndo);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
    btnEmoji = findViewById(R.id.btnEmoji);
    // bind inner palette row so toggling doesn't reflow layout
    emojiPalette = findViewById(R.id.emojiPaletteInner);

        // 设置棋盘视图的回调
        boardView.setGameCallback(new ChessBoardView.ChessGameCallback() {
            @Override
            public void onPieceSelected(String pieceInfo) {
                // 可以在这里显示选中棋子的信息
            }

            @Override
            public void onPieceMoved(int fromRow, int fromCol, int toRow, int toCol) {
                // 处理玩家移动
                handlePlayerMove(fromRow, fromCol, toRow, toCol);
            }

            @Override
            public void onPlayerChanged(int player) {
                updateUI();
            }

            @Override
            public void onGameOver(String message) {
                tvGameStatus.setText(message);
            }
        });
    }

    private void setupListeners() {
        btnUndo.setOnClickListener(v -> {
            showToast("当前不支持悔棋");
        });

        btnRestart.setOnClickListener(v -> {
            if (isSpectator) {
                showToast("观战者不能重新开始");
                return;
            }
            // 尝试通过 GameManager 请求重启（多人场景）
            if (gameManager.canRestartGame(gameId, username)) {
                gameManager.restartGame(gameId);
            } else {
                // 无法通过网络重启时，回退到本地重置（方便调试）
                boardView.restartGame();
                updateUI();
                btnRestart.setVisibility(Button.GONE);
            }
        });

        btnExit.setOnClickListener(v -> {
            finish();
        });

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

    private void handlePlayerMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (isSpectator) {
            showToast("观战者不能移动棋子");
            return;
        }

        // 创建移动数据
        JSONObject moveData = new JSONObject();
        try {
            moveData.put("fromRow", fromRow);
            moveData.put("fromCol", fromCol);
            moveData.put("toRow", toRow);
            moveData.put("toCol", toCol);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        // 发送移动到GameManager
        gameManager.sendGameMove(gameId, username, moveData);
    }

    private void handleIncomingEmoji(String sender, String emoji) {
        if (game != null) {
            if (game instanceof com.example.hakimichat.game.BaseGame) {
                ((com.example.hakimichat.game.BaseGame) game).setLastEmoji(sender, emoji);
            }
        }

        // 如果发送者是玩家，显示在玩家头像附近并设置角标
        String white = game.getWhitePlayerName();
        String black = game.getBlackPlayerName();
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

        // 左侧头像的表情放在头像右上角，右侧头像的表情放在头像左上角（与其他游戏保持一致）
        if (anchor == ivWhiteAvatar) {
            // 左侧 avatar -> bubble 放右上
            lp.leftMargin = loc[0] - rootLoc[0] + anchor.getWidth() + 6;
        } else {
            // 右侧 avatar -> bubble 放左上
            lp.leftMargin = loc[0] - rootLoc[0] - bubbleW - 6;
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


    private void updateUI() {
        if (game == null) return;

        // 同步棋盘状态到视图
        boardView.setBoardFromGame((ChessGame) game);

        // 更新玩家信息
        tvWhitePlayer.setText("白方: " + (game.getWhitePlayerName() != null ? game.getWhitePlayerName() : "等待中"));
        tvBlackPlayer.setText("黑方: " + (game.getBlackPlayerName() != null ? game.getBlackPlayerName() : "等待中"));

        // 设置头像文字（显示昵称首字或"电"表示电脑），与其他游戏 Activity 保持一致
        try {
            String whitePlayer = game.getWhitePlayerName();
            String blackPlayer = game.getBlackPlayerName();

            if (whitePlayer != null) {
                String display = whitePlayer.startsWith("电脑") ? "电" : (whitePlayer.isEmpty() ? "?" : String.valueOf(whitePlayer.charAt(0)));
                ivWhiteAvatar.setText(display);
            } else {
                ivWhiteAvatar.setText("");
            }

            if (blackPlayer != null) {
                String display = blackPlayer.startsWith("电脑") ? "电" : (blackPlayer.isEmpty() ? "?" : String.valueOf(blackPlayer.charAt(0)));
                ivBlackAvatar.setText(display);
            } else {
                ivBlackAvatar.setText("");
            }
        } catch (Exception ignore) {
            // ignore avatar rendering issues
        }

        // 更新观战者信息（有观战者则显示，否则隐藏）
        java.util.List<String> spectators = game.getSpectators();
        if (spectators.isEmpty()) {
            tvSpectators.setVisibility(TextView.GONE);
            tvSpectators.setText("观战者: 无");
        } else {
            tvSpectators.setVisibility(TextView.VISIBLE);
            tvSpectators.setText("观战者: " + android.text.TextUtils.join(", ", spectators));
        }

        // 更新游戏状态
        if (game.isGameOver()) {
            tvGameStatus.setText(game.getGameResult());
        } else {
            // 如果玩家不足两人，显示等待玩家加入
            java.util.List<String> players = game.getPlayers();
            if (players.size() < 2) {
                tvGameStatus.setText("等待玩家加入");
            } else if (game.getCurrentPlayer() != null) {
                tvGameStatus.setText("当前回合: " + game.getCurrentPlayer());
            } else {
                tvGameStatus.setText("等待玩家加入");
            }
        }

        // 更新按钮状态
        if (isSpectator) {
            btnUndo.setEnabled(false);
            btnRestart.setEnabled(false);
        } else {
            // 始终隐藏/禁用悔棋按钮（多人模式下不支持）
            btnUndo.setVisibility(android.view.View.GONE);
            btnRestart.setEnabled(true);
        }
    }

    private void updateFromState(JSONObject gameState) {
        if (game != null) {
            game.setGameState(gameState);
            updateUI();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameManager != null && gameId != null) {
            gameManager.removeGameStateListener(gameId);

            if (game != null) {
                if (isSpectator) {
                    // 观战者退出 - 更新本地并广播状态让其他人知道
                    game.removeSpectator(username);
                    gameManager.broadcastGameState(gameId);
                } else {
                    // 玩家退出 - 通知 GameManager（会在服务器端/房间内广播并统一处理）
                    gameManager.quitGame(gameId, username);
                }
            }
        }
    }
}