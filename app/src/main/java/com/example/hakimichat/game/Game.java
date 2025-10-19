package com.example.hakimichat.game;

import org.json.JSONObject;
import java.util.List;

/**
 * 游戏接口 - 定义所有游戏必须实现的方法
 */
public interface Game {
    
    /**
     * 获取游戏类型名称
     */
    String getGameType();
    
    /**
     * 获取游戏显示名称
     */
    String getGameName();
    
    /**
     * 获取游戏描述
     */
    String getGameDescription();
    
    /**
     * 获取游戏图标资源ID
     */
    int getGameIcon();
    
    /**
     * 获取最大玩家数量
     */
    int getMaxPlayers();
    
    /**
     * 获取最小玩家数量
     */
    int getMinPlayers();
    
    /**
     * 初始化游戏
     */
    void initGame();
    
    /**
     * 处理玩家移动/操作
     * @param player 玩家昵称
     * @param moveData 移动数据
     * @return 操作是否成功
     */
    boolean processMove(String player, JSONObject moveData);
    
    /**
     * 获取当前游戏状态的JSON表示
     */
    JSONObject getGameState();
    
    /**
     * 从JSON恢复游戏状态
     */
    void setGameState(JSONObject state);
    
    /**
     * 检查游戏是否结束
     */
    boolean isGameOver();
    
    /**
     * 获取游戏结果描述
     */
    String getGameResult();
    
    /**
     * 获取当前轮到的玩家
     */
    String getCurrentPlayer();
    
    /**
     * 添加玩家到游戏
     */
    boolean addPlayer(String player);
    
    /**
     * 移除玩家
     */
    void removePlayer(String player);
    
    /**
     * 获取所有玩家列表
     */
    List<String> getPlayers();
    
    /**
     * 添加观战者
     */
    void addSpectator(String spectator);
    
    /**
     * 移除观战者
     */
    void removeSpectator(String spectator);
    
    /**
     * 获取观战者列表
     */
    List<String> getSpectators();
    
    /**
     * 检查游戏是否可以开始
     */
    boolean canStart();
    
    /**
     * 重置游戏
     */
    void reset();
}
