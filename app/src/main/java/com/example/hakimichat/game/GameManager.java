package com.example.hakimichat.game;

import com.example.hakimichat.Message;
import com.example.hakimichat.ServerManager;
import com.example.hakimichat.ClientManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏管理器 - 单例模式
 * 负责管理所有游戏会话、玩家匹配、消息转发等
 */
public class GameManager {
    
    private static GameManager instance;
    private Map<String, Game> activeGames;  // 活跃的游戏会话
    private Map<String, GameStateListener> gameStateListeners;  // 游戏状态监听器
    private ServerManager serverManager;
    private ClientManager clientManager;
    private String currentUsername;
    private boolean isHost;
    
    /**
     * 游戏状态监听器接口
     */
    public interface GameStateListener {
        void onGameStateChanged(String gameId, JSONObject gameState);
        void onGameEnded(String gameId, String result);
    }
    
    private GameManager() {
        activeGames = new HashMap<>();
        gameStateListeners = new HashMap<>();
    }
    
    public static synchronized GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }
    
    /**
     * 初始化GameManager
     */
    public void init(String username, boolean isHost, ServerManager serverManager, ClientManager clientManager) {
        this.currentUsername = username;
        this.isHost = isHost;
        this.serverManager = serverManager;
        this.clientManager = clientManager;
    }
    
    /**
     * 创建新游戏
     */
    public Game createGame(String gameType) {
        Game game = null;
        
        if ("TicTacToe".equals(gameType)) {
            game = new TicTacToeGame();
        }
        // 未来可以在这里添加更多游戏类型
        
        if (game != null) {
            activeGames.put(((BaseGame)game).getGameId(), game);
        }
        
        return game;
    }
    
    /**
     * 根据指定的 gameId 创建游戏
     */
    public Game createGameWithId(String gameType, String gameId) {
        Game game = null;
        
        if ("TicTacToe".equals(gameType)) {
            game = new TicTacToeGame();
        }
        // 未来可以在这里添加更多游戏类型
        
        if (game != null) {
            ((BaseGame)game).setGameId(gameId);
            activeGames.put(gameId, game);
        }
        
        return game;
    }
    
    /**
     * 获取游戏
     */
    public Game getGame(String gameId) {
        return activeGames.get(gameId);
    }
    
    /**
     * 移除游戏
     */
    public void removeGame(String gameId) {
        activeGames.remove(gameId);
        gameStateListeners.remove(gameId);
    }
    
    /**
     * 设置游戏状态监听器
     */
    public void setGameStateListener(String gameId, GameStateListener listener) {
        gameStateListeners.put(gameId, listener);
    }
    
    /**
     * 移除游戏状态监听器
     */
    public void removeGameStateListener(String gameId) {
        gameStateListeners.remove(gameId);
    }
    
    /**
     * 发送游戏邀请
     */
    public void sendGameInvite(String gameType, String gameId, String invitedPlayer) {
        Message message = Message.createGameInviteMessage(currentUsername, gameType, gameId, invitedPlayer);
        sendMessage(message);
    }
    
    /**
     * 接受游戏邀请
     */
    public void acceptGameInvite(String gameId) {
        Message message = Message.createGameJoinMessage(currentUsername, gameId);
        sendMessage(message);
    }
    
    /**
     * 发送游戏移动
     */
    public void sendGameMove(String gameId, String player, JSONObject moveData) {
        Game game = activeGames.get(gameId);
        if (game != null && game.processMove(player, moveData)) {
            // 移动成功，广播游戏状态
            broadcastGameState(gameId);
            
            // 立即更新本地界面
            GameStateListener listener = gameStateListeners.get(gameId);
            if (listener != null) {
                listener.onGameStateChanged(gameId, game.getGameState());
            }
            
            // 检查游戏是否结束
            if (game.isGameOver()) {
                Message endMessage = Message.createGameEndMessage(gameId, game.getGameResult());
                sendMessage(endMessage);
                
                // 通知监听器
                if (listener != null) {
                    listener.onGameEnded(gameId, game.getGameResult());
                }
            }
        }
    }
    
    /**
     * 广播游戏状态
     */
    public void broadcastGameState(String gameId) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            JSONObject gameState = game.getGameState();
            Message message = Message.createGameStateMessage(gameId, gameState.toString());
            sendMessage(message);
        }
    }
    
    /**
     * 处理收到的游戏邀请
     */
    public void handleGameInvite(Message message) {
        // 由RoomActivity处理，显示对话框让用户选择
    }
    
    /**
     * 处理加入游戏请求
     */
    public void handleGameJoin(Message message) {
        String gameId = message.getGameId();
        String player = message.getSender();
        
        Game game = activeGames.get(gameId);
        if (game != null && game.addPlayer(player)) {
            // 玩家加入成功，广播游戏状态
            broadcastGameState(gameId);
        }
    }
    
    /**
     * 处理游戏移动
     */
    public void handleGameMove(Message message) {
        String gameId = message.getGameId();
        String player = message.getSender();
        
        try {
            JSONObject moveData = new JSONObject(message.getGameData());
            sendGameMove(gameId, player, moveData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理游戏状态更新
     */
    public void handleGameState(Message message) {
        String gameId = message.getGameId();
        
        try {
            JSONObject gameState = new JSONObject(message.getGameData());
            Game game = activeGames.get(gameId);
            
            if (game != null) {
                game.setGameState(gameState);
                
                // 通知监听器
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameStateChanged(gameId, gameState);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 处理游戏结束
     */
    public void handleGameEnd(Message message) {
        String gameId = message.getGameId();
        String result = message.getGameData();
        
        // 通知监听器
        GameStateListener listener = gameStateListeners.get(gameId);
        if (listener != null) {
            listener.onGameEnded(gameId, result);
        }
    }
    
    /**
     * 添加观战者
     */
    public void addSpectator(String gameId, String spectator) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            game.addSpectator(spectator);
            broadcastGameState(gameId);
        }
    }
    
    /**
     * 重新开始游戏
     */
    public void restartGame(String gameId) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            game.reset();
            
            // 发送再来一局消息
            Message restartMessage = Message.createGameRestartMessage(currentUsername, gameId);
            sendMessage(restartMessage);
            
            // 广播游戏状态
            broadcastGameState(gameId);
            
            // 立即更新本地界面
            GameStateListener listener = gameStateListeners.get(gameId);
            if (listener != null) {
                listener.onGameStateChanged(gameId, game.getGameState());
            }
        }
    }
    
    /**
     * 退出游戏
     */
    public void quitGame(String gameId, String username) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            // 发送退出消息
            Message quitMessage = Message.createGameQuitMessage(username, gameId);
            sendMessage(quitMessage);
            
            // 从游戏中移除自己
            game.removePlayer(username);
            
            // 检查是否还有玩家
            if (game.getPlayers().isEmpty()) {
                // 没有玩家了，移除游戏
                removeGame(gameId);
            }
        }
    }
    
    /**
     * 处理退出游戏消息
     */
    public void handleGameQuit(Message message) {
        String gameId = message.getGameId();
        String quitter = message.getSender();
        
        Game game = activeGames.get(gameId);
        if (game != null) {
            boolean wasGameOver = game.isGameOver();
            
            // 移除退出的玩家
            game.removePlayer(quitter);
            
            // 如果游戏进行中，玩家退出，判定剩余玩家获胜
            if (!wasGameOver && game.getPlayers().size() == 1) {
                String winner = game.getPlayers().get(0);
                String result = winner + " 获胜（对方已退出）";
                
                // 通知监听器游戏结束
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameEnded(gameId, result);
                }
                
                // 广播游戏状态
                broadcastGameState(gameId);
            } else if (wasGameOver && game.getPlayers().size() >= 1) {
                // 游戏已结束，玩家退出，通知剩余玩家隐藏"再来一局"按钮
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    // 使用特殊的结果字符串来标识这是游戏结束后的退出
                    listener.onGameEnded(gameId, quitter + " 已离开游戏");
                }
                
                // 广播游戏状态
                broadcastGameState(gameId);
            } else {
                // 观战者退出或其他情况，只需更新状态
                broadcastGameState(gameId);
            }
        }
    }
    
    /**
     * 处理再来一局消息
     */
    public void handleGameRestart(Message message) {
        String gameId = message.getGameId();
        
        Game game = activeGames.get(gameId);
        if (game != null) {
            game.reset();
            
            // 通知监听器
            GameStateListener listener = gameStateListeners.get(gameId);
            if (listener != null) {
                listener.onGameStateChanged(gameId, game.getGameState());
            }
        }
    }
    
    /**
     * 检查是否可以重新开始游戏
     */
    public boolean canRestartGame(String gameId, String player) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            return game.getPlayers().contains(player);
        }
        return false;
    }
    
    /**
     * 发送消息（通过ServerManager或ClientManager）
     */
    private void sendMessage(Message message) {
        if (isHost && serverManager != null) {
            serverManager.broadcastMessage(message);
        } else if (clientManager != null) {
            clientManager.sendMessage(message);
        }
    }
    
    /**
     * 清理所有游戏
     */
    public void cleanup() {
        activeGames.clear();
        gameStateListeners.clear();
    }
}
