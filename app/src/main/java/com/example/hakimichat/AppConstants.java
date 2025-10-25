package com.example.hakimichat;

/**
 * 应用常量配置类
 * 用于管理可调整的参数
 */
public class AppConstants {
    
    // ========== 昵称相关配置 ==========
    /**
     * 昵称最大长度（字符数）
     */
    public static final int MAX_NICKNAME_LENGTH = 5;
    
    /**
     * 昵称最小长度（字符数）
     */
    public static final int MIN_NICKNAME_LENGTH = 1;
    
    // ========== 消息相关配置 ==========
    /**
     * 单条消息最大长度（字符数）
     */
    public static final int MAX_MESSAGE_LENGTH = 300;
    
    /**
     * 消息最小长度（字符数，不包括空白字符）
     */
    public static final int MIN_MESSAGE_LENGTH = 1;
    
    // ========== 历史消息配置 ==========
    /**
     * 历史消息保存数量
     */
    public static final int MAX_HISTORY_SIZE = 30;
    
    // ========== 网络相关配置 ==========
    /**
     * 服务器端口
     */
    public static final int SERVER_PORT = 8888;
    
    /**
     * 连接超时时间（毫秒）
     */
    public static final int CONNECTION_TIMEOUT = 5000;
    
    // 私有构造函数，防止实例化
    private AppConstants() {
        throw new AssertionError("Cannot instantiate AppConstants");
    }
}
