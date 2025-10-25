package com.example.hakimichat;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 消息类型常量
    public static final int TYPE_NORMAL = 0;         // 普通消息
    public static final int TYPE_USER_COUNT = 1;     // 在线人数更新
    public static final int TYPE_NICKNAME_CHECK = 2; // 昵称检查
    public static final int TYPE_NICKNAME_RESULT = 3;// 昵称验证结果
    public static final int TYPE_MEMBER_LIST = 4;    // 成员列表
    public static final int TYPE_KICK = 5;           // 踢人消息
    public static final int TYPE_HISTORY = 6;        // 历史消息

    // 游戏相关消息类型常量
    public static final int TYPE_GAME_INVITE = 7;    // 游戏邀请
    public static final int TYPE_GAME_JOIN = 8;      // 加入游戏
    public static final int TYPE_GAME_MOVE = 9;      // 游戏移动/操作
    public static final int TYPE_GAME_STATE = 10;    // 游戏状态同步
    public static final int TYPE_GAME_END = 11;      // 游戏结束
    public static final int TYPE_GAME_SPECTATE = 12; // 观战请求
    public static final int TYPE_GAME_QUIT = 13;     // 退出游戏
    public static final int TYPE_GAME_RESTART = 14;  // 再来一局
    public static final int TYPE_GAME_EMOJI = 15;    // 游戏表情
    
    private String sender;
    private String content;
    private long timestamp;
    private boolean isSentByMe;
    private int messageType;     // 消息类型
    private int userCount;       // 在线人数
    private String validatedNickname; // 验证后的昵称
    private boolean isHost;      // 是否是房主发送的消息
    private String targetNickname; // 踢人目标昵称（仅TYPE_KICK用）
    
    // 游戏相关字段
    private String gameId;       // 游戏会话ID
    private String gameType;     // 游戏类型 (如 "TicTacToe")
    private String gameData;     // 游戏数据 (JSON格式)
    private String invitedPlayer; // 被邀请的玩家
    private java.util.List<String> players; // 游戏参与玩家列表
    private java.util.List<String> spectators; // 观战者列表
    private boolean gameStarted; // 游戏是否已开始
    private int currentPlayerCount; // 当前玩家数量
    private int maxPlayerCount; // 最大玩家数量
    private String gameName; // 游戏名称（用于显示）
    private boolean gameEnded; // 游戏是否已结束（房主退出）

    public Message(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.isSentByMe = false;
        this.messageType = TYPE_NORMAL;
        this.userCount = 0;
        this.isHost = false;
    }
    
    /**
     * 创建在线人数更新消息
     */
    public static Message createUserCountMessage(int userCount) {
        Message message = new Message("系统", "在线人数更新");
        message.messageType = TYPE_USER_COUNT;
        message.userCount = userCount;
        return message;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSentByMe() {
        return isSentByMe;
    }

    public void setSentByMe(boolean sentByMe) {
        isSentByMe = sentByMe;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public int getUserCount() {
        return userCount;
    }

    public void setUserCount(int userCount) {
        this.userCount = userCount;
    }

    public String getValidatedNickname() {
        return validatedNickname;
    }

    public void setValidatedNickname(String validatedNickname) {
        this.validatedNickname = validatedNickname;
    }

    public boolean isHost() {
        return isHost;
    }

    public void setHost(boolean host) {
        isHost = host;
    }
    
    /**
     * 创建昵称检查消息
     */
    public static Message createNicknameCheckMessage(String nickname) {
        Message message = new Message(nickname, "昵称检查");
        message.messageType = TYPE_NICKNAME_CHECK;
        return message;
    }
    
    /**
     * 创建昵称验证结果消息
     */
    public static Message createNicknameResultMessage(String validatedNickname) {
        Message message = new Message("系统", "昵称验证");
        message.messageType = TYPE_NICKNAME_RESULT;
        message.validatedNickname = validatedNickname;
        return message;
    }
    
    /**
     * 创建成员列表消息
     */
    public static Message createMemberListMessage(String[] members) {
        Message message = new Message("系统", "成员列表");
        message.messageType = TYPE_MEMBER_LIST;
        message.content = String.join(",", members);
        return message;
    }

    /**
     * 创建踢人消息
     */
    public static Message createKickMessage(String targetNickname) {
        Message message = new Message("系统", "踢出成员");
        message.messageType = TYPE_KICK;
        message.targetNickname = targetNickname;
        return message;
    }

    public String getTargetNickname() {
        return targetNickname;
    }

    public void setTargetNickname(String targetNickname) {
        this.targetNickname = targetNickname;
    }
    
    // 游戏相关的getter和setter
    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getGameType() {
        return gameType;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getGameData() {
        return gameData;
    }

    public void setGameData(String gameData) {
        this.gameData = gameData;
    }

    public String getInvitedPlayer() {
        return invitedPlayer;
    }

    public void setInvitedPlayer(String invitedPlayer) {
        this.invitedPlayer = invitedPlayer;
    }

    public java.util.List<String> getPlayers() {
        return players;
    }

    public void setPlayers(java.util.List<String> players) {
        this.players = players;
    }

    public java.util.List<String> getSpectators() {
        return spectators;
    }

    public void setSpectators(java.util.List<String> spectators) {
        this.spectators = spectators;
    }
    
    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public int getCurrentPlayerCount() {
        return currentPlayerCount;
    }

    public void setCurrentPlayerCount(int currentPlayerCount) {
        this.currentPlayerCount = currentPlayerCount;
    }

    public int getMaxPlayerCount() {
        return maxPlayerCount;
    }

    public void setMaxPlayerCount(int maxPlayerCount) {
        this.maxPlayerCount = maxPlayerCount;
    }

    public String getGameName() {
        return gameName;
    }

    public void setGameName(String gameName) {
        this.gameName = gameName;
    }
    
    public boolean isGameEnded() {
        return gameEnded;
    }

    public void setGameEnded(boolean gameEnded) {
        this.gameEnded = gameEnded;
    }
    
    /**
     * 创建游戏邀请消息
     */
    public static Message createGameInviteMessage(String sender, String gameType, String gameId, String invitedPlayer) {
        Message message = new Message(sender, "邀请你一起玩" + gameType);
        message.messageType = TYPE_GAME_INVITE;
        message.gameType = gameType;
        message.gameId = gameId;
        message.invitedPlayer = invitedPlayer;
        
        // 设置游戏名称（可以根据gameType设置中文名称）
        message.gameName = "TicTacToe".equals(gameType) ? "井字棋" : gameType;
        
        // 初始化游戏状态
        message.gameStarted = false;
        message.currentPlayerCount = 1; // 创建者已经加入
        message.maxPlayerCount = 2; // 默认井字棋最多2人
        
        return message;
    }
    
    /**
     * 创建游戏邀请消息（带游戏状态）
     */
    public static Message createGameInviteMessageWithState(String sender, String gameType, String gameId, 
                                                           String invitedPlayer, boolean gameStarted, 
                                                           int currentPlayerCount, int maxPlayerCount, String gameName) {
        Message message = new Message(sender, "邀请你一起玩" + gameName);
        message.messageType = TYPE_GAME_INVITE;
        message.gameType = gameType;
        message.gameId = gameId;
        message.invitedPlayer = invitedPlayer;
        message.gameName = gameName;
        message.gameStarted = gameStarted;
        message.currentPlayerCount = currentPlayerCount;
        message.maxPlayerCount = maxPlayerCount;
        return message;
    }
    
    /**
     * 创建加入游戏消息
     */
    public static Message createGameJoinMessage(String sender, String gameId) {
        Message message = new Message(sender, "加入游戏");
        message.messageType = TYPE_GAME_JOIN;
        message.gameId = gameId;
        return message;
    }
    
    /**
     * 创建游戏移动消息
     */
    public static Message createGameMoveMessage(String sender, String gameId, String moveData) {
        Message message = new Message(sender, "游戏操作");
        message.messageType = TYPE_GAME_MOVE;
        message.gameId = gameId;
        message.gameData = moveData;
        return message;
    }
    
    /**
     * 创建游戏状态同步消息
     */
    public static Message createGameStateMessage(String gameId, String gameData) {
        Message message = new Message("系统", "游戏状态");
        message.messageType = TYPE_GAME_STATE;
        message.gameId = gameId;
        message.gameData = gameData;
        return message;
    }
    
    /**
     * 创建游戏结束消息
     */
    public static Message createGameEndMessage(String gameId, String result) {
        Message message = new Message("系统", result);
        message.messageType = TYPE_GAME_END;
        message.gameId = gameId;
        message.gameData = result;
        return message;
    }
    
    /**
     * 创建观战请求消息
     */
    public static Message createGameSpectateMessage(String sender, String gameId) {
        Message message = new Message(sender, "请求观战");
        message.messageType = TYPE_GAME_SPECTATE;
        message.gameId = gameId;
        return message;
    }
    
    /**
     * 创建退出游戏消息
     */
    public static Message createGameQuitMessage(String sender, String gameId) {
        Message message = new Message(sender, "退出了游戏");
        message.messageType = TYPE_GAME_QUIT;
        message.gameId = gameId;
        return message;
    }
    
    /**
     * 创建再来一局消息
     */
    public static Message createGameRestartMessage(String sender, String gameId) {
        Message message = new Message(sender, "发起了再来一局");
        message.messageType = TYPE_GAME_RESTART;
        message.gameId = gameId;
        return message;
    }

    /**
     * 创建游戏表情消息
     */
    public static Message createGameEmojiMessage(String sender, String gameId, String emoji) {
        // 不在 content 填写可见文本，避免被误加入聊天展示
        Message message = new Message(sender, "");
        message.messageType = TYPE_GAME_EMOJI;
        message.gameId = gameId;
        // 将 emoji 放到 gameData 字段（JSON格式）
        message.gameData = "{\"emoji\":\"" + emoji + "\"}";
        return message;
    }
}
