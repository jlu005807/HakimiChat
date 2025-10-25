package com.example.hakimichat.game;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/**
 * 游戏基类 - 提供通用的游戏功能实现
 */
public abstract class BaseGame implements Game {
    
    protected String gameId;
    protected List<String> players;
    protected List<String> spectators;
    protected boolean isGameStarted;
    protected boolean isGameOver;
    protected String currentPlayer;
    protected String gameResult;
    // 存储本局每位玩家/观战者最后一次发送的表情（局内有效，局重置时清空）
    protected Map<String, String> lastEmojiMap;
    
    public BaseGame() {
        this.gameId = UUID.randomUUID().toString();
        this.players = new ArrayList<>();
        this.spectators = new ArrayList<>();
        this.isGameStarted = false;
        this.isGameOver = false;
        this.lastEmojiMap = new HashMap<>();
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
    
    @Override
    public boolean addPlayer(String player) {
        if (players.size() >= getMaxPlayers()) {
            return false;
        }
        if (!players.contains(player)) {
            players.add(player);
            return true;
        }
        return false;
    }
    
    @Override
    public void removePlayer(String player) {
        players.remove(player);
    }
    
    @Override
    public List<String> getPlayers() {
        return new ArrayList<>(players);
    }
    
    @Override
    public void addSpectator(String spectator) {
        if (!spectators.contains(spectator) && !players.contains(spectator)) {
            spectators.add(spectator);
        }
    }
    
    @Override
    public void removeSpectator(String spectator) {
        spectators.remove(spectator);
    }
    
    @Override
    public List<String> getSpectators() {
        return new ArrayList<>(spectators);
    }
    
    @Override
    public boolean canStart() {
        return players.size() >= getMinPlayers() && players.size() <= getMaxPlayers();
    }
    
    @Override
    public boolean isGameOver() {
        return isGameOver;
    }
    
    /**
     * 检查游戏是否已开始
     */
    public boolean isGameStarted() {
        return isGameStarted;
    }
    
    @Override
    public String getGameResult() {
        return gameResult;
    }
    
    @Override
    public String getCurrentPlayer() {
        return currentPlayer;
    }
    
    /**
     * 将玩家列表转换为JSON数组
     */
    protected JSONArray playersToJson() {
        JSONArray jsonArray = new JSONArray();
        for (String player : players) {
            jsonArray.put(player);
        }
        return jsonArray;
    }
    
    /**
     * 从JSON数组恢复玩家列表
     */
    protected void playersFromJson(JSONArray jsonArray) {
        players.clear();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                players.add(jsonArray.getString(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 将观战者列表转换为JSON数组
     */
    protected JSONArray spectatorsToJson() {
        JSONArray jsonArray = new JSONArray();
        for (String spectator : spectators) {
            jsonArray.put(spectator);
        }
        return jsonArray;
    }
    
    /**
     * 从JSON数组恢复观战者列表
     */
    protected void spectatorsFromJson(JSONArray jsonArray) {
        spectators.clear();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                spectators.add(jsonArray.getString(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void reset() {
        isGameStarted = false;
        isGameOver = false;
        gameResult = null;
        currentPlayer = null;
        // 重置本局表情缓存
        if (lastEmojiMap != null) lastEmojiMap.clear();
    }

    /**
     * 设置玩家/观战者在本局发送的最后表情
     */
    public void setLastEmoji(String user, String emoji) {
        if (lastEmojiMap == null) lastEmojiMap = new HashMap<>();
        lastEmojiMap.put(user, emoji);
    }

    /**
     * 获取玩家/观战者在本局发送的最后表情
     */
    public String getLastEmoji(String user) {
        if (lastEmojiMap == null) return null;
        return lastEmojiMap.get(user);
    }
}
