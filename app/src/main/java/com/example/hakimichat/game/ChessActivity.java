package com.example.hakimichat.game;

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

public class ChessActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_IS_SPECTATOR = "is_spectator";
    public static final String EXTRA_IS_SINGLE_PLAYER = "is_single_player";

    private ChessBoardView boardView;
    private TextView tvGameStatus, tvWhitePlayer, tvBlackPlayer, tvSpectators;
    private Button btnUndo, btnRestart, btnExit;

    private String gameId;
    private String username;
    private boolean isSpectator;
    private boolean isSinglePlayer;
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
        isSinglePlayer = getIntent().getBooleanExtra(EXTRA_IS_SINGLE_PLAYER, false);

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
        tvSpectators = findViewById(R.id.tvSpectators);
        btnUndo = findViewById(R.id.btnUndo);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);

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
            if (isSpectator) {
                showToast("观战者不能悔棋");
                return;
            }
            if (!isSinglePlayer) {
                showToast("多人游戏不支持悔棋");
                return;
            }
            // 单机模式下悔棋
            boardView.undoMove();
            updateUI();
        });

        btnRestart.setOnClickListener(v -> {
            if (isSpectator) {
                showToast("观战者不能重新开始");
                return;
            }
            if (isSinglePlayer) {
                // 单机模式：只在本地重置
                boardView.restartGame();
                updateUI();
            } else {
                // 多人模式：发送重新开始请求
                if (gameManager.canRestartGame(gameId, username)) {
                    gameManager.restartGame(gameId);
                } else {
                    showToast("只有玩家可以重新开始游戏");
                }
            }
        });

        btnExit.setOnClickListener(v -> {
            finish();
        });
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

    private void performAIMove() {
        // 简单的AI实现：随机移动
        // 这里可以实现更复杂的AI逻辑
        // 暂时使用随机移动作为占位符
        try {
            // 获取所有可能的移动
            java.util.List<int[]> possibleMoves = getPossibleMoves();
            if (!possibleMoves.isEmpty()) {
                // 随机选择一个移动
                java.util.Random random = new java.util.Random();
                int[] move = possibleMoves.get(random.nextInt(possibleMoves.size()));

                JSONObject moveData = new JSONObject();
                moveData.put("fromRow", move[0]);
                moveData.put("fromCol", move[1]);
                moveData.put("toRow", move[2]);
                moveData.put("toCol", move[3]);

                gameManager.sendGameMove(gameId, game.getCurrentPlayer(), moveData);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private java.util.List<int[]> getPossibleMoves() {
        java.util.List<int[]> moves = new java.util.ArrayList<>();
        // 简化实现：返回一些可能的移动
        // 这里应该实现真正的国际象棋移动规则
        for (int fromRow = 0; fromRow < 8; fromRow++) {
            for (int fromCol = 0; fromCol < 8; fromCol++) {
                for (int toRow = 0; toRow < 8; toRow++) {
                    for (int toCol = 0; toCol < 8; toCol++) {
                        if (Math.abs(fromRow - toRow) <= 2 && Math.abs(fromCol - toCol) <= 2) {
                            moves.add(new int[]{fromRow, fromCol, toRow, toCol});
                        }
                    }
                }
            }
        }
        return moves;
    }

    private void updateUI() {
        if (game == null) return;

        // 同步棋盘状态到视图
        boardView.setBoardFromGame((ChessGame) game);

        // 更新玩家信息
        tvWhitePlayer.setText("白方: " + (game.getWhitePlayerName() != null ? game.getWhitePlayerName() : "等待中"));
        tvBlackPlayer.setText("黑方: " + (game.getBlackPlayerName() != null ? game.getBlackPlayerName() : "等待中"));

        // 更新观战者信息
        java.util.List<String> spectators = game.getSpectators();
        if (spectators.isEmpty()) {
            tvSpectators.setText("观战者: 无");
        } else {
            tvSpectators.setText("观战者: " + android.text.TextUtils.join(", ", spectators));
        }

        // 更新游戏状态
        if (game.isGameOver()) {
            tvGameStatus.setText(game.getGameResult());
        } else {
            String currentPlayer = game.getCurrentPlayer();
            if (currentPlayer != null) {
                tvGameStatus.setText("当前回合: " + currentPlayer);
            } else {
                tvGameStatus.setText("等待玩家加入");
            }
        }

        // 更新按钮状态
        if (isSpectator) {
            btnUndo.setEnabled(false);
            btnRestart.setEnabled(false);
        } else {
            btnUndo.setEnabled(isSinglePlayer);
            btnRestart.setEnabled(isSinglePlayer);
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
        }
    }
}