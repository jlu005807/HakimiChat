package com.example.hakimichat;

import android.util.Log;

/**
 * 测试房间号编码解码
 */
public class RoomCodeTest {
    private static final String TAG = "RoomCodeTest";
    
    public static void testRoomCode() {
        // 测试用例
        String[] testIps = {
            "192.168.1.100",
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1"
        };
        
        for (String ip : testIps) {
            Log.d(TAG, "=== 测试IP: " + ip + " ===");
            
            // 编码
            String roomCode = RoomCodeUtils.encodeIpToRoomCode(ip);
            Log.d(TAG, "编码后房间号: " + roomCode);
            
            if (roomCode != null) {
                // 验证格式
                boolean isValid = RoomCodeUtils.isValidRoomCode(roomCode);
                Log.d(TAG, "房间号格式验证: " + (isValid ? "通过" : "失败"));
                
                // 解码
                String decodedIp = RoomCodeUtils.decodeRoomCodeToIp(roomCode);
                Log.d(TAG, "解码后IP: " + decodedIp);
                
                // 验证
                boolean match = ip.equals(decodedIp);
                Log.d(TAG, "编解码验证: " + (match ? "✅ 成功" : "❌ 失败"));
            } else {
                Log.e(TAG, "❌ 编码失败");
            }
            
            Log.d(TAG, "");
        }
    }
}
