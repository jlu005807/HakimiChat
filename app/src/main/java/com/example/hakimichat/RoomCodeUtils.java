package com.example.hakimichat;

import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

/**
 * 房间号编码解码工具类 - V3优化版本
 * 将IP地址转换为简短的房间号，格式：字母+数字
 * 例如：192.168.1.100 -> ABCD567
 * 
 * 算法说明（简化版）：
 * - IP地址4个字节 → 每5位映射为1个字符
 * - 32位需要: ⌈32/5⌉ = 7个字符（精确）
 * - 使用位操作直接提取，避免大数运算
 * - 最后一位包含简单校验和
 */
public class RoomCodeUtils {
    
    // 32个字符，去掉易混淆的 I, O, 0, 1
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 7; // 房间号长度
    
    /**
     * 将IP地址编码为房间号（V3优化算法）
     * @param ipAddress IP地址，例如：192.168.1.100
     * @return 房间号，例如：ABCD567
     */
    public static String encodeIpToRoomCode(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return null;
        }
        
        try {
            android.util.Log.d("RoomCodeUtils", "======== 开始编码 V3 ========");
            android.util.Log.d("RoomCodeUtils", "输入IP: " + ipAddress);
            
            // 解析IP地址的四个部分
            String[] parts = ipAddress.split("\\.");
            if (parts.length != 4) {
                android.util.Log.e("RoomCodeUtils", "编码失败: IP格式错误");
                return null;
            }
            
            // 转换为4个字节
            byte[] ipBytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(parts[i]);
                if (octet < 0 || octet > 255) {
                    android.util.Log.e("RoomCodeUtils", "编码失败: IP段超出范围");
                    return null;
                }
                ipBytes[i] = (byte) octet;
            }
            
            android.util.Log.d("RoomCodeUtils", "IP字节: [" + 
                (ipBytes[0] & 0xFF) + ", " + (ipBytes[1] & 0xFF) + ", " + 
                (ipBytes[2] & 0xFF) + ", " + (ipBytes[3] & 0xFF) + "]");
            
            // 将4字节(32位)编码为7个字符(每字符5位)
            // 使用位提取方式: 每次提取5位作为索引
            StringBuilder roomCode = new StringBuilder();
            
            // 合并为一个长整数（无符号）
            long ipValue = 0;
            for (int i = 0; i < 4; i++) {
                ipValue = (ipValue << 8) | (ipBytes[i] & 0xFF);
            }
            
            android.util.Log.d("RoomCodeUtils", "IP数值: " + ipValue + 
                " (0x" + Long.toHexString(ipValue) + ")");
            
            // 提取7个5位数据块 (前6个完整，第7个包含剩余2位+校验)
            for (int i = 0; i < 6; i++) {
                // 从高位开始提取，每次提取5位
                int shift = 27 - (i * 5); // 27, 22, 17, 12, 7, 2
                int index = (int) ((ipValue >> shift) & 0x1F); // 提取5位
                char c = CHARSET.charAt(index);
                roomCode.append(c);
                android.util.Log.d("RoomCodeUtils", "  位" + (i+1) + 
                    ": shift=" + shift + ", index=" + index + ", 字符=" + c);
            }
            
            // 第7个字符: 剩余2位 + 3位校验
            int lastBits = (int) (ipValue & 0x03); // 最低2位
            int checksum = calculateSimpleChecksum(ipBytes); // 3位校验(0-7)
            int lastIndex = (lastBits << 3) | checksum; // 合并为5位
            char lastChar = CHARSET.charAt(lastIndex);
            roomCode.append(lastChar);
            
            android.util.Log.d("RoomCodeUtils", "  位7: lastBits=" + lastBits + 
                ", checksum=" + checksum + ", index=" + lastIndex + ", 字符=" + lastChar);
            
            String result = roomCode.toString().toUpperCase();
            android.util.Log.d("RoomCodeUtils", "编码成功: " + result);
            android.util.Log.d("RoomCodeUtils", "======== 编码完成 ========");
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e("RoomCodeUtils", "编码异常: " + e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 将房间号解码为IP地址（V3优化算法）
     * @param roomCode 房间号，例如：ABCD567
     * @return IP地址，例如：192.168.1.100
     */
    public static String decodeRoomCodeToIp(String roomCode) {
        if (roomCode == null || roomCode.length() != CODE_LENGTH) {
            android.util.Log.e("RoomCodeUtils", "解码失败: 房间号长度不正确 - " + 
                (roomCode == null ? "null" : roomCode.length()));
            return null;
        }
        
        try {
            roomCode = roomCode.toUpperCase();
            android.util.Log.d("RoomCodeUtils", "======== 开始解码 V3 ========");
            android.util.Log.d("RoomCodeUtils", "输入房间号: " + roomCode);
            
            // 验证房间号格式
            for (char c : roomCode.toCharArray()) {
                if (CHARSET.indexOf(c) == -1) {
                    android.util.Log.e("RoomCodeUtils", "解码失败: 包含无效字符 - " + c);
                    return null;
                }
            }
            
            // 解码前6个字符，每个代表5位
            long ipValue = 0;
            for (int i = 0; i < 6; i++) {
                char c = roomCode.charAt(i);
                int index = CHARSET.indexOf(c);
                ipValue = (ipValue << 5) | index; // 左移5位，加入新的5位
                android.util.Log.d("RoomCodeUtils", "  位" + (i+1) + 
                    ": 字符=" + c + ", 索引=" + index + ", 累积=0x" + 
                    Long.toHexString(ipValue));
            }
            
            // 解码第7个字符: 包含最后2位IP + 3位校验
            char lastChar = roomCode.charAt(6);
            int lastIndex = CHARSET.indexOf(lastChar);
            int lastBits = (lastIndex >> 3) & 0x03; // 高2位是IP数据
            int checksum = lastIndex & 0x07; // 低3位是校验和
            
            android.util.Log.d("RoomCodeUtils", "  位7: 字符=" + lastChar + 
                ", 索引=" + lastIndex + ", lastBits=" + lastBits + 
                ", checksum=" + checksum);
            
            // 将最后2位加入IP值
            ipValue = (ipValue << 2) | lastBits;
            
            android.util.Log.d("RoomCodeUtils", "完整IP值: " + ipValue + 
                " (0x" + Long.toHexString(ipValue) + ")");
            
            // 转换为4个字节
            byte[] ipBytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                ipBytes[i] = (byte) ((ipValue >> (24 - i * 8)) & 0xFF);
            }
            
            android.util.Log.d("RoomCodeUtils", "IP字节: [" + 
                (ipBytes[0] & 0xFF) + ", " + (ipBytes[1] & 0xFF) + ", " + 
                (ipBytes[2] & 0xFF) + ", " + (ipBytes[3] & 0xFF) + "]");
            
            // 验证校验和
            int expectedChecksum = calculateSimpleChecksum(ipBytes);
            android.util.Log.d("RoomCodeUtils", "计算校验和: " + expectedChecksum);
            
            if (expectedChecksum != checksum) {
                android.util.Log.e("RoomCodeUtils", "解码失败: 校验和不匹配 - 期望: " + 
                    expectedChecksum + ", 实际: " + checksum);
                return null;
            }
            
            // 转换为IP字符串
            String ip = (ipBytes[0] & 0xFF) + "." + 
                        (ipBytes[1] & 0xFF) + "." + 
                        (ipBytes[2] & 0xFF) + "." + 
                        (ipBytes[3] & 0xFF);
            
            android.util.Log.d("RoomCodeUtils", "解码成功: " + ip);
            android.util.Log.d("RoomCodeUtils", "======== 解码完成 ========");
            return ip;
            
        } catch (Exception e) {
            android.util.Log.e("RoomCodeUtils", "解码异常: " + e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 计算简单校验和（3位，0-7）
     * @param ipBytes IP地址的4个字节
     * @return 3位校验和 (0-7)
     */
    private static int calculateSimpleChecksum(byte[] ipBytes) {
        // 简单的XOR校验，取3位
        int checksum = 0;
        for (byte b : ipBytes) {
            checksum ^= (b & 0xFF);
        }
        // 折叠为3位: 取高3位和低3位进行XOR
        int high3 = (checksum >> 5) & 0x07;
        int mid3 = (checksum >> 2) & 0x07;
        int low3 = checksum & 0x07;
        return (high3 ^ mid3 ^ low3) & 0x07;
    }
    
    /**
     * 生成更简短的房间号（4位）
     * 使用哈希算法，但可能有冲突
     */
    public static String generateShortRoomCode(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return null;
        }
        
        try {
            // 使用MD5生成哈希
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(ipAddress.getBytes());
            
            // 取前4个字节
            int hash = ByteBuffer.wrap(digest).getInt();
            
            // 转换为正数
            hash = Math.abs(hash);
            
            // 生成4位房间号
            StringBuilder roomCode = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                roomCode.append(CHARSET.charAt(hash % CHARSET.length()));
                hash /= CHARSET.length();
            }
            
            return roomCode.toString().toUpperCase();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 验证房间号格式是否正确
     */
    public static boolean isValidRoomCode(String roomCode) {
        if (roomCode == null || roomCode.length() != CODE_LENGTH) {
            return false;
        }
        
        roomCode = roomCode.toUpperCase();
        for (char c : roomCode.toCharArray()) {
            if (CHARSET.indexOf(c) == -1) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 格式化房间号显示（不添加分隔符，直接返回）
     * 例如：ABC123 -> ABC123
     */
    public static String formatRoomCode(String roomCode) {
        if (roomCode == null || roomCode.length() != CODE_LENGTH) {
            return roomCode;
        }
        return roomCode.toUpperCase();
    }
    
    /**
     * 清理房间号（移除空格和特殊字符，转大写）
     */
    public static String cleanRoomCode(String input) {
        if (input == null) {
            return null;
        }
        // 移除空格、横线等特殊字符，只保留字母和数字
        return input.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
