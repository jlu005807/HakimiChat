package com.example.hakimichat.game;

import com.example.hakimichat.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 井字棋游戏逻辑
 */
public class TicTacToeGame extends BaseGame {
    
    private static final String GAME_TYPE = "TicTacToe";
    private static final String EMPTY = "";
    private static final String PLAYER_X = "X";
    private static final String PLAYER_O = "O";
    
    private String[][] board;  // 3x3 棋盘
    private String playerXName;
    private String playerOName;
    private int moveCount;
    
    public TicTacToeGame() {
        super();
        board = new String[3][3];
        initGame();
    }
    
    @Override
    public void initGame() {
        // 初始化棋盘
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = EMPTY;
            }
        }
        moveCount = 0;
        isGameStarted = false;
        isGameOver = false;
        gameResult = null;
    }
    
    @Override
    public String getGameType() {
        return GAME_TYPE;
    }
    
    @Override
    public String getGameName() {
        return "井字棋";
    }
    
    @Override
    public String getGameDescription() {
        return "经典双人对战游戏，连成三子获胜";
    }
    
    @Override
    public int getGameIcon() {
        return R.drawable.ic_tictactoe;
    }
    
    @Override
    public int getMaxPlayers() {
        return 2;
    }
    
    @Override
    public int getMinPlayers() {
        return 2;
    }
    
    @Override
    public boolean addPlayer(String player) {
        if (super.addPlayer(player)) {
            if (players.size() == 1) {
                playerXName = player;
                currentPlayer = playerXName;
            } else if (players.size() == 2) {
                playerOName = player;
                currentPlayer = playerXName;  // 设置当前玩家为X（第一个玩家）
                isGameStarted = true;
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean processMove(String player, JSONObject moveData) {
        if (isGameOver || !isGameStarted) {
            return false;
        }
        
        // 检查是否轮到该玩家
        if (!player.equals(currentPlayer)) {
            return false;
        }
        
        try {
            int row = moveData.getInt("row");
            int col = moveData.getInt("col");
            
            // 检查位置是否有效
            if (row < 0 || row >= 3 || col < 0 || col >= 3) {
                return false;
            }
            
            // 检查位置是否为空
            if (!board[row][col].equals(EMPTY)) {
                return false;
            }
            
            // 放置棋子
            String piece = player.equals(playerXName) ? PLAYER_X : PLAYER_O;
            board[row][col] = piece;
            moveCount++;
            
            // 检查游戏是否结束
            if (checkWin(piece)) {
                isGameOver = true;
                gameResult = player + " 获胜！";
            } else if (moveCount >= 9) {
                isGameOver = true;
                gameResult = "平局！";
            } else {
                // 切换玩家
                currentPlayer = player.equals(playerXName) ? playerOName : playerXName;
            }
            
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 检查是否获胜
     */
    private boolean checkWin(String piece) {
        // 检查行
        for (int i = 0; i < 3; i++) {
            if (board[i][0].equals(piece) && 
                board[i][1].equals(piece) && 
                board[i][2].equals(piece)) {
                return true;
            }
        }
        
        // 检查列
        for (int i = 0; i < 3; i++) {
            if (board[0][i].equals(piece) && 
                board[1][i].equals(piece) && 
                board[2][i].equals(piece)) {
                return true;
            }
        }
        
        // 检查对角线
        if (board[0][0].equals(piece) && 
            board[1][1].equals(piece) && 
            board[2][2].equals(piece)) {
            return true;
        }
        
        if (board[0][2].equals(piece) && 
            board[1][1].equals(piece) && 
            board[2][0].equals(piece)) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public JSONObject getGameState() {
        try {
            JSONObject state = new JSONObject();
            state.put("gameId", gameId);
            state.put("gameType", GAME_TYPE);
            state.put("isGameStarted", isGameStarted);
            state.put("isGameOver", isGameOver);
            state.put("currentPlayer", currentPlayer);
            state.put("gameResult", gameResult);
            state.put("moveCount", moveCount);
            state.put("playerXName", playerXName);
            state.put("playerOName", playerOName);
            state.put("players", playersToJson());
            state.put("spectators", spectatorsToJson());
            
            // 保存棋盘状态
            JSONArray boardArray = new JSONArray();
            for (int i = 0; i < 3; i++) {
                JSONArray row = new JSONArray();
                for (int j = 0; j < 3; j++) {
                    row.put(board[i][j]);
                }
                boardArray.put(row);
            }
            state.put("board", boardArray);
            
            return state;
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }
    
    @Override
    public void setGameState(JSONObject state) {
        try {
            gameId = state.optString("gameId", gameId);
            isGameStarted = state.optBoolean("isGameStarted", false);
            isGameOver = state.optBoolean("isGameOver", false);
            currentPlayer = state.optString("currentPlayer", null);
            gameResult = state.optString("gameResult", null);
            moveCount = state.optInt("moveCount", 0);
            playerXName = state.optString("playerXName", null);
            playerOName = state.optString("playerOName", null);
            
            // 恢复玩家列表
            if (state.has("players")) {
                playersFromJson(state.getJSONArray("players"));
            }
            
            // 恢复观战者列表
            if (state.has("spectators")) {
                spectatorsFromJson(state.getJSONArray("spectators"));
            }
            
            // 恢复棋盘状态
            if (state.has("board")) {
                JSONArray boardArray = state.getJSONArray("board");
                for (int i = 0; i < 3; i++) {
                    JSONArray row = boardArray.getJSONArray(i);
                    for (int j = 0; j < 3; j++) {
                        board[i][j] = row.getString(j);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        initGame();
        // 保留玩家但重置游戏状态
        if (players.size() >= 2) {
            playerXName = players.get(0);
            playerOName = players.get(1);
            currentPlayer = playerXName;
            isGameStarted = true;
        } else if (players.size() == 1) {
            // 只有一个玩家（发起者），重置为等待状态
            playerXName = players.get(0);
            playerOName = null;
            currentPlayer = null;
            isGameStarted = false;
        }
    }
    
    /**
     * 获取棋盘上的棋子
     */
    public String getPiece(int row, int col) {
        if (row >= 0 && row < 3 && col >= 0 && col < 3) {
            return board[row][col];
        }
        return EMPTY;
    }
    
    /**
     * 获取玩家的棋子类型
     */
    public String getPlayerPiece(String player) {
        if (player.equals(playerXName)) {
            return PLAYER_X;
        } else if (player.equals(playerOName)) {
            return PLAYER_O;
        }
        return EMPTY;
    }
}
