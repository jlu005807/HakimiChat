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
    
    private String sender;
    private String content;
    private long timestamp;
    private boolean isSentByMe;
    private int messageType;     // 消息类型
    private int userCount;       // 在线人数
    private String validatedNickname; // 验证后的昵称
    private boolean isHost;      // 是否是房主发送的消息
    private String targetNickname; // 踢人目标昵称（仅TYPE_KICK用）

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
}
