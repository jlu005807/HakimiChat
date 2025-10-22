package com.example.hakimichat.game;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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
    
    private ImageButton[][] buttons = new ImageButton[3][3];
    private TextView tvGameStatus, tvPlayerX, tvPlayerO, tvSpectators;
    private Button btnRestart, btnExit;
    
    private String gameId;
    private String username;
    private boolean isSpectator;
    private TicTacToeGame game;
    private GameManager gameManager;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tictactoe);
        
        // 获取传入参数
        gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        username = getIntent().getStringExtra(EXTRA_USERNAME);
        isSpectator = getIntent().getBooleanExtra(EXTRA_IS_SPECTATOR, false);
        
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
                    // 如果是对方退出导致的结束，不显示重新开始按钮
                    else if (!isSpectator && !result.contains("已退出")) {
                        btnRestart.setVisibility(Button.VISIBLE);
                    }
                });
            }
        });
    }
    
    private void initViews() {
        tvGameStatus = findViewById(R.id.tvGameStatus);
        tvPlayerX = findViewById(R.id.tvPlayerX);
        tvPlayerO = findViewById(R.id.tvPlayerO);
        tvSpectators = findViewById(R.id.tvSpectators);
        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
        
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
            if (gameManager.canRestartGame(gameId, username)) {
                gameManager.restartGame(gameId);
                btnRestart.setVisibility(Button.GONE);
            } else {
                showToast("只有玩家可以重新开始游戏");
            }
        });
        
        // 退出按钮
        btnExit.setOnClickListener(v -> finish());
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
        } catch (JSONException e) {
            e.printStackTrace();
            showToast("操作失败");
        }
    }
    
    private void updateUI() {
        // 更新玩家信息
        java.util.List<String> players = game.getPlayers();
        if (players.size() >= 1) {
            tvPlayerX.setText("玩家X: " + players.get(0));
        }
        if (players.size() >= 2) {
            tvPlayerO.setText("玩家O: " + players.get(1));
        }
        
        // 更新游戏状态
        if (game.isGameOver()) {
            tvGameStatus.setText(game.getGameResult());
        } else if (game.getCurrentPlayer() != null) {
            String currentPiece = game.getPlayerPiece(game.getCurrentPlayer());
            tvGameStatus.setText("当前回合: " + game.getCurrentPlayer() + " (" + currentPiece + ")");
        } else {
            tvGameStatus.setText("等待玩家加入...");
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
                    // 观战者退出，只移除观战者，不发送退出消息
                    game.removeSpectator(username);
                } else {
                    // 玩家退出游戏，通知其他人
                    // 无论游戏是否结束都要通知，因为游戏结束后对方可能在等待再来一局
                    gameManager.quitGame(gameId, username);
                }
            }
        }
    }
}
