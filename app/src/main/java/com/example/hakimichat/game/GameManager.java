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
    
    /**
     * 游戏卡片更新监听器接口
     */
    public interface GameCardUpdateListener {
        void onGameCardUpdate(String gameId, int currentPlayers, int maxPlayers, boolean gameStarted, boolean gameEnded);
    }
    
    private GameCardUpdateListener gameCardUpdateListener;
    
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
     * 设置游戏卡片更新监听器
     */
    public void setGameCardUpdateListener(GameCardUpdateListener listener) {
        this.gameCardUpdateListener = listener;
    }
    
    /**
     * 通知游戏卡片更新
     */
    private void notifyGameCardUpdate(String gameId) {
        if (gameCardUpdateListener != null) {
            Game game = activeGames.get(gameId);
            if (game != null) {
                boolean gameStarted = false;
                boolean gameEnded = false;
                if (game instanceof BaseGame) {
                    gameStarted = ((BaseGame) game).isGameStarted();
                    gameEnded = ((BaseGame) game).isGameOver() && game.getPlayers().isEmpty();
                }
                gameCardUpdateListener.onGameCardUpdate(
                    gameId, 
                    game.getPlayers().size(), 
                    game.getMaxPlayers(),
                    gameStarted,
                    gameEnded
                );
            }
        }
    }
    
    /**
     * 发送游戏邀请
     */
    public void sendGameInvite(String gameType, String gameId, String invitedPlayer) {
        // 获取游戏实例以获取状态信息
        Game game = activeGames.get(gameId);
        if (game != null) {
            boolean gameStarted = false;
            if (game instanceof BaseGame) {
                gameStarted = ((BaseGame) game).isGameStarted();
            }
            
            Message message = Message.createGameInviteMessageWithState(
                currentUsername, 
                gameType, 
                gameId, 
                invitedPlayer,
                gameStarted,
                game.getPlayers().size(),
                game.getMaxPlayers(),
                game.getGameName()
            );
            sendMessage(message);
        } else {
            // 如果游戏实例不存在，使用默认方法
            Message message = Message.createGameInviteMessage(currentUsername, gameType, gameId, invitedPlayer);
            sendMessage(message);
        }
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
            
            // 通知游戏卡片更新
            notifyGameCardUpdate(gameId);
            
            // 立即更新本地界面
            GameStateListener listener = gameStateListeners.get(gameId);
            if (listener != null) {
                listener.onGameStateChanged(gameId, game.getGameState());
            }
            
            // 检查游戏是否结束
            if (game.isGameOver()) {
                Message endMessage = Message.createGameEndMessage(gameId, game.getGameResult());
                sendMessage(endMessage);
                
                // 游戏结束时也更新游戏卡片
                notifyGameCardUpdate(gameId);
                
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
            // 玩家加入成功，立即通知本地监听器
            GameStateListener listener = gameStateListeners.get(gameId);
            if (listener != null) {
                listener.onGameStateChanged(gameId, game.getGameState());
            }
            
            // 通知游戏卡片更新
            notifyGameCardUpdate(gameId);
            
            // 广播游戏状态给其他人
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
            
            // 如果本地没有游戏实例，创建一个（用于更新卡片）
            if (game == null) {
                String gameType = gameState.optString("gameType", "TicTacToe");
                game = createGameWithId(gameType, gameId);
            }
            
            if (game != null) {
                game.setGameState(gameState);
                
                // 通知监听器
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameStateChanged(gameId, gameState);
                }
                
                // 通知游戏卡片更新
                notifyGameCardUpdate(gameId);
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
     * 添加观战者（不广播游戏状态）
     */
    public void addSpectator(String gameId, String spectator) {
        Game game = activeGames.get(gameId);
        if (game != null) {
            game.addSpectator(spectator);
            
            // 观战者加入不应该触发游戏卡片更新，因为不影响游戏状态
            // 也不广播游戏状态，避免影响正在进行的游戏
        }
    }
    
    /**
     * 处理观战消息（其他人通知我有人观战）
     */
    public void handleSpectate(Message message) {
        String gameId = message.getGameId();
        String spectator = message.getSender();
        
        Game game = activeGames.get(gameId);
        if (game != null) {
            game.addSpectator(spectator);
            
            // 立即广播当前游戏状态给所有人（包括新加入的观战者）
            // 这样观战者就能看到当前的棋局
            broadcastGameState(gameId);
            
            // 通知本地监听器更新界面（更新观战者列表）
            GameStateListener listener = gameStateListeners.get(gameId);
            if (listener != null) {
                listener.onGameStateChanged(gameId, game.getGameState());
            }
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
        // 创建并发送退出消息
        Message quitMessage = Message.createGameQuitMessage(username, gameId);
        sendMessage(quitMessage);
        
        // 立即在本地也处理这个消息（因为broadcastMessage不会发送给发送者自己）
        handleGameQuit(quitMessage);
    }
    
    /**
     * 处理退出游戏消息（统一处理本地和远程的退出）
     */
    public void handleGameQuit(Message message) {
        String gameId = message.getGameId();
        String quitter = message.getSender();
        
        Game game = activeGames.get(gameId);
        if (game == null) {
            return;
        }
        
        boolean wasGameOver = game.isGameOver();
        boolean wasGameStarted = false;
        if (game instanceof BaseGame) {
            wasGameStarted = ((BaseGame) game).isGameStarted();
        }
        
        // 检查退出者是玩家还是观战者
        boolean isPlayer = game.getPlayers().contains(quitter);
        boolean isSpectator = game.getSpectators().contains(quitter);
        
        // 检查是否是发起者（第一个玩家）退出
        boolean isCreator = isPlayer && game.getPlayers().size() > 0 && 
                           game.getPlayers().get(0).equals(quitter);
        
        if (isPlayer) {
            // 玩家退出
            game.removePlayer(quitter);
            
            if (isCreator) {
                // 发起者退出，结束游戏并通知所有人
                if (game instanceof BaseGame) {
                    ((BaseGame) game).isGameOver = true;
                    ((BaseGame) game).gameResult = "游戏已结束（发起者退出）";
                }
                
                // 通知本地监听器（如果在游戏界面的人看到结束消息）
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameEnded(gameId, "游戏已结束（发起者退出）");
                }
                
                // 广播游戏结束消息给所有人
                Message endMessage = Message.createGameEndMessage(gameId, "游戏已结束（发起者退出）");
                sendMessage(endMessage);
                
                // 广播游戏状态，让所有人（包括没进入游戏的人）都能更新卡片
                broadcastGameState(gameId);
                
                // 通知本地游戏卡片更新为已结束状态
                if (gameCardUpdateListener != null) {
                    gameCardUpdateListener.onGameCardUpdate(gameId, 0, game.getMaxPlayers(), false, true);
                }
                
                // 保留游戏作为历史记录，不移除
                // removeGame(gameId);
            } else if (wasGameStarted && !wasGameOver && game.getPlayers().size() == 1) {
                // 非发起者退出，游戏已开始但未结束
                // 重置游戏让发起者可以继续等待其他人
                game.reset();
                
                // 通知监听器游戏已重置
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameEnded(gameId, quitter + " 已退出，等待新玩家加入");
                    listener.onGameStateChanged(gameId, game.getGameState());
                }
                
                // 通知游戏卡片更新
                notifyGameCardUpdate(gameId);
                
                // 广播游戏状态
                broadcastGameState(gameId);
            } else if (wasGameStarted && !wasGameOver && game.getPlayers().size() == 0) {
                // 所有玩家都退出了，移除游戏
                removeGame(gameId);
            } else if (wasGameStarted && game.getPlayers().size() == 1) {
                // 游戏已开始，有玩家退出，重置游戏
                game.reset();
                
                // 通知监听器
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameStateChanged(gameId, game.getGameState());
                }
                
                // 广播游戏状态
                broadcastGameState(gameId);
            } else if (wasGameOver && game.getPlayers().size() >= 1) {
                // 游戏已结束，玩家退出，通知剩余玩家隐藏"再来一局"按钮
                GameStateListener listener = gameStateListeners.get(gameId);
                if (listener != null) {
                    listener.onGameEnded(gameId, quitter + " 已离开游戏");
                }
                
                // 广播游戏状态
                broadcastGameState(gameId);
            } else {
                // 其他情况，广播游戏状态
                broadcastGameState(gameId);
            }
        } else if (isSpectator) {
            // 观战者退出，只需移除并更新状态
            game.removeSpectator(quitter);
            broadcastGameState(gameId);
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
