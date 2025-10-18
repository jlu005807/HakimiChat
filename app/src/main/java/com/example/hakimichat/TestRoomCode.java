package com.example.hakimichat;

public class TestRoomCode {
    public static void main(String[] args) {
        // 测试多个常见IP地址
        String[] testIPs = {
            "192.168.1.1",
            "192.168.1.100",
            "192.168.0.1",
            "10.0.0.1",
            "172.16.0.1"
        };
        
        System.out.println("===== 房间号编解码测试 =====\n");
        
        for (String ip : testIPs) {
            System.out.println("原始IP: " + ip);
            
            // 编码
            String roomCode = RoomCodeUtils.encodeIpToRoomCode(ip);
            System.out.println("房间号: " + roomCode);
            
            if (roomCode != null) {
                // 解码
                String decodedIp = RoomCodeUtils.decodeRoomCodeToIp(roomCode);
                System.out.println("解码IP: " + decodedIp);
                
                // 验证
                boolean success = ip.equals(decodedIp);
                System.out.println("验证结果: " + (success ? "✅ 成功" : "❌ 失败"));
                
                if (!success) {
                    System.out.println("  [错误] 期望: " + ip + ", 实际: " + decodedIp);
                }
            } else {
                System.out.println("❌ 编码失败");
            }
            
            System.out.println();
        }
        
        // 测试清理函数
        System.out.println("\n===== 房间号清理测试 =====\n");
        String[] testCodes = {
            "ABC123",
            "abc123",
            "ABC-123",
            "ABC 123",
            "A B C 1 2 3"
        };
        
        for (String code : testCodes) {
            String cleaned = RoomCodeUtils.cleanRoomCode(code);
            System.out.println("输入: '" + code + "' -> 清理后: '" + cleaned + "'");
        }
    }
}
