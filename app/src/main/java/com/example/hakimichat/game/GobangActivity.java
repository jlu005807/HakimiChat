package com.example.hakimichat.game;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
    private Button btnUndo, btnRestart, btnExit;

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
                mainHandler.post(() -> updateFromState(gameState));
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

    private void initViews() {
        boardView = findViewById(R.id.gobangBoard);
        tvGameStatus = findViewById(R.id.tvGameStatus);
        tvBlackPlayer = findViewById(R.id.tvBlackPlayer);
        tvWhitePlayer = findViewById(R.id.tvWhitePlayer);
        tvSpectators = findViewById(R.id.tvSpectators);
        btnUndo = findViewById(R.id.btnUndo);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
    }

    private void setupListeners() {
        // 棋盘点击监听
        boardView.setOnBoardClickListener((x, y) -> onCellClick(x, y));

        // 悔棋按钮（仅单机模式可见）
        btnUndo.setOnClickListener(v -> {
            if (game != null && game.isAiEnabled()) {
                if (game.undoMove()) {
                    updateUI();
                    showToast("已悔棋");
                } else {
                    showToast("无法悔棋");
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

        // 更新游戏状态
        if (game.isGameOver()) {
            tvGameStatus.setText(game.getGameResult());
        } else if (game.getCurrentPlayer() != null) {
            String piece = game.getCurrentPlayer().equals(blackPlayer) ? "黑子" : "白子";
            tvGameStatus.setText("当前回合: " + game.getCurrentPlayer() + " (" + piece + ")");
        } else {
            tvGameStatus.setText("等待玩家加入...");
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
