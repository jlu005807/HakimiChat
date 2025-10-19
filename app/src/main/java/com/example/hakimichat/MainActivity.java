package com.example.hakimichat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private TextView tvIpAddress;
    private TextView tvRoomCode;
    private EditText etUsername;
    private EditText etRoomId;
    private Button btnCreateRoom;
    private Button btnJoinRoom;
    private Button btnCopyRoomCode;

    private String localIpAddress;
    private String currentRoomCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题
        ThemeManager.getInstance(this).initTheme();
        
        super.onCreate(savedInstanceState);
        
        // 设置窗口软键盘模式，确保输入框不被键盘遮挡
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        checkPermissions();
        
        // 测试房间号编码解码
        testRoomCodeSystem();
    }
    
    private void testRoomCodeSystem() {
        // 测试几个常见IP
        String testIp = "192.168.1.100";
        String roomCode = RoomCodeUtils.encodeIpToRoomCode(testIp);
        android.util.Log.d("MainActivity", "测试编码 - IP: " + testIp + " → 房间号: " + roomCode);
        
        if (roomCode != null) {
            String decodedIp = RoomCodeUtils.decodeRoomCodeToIp(roomCode);
            android.util.Log.d("MainActivity", "测试解码 - 房间号: " + roomCode + " → IP: " + decodedIp);
            android.util.Log.d("MainActivity", "编解码验证: " + (testIp.equals(decodedIp) ? "✅ 成功" : "❌ 失败"));
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.INTERNET
            };

            // 检查是否需要请求权限
            boolean needRequest = false;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }

            if (needRequest) {
                // 如果需要，显示权限说明
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, 
                        Manifest.permission.ACCESS_WIFI_STATE)) {
                    showPermissionExplanation();
                } else {
                    ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
                }
            } else {
                getLocalIpAddress();
            }
        } else {
            getLocalIpAddress();
        }
    }

    private void showPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("应用需要网络和WiFi权限才能正常工作")
                .setPositiveButton("授权", (dialog, which) -> {
                    String[] permissions = {
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE,
                            Manifest.permission.INTERNET
                    };
                    ActivityCompat.requestPermissions(MainActivity.this, 
                            permissions, PERMISSION_REQUEST_CODE);
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    Toast.makeText(MainActivity.this, "缺少必要权限", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                getLocalIpAddress();
            } else {
                Toast.makeText(this, "权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show();
                tvIpAddress.setText("本机IP: 权限被拒绝");
            }
        }
    }

    private void initViews() {
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvRoomCode = findViewById(R.id.tvRoomCode);
        etUsername = findViewById(R.id.etUsername);
        etRoomId = findViewById(R.id.etRoomId);
        btnCreateRoom = findViewById(R.id.btnCreateRoom);
        btnJoinRoom = findViewById(R.id.btnJoinRoom);
        btnCopyRoomCode = findViewById(R.id.btnCopyRoomCode);
        Button btnRefreshIp = findViewById(R.id.btnRefreshIp);
        android.widget.ImageButton btnSettings = findViewById(R.id.btnSettings);
        
        // 刷新IP按钮点击事件
        btnRefreshIp.setOnClickListener(v -> {
            Toast.makeText(this, "正在刷新IP地址...", Toast.LENGTH_SHORT).show();
            getLocalIpAddress();
        });
        
        // 设置按钮点击事件
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void getLocalIpAddress() {
        try {
            // 首先尝试通过WiFi获取IP
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                
                if (ipAddress != 0) {
                    // 兼容不同Android版本的IP格式化方法
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        localIpAddress = Formatter.formatIpAddress(ipAddress);
                    } else {
                        localIpAddress = String.format("%d.%d.%d.%d",
                                (ipAddress & 0xff),
                                (ipAddress >> 8 & 0xff),
                                (ipAddress >> 16 & 0xff),
                                (ipAddress >> 24 & 0xff));
                    }
                    tvIpAddress.setText("本机IP: " + localIpAddress + " (WiFi)");
                    updateRoomCode();
                    return;
                }
            }
            
            // WiFi未开启或无IP，尝试获取热点IP（通过NetworkInterface）
            localIpAddress = getHotspotIpAddress();
            if (localIpAddress != null) {
                tvIpAddress.setText("本机IP: " + localIpAddress + " (热点)");
                updateRoomCode();
                Toast.makeText(this, "检测到热点模式，其他设备连接您的热点后可加入房间", Toast.LENGTH_LONG).show();
            } else {
                // 都没有获取到IP
                tvIpAddress.setText("本机IP: 未连接网络");
                tvRoomCode.setText("房间号: 未生成");
                btnCopyRoomCode.setEnabled(false);
                Toast.makeText(this, "请开启WiFi或热点", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            tvIpAddress.setText("本机IP: 获取失败");
            tvRoomCode.setText("房间号: 未生成");
            btnCopyRoomCode.setEnabled(false);
            Toast.makeText(this, "获取IP地址失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("MainActivity", "获取IP失败", e);
        }
    }
    
    /**
     * 通过NetworkInterface获取热点IP地址
     * 当手机开启热点时，通常会在wlan0或ap0接口上分配一个IP（通常是192.168.43.1）
     */
    private String getHotspotIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                // 只检查活动的接口
                if (!intf.isUp() || intf.isLoopback()) {
                    continue;
                }
                
                String interfaceName = intf.getName();
                android.util.Log.d("MainActivity", "检查网络接口: " + interfaceName);
                
                // 热点接口通常命名为: wlan0, ap0, swlan0, wlan1等
                // 移动热点通常会分配192.168.43.x或192.168.x.x的IP
                if (interfaceName.toLowerCase().contains("wlan") || 
                    interfaceName.toLowerCase().contains("ap")) {
                    
                    List<InetAddress> addresses = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addresses) {
                        if (!addr.isLoopbackAddress()) {
                            String sAddr = addr.getHostAddress();
                            
                            // 过滤IPv6地址
                            boolean isIPv4 = sAddr.indexOf(':') < 0;
                            
                            if (isIPv4) {
                                android.util.Log.d("MainActivity", "找到IPv4地址: " + sAddr + " 在接口 " + interfaceName);
                                
                                // 热点通常是192.168.43.1或192.168.x.1
                                if (sAddr.startsWith("192.168.")) {
                                    return sAddr;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "获取热点IP失败", e);
        }
        return null;
    }
    
    /**
     * 更新房间号显示
     */
    private void updateRoomCode() {
        if (localIpAddress != null) {
            currentRoomCode = RoomCodeUtils.encodeIpToRoomCode(localIpAddress);
            if (currentRoomCode != null) {
                tvRoomCode.setText("房间号: " + currentRoomCode);
                btnCopyRoomCode.setEnabled(true);
            } else {
                tvRoomCode.setText("房间号: 生成失败");
                btnCopyRoomCode.setEnabled(false);
            }
        }
    }

    private void setupListeners() {
        btnCreateRoom.setOnClickListener(v -> createRoom());
        btnJoinRoom.setOnClickListener(v -> joinRoom());
        btnCopyRoomCode.setOnClickListener(v -> copyRoomCodeToClipboard());
    }
    
    private void copyRoomCodeToClipboard() {
        if (currentRoomCode != null) {
            android.content.ClipboardManager clipboard = 
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(
                    "房间号", currentRoomCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "房间号已复制: " + currentRoomCode, Toast.LENGTH_SHORT).show();
        }
    }

    private void createRoom() {
        // 检查是否有有效的IP地址
        if (TextUtils.isEmpty(localIpAddress) || localIpAddress.equals("0.0.0.0")) {
            Toast.makeText(this, "未获取到IP地址，请开启WiFi或热点后点击\"刷新IP\"", Toast.LENGTH_LONG).show();
            return;
        }
        
        // 检查网络连接（包括热点模式）
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "网络不可用，请检查WiFi或热点是否已开启", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = etUsername.getText().toString().trim();
        
        // 验证昵称长度
        if (!username.isEmpty() && username.length() > AppConstants.MAX_NICKNAME_LENGTH) {
            Toast.makeText(this, "昵称不能超过" + AppConstants.MAX_NICKNAME_LENGTH + "个字", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("MainActivity", "创建房间 - IP: " + localIpAddress + ", 用户名: " + username);
        
        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra(RoomActivity.getExtraIsHost(), true);
        intent.putExtra(RoomActivity.getExtraServerIp(), localIpAddress);
        intent.putExtra(RoomActivity.getExtraUsername(), username);
        startActivity(intent);
    }

    private void joinRoom() {
        String roomInput = etRoomId.getText().toString().trim();
        
        if (TextUtils.isEmpty(roomInput)) {
            Toast.makeText(this, "请输入房间号或IP地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 检查网络连接（包括热点模式）
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "网络不可用，请检查WiFi或热点连接", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("MainActivity", "原始输入: " + roomInput);
        
        String serverIp = null;
        
        // 首先判断是否是IP地址（包含点号）
        if (roomInput.contains(".") && isValidIpAddress(roomInput)) {
            serverIp = roomInput;
            android.util.Log.d("MainActivity", "识别为IP地址: " + serverIp);
            Toast.makeText(this, "使用IP地址连接: " + serverIp, Toast.LENGTH_SHORT).show();
        } 
        // 尝试作为房间号解码
        else {
            // 清理房间号（移除空格、分隔符等）
            String cleanCode = RoomCodeUtils.cleanRoomCode(roomInput);
            android.util.Log.d("MainActivity", "清理后房间号: " + cleanCode);
            
            if (RoomCodeUtils.isValidRoomCode(cleanCode)) {
                android.util.Log.d("MainActivity", "房间号格式验证通过");
                serverIp = RoomCodeUtils.decodeRoomCodeToIp(cleanCode);
                android.util.Log.d("MainActivity", "解码后IP: " + serverIp);
                
                if (serverIp != null) {
                    Toast.makeText(this, "房间号解析成功: " + serverIp, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "房间号解码失败，请检查房间号是否正确", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                Toast.makeText(this, "房间号格式错误（应为7位字符）", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String username = etUsername.getText().toString().trim();
        
        // 验证昵称长度
        if (!username.isEmpty() && username.length() > AppConstants.MAX_NICKNAME_LENGTH) {
            Toast.makeText(this, "昵称不能超过" + AppConstants.MAX_NICKNAME_LENGTH + "个字", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra(RoomActivity.getExtraIsHost(), false);
        intent.putExtra(RoomActivity.getExtraServerIp(), serverIp);
        intent.putExtra(RoomActivity.getExtraUsername(), username);
        startActivity(intent);
    }

    private boolean isNetworkAvailable() {
        try {
            // 如果已经获取到有效的本地IP地址，说明网络是可用的（包括热点模式）
            if (localIpAddress != null && !localIpAddress.isEmpty() && 
                !localIpAddress.equals("0.0.0.0")) {
                android.util.Log.d("MainActivity", "网络可用：已有本地IP - " + localIpAddress);
                return true;
            }
            
            android.net.ConnectivityManager connectivityManager = 
                    (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (connectivityManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.net.Network network = connectivityManager.getActiveNetwork();
                    if (network != null) {
                        android.net.NetworkCapabilities capabilities = 
                                connectivityManager.getNetworkCapabilities(network);
                        if (capabilities != null) {
                            // 支持WiFi或以太网连接
                            boolean hasWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
                            boolean hasEthernet = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
                            android.util.Log.d("MainActivity", "网络检查 - WiFi: " + hasWifi + ", Ethernet: " + hasEthernet);
                            return hasWifi || hasEthernet;
                        }
                    }
                } else {
                    android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected()) {
                        int type = networkInfo.getType();
                        boolean isWifi = type == android.net.ConnectivityManager.TYPE_WIFI;
                        boolean isEthernet = type == android.net.ConnectivityManager.TYPE_ETHERNET;
                        android.util.Log.d("MainActivity", "网络检查(旧版) - WiFi: " + isWifi + ", Ethernet: " + isEthernet);
                        return isWifi || isEthernet;
                    }
                }
            }
            
            // 最后尝试检查是否有热点IP
            String hotspotIp = getHotspotIpAddress();
            if (hotspotIp != null) {
                android.util.Log.d("MainActivity", "网络可用：检测到热点IP - " + hotspotIp);
                return true;
            }
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "网络检查异常", e);
        }
        android.util.Log.d("MainActivity", "网络不可用");
        return false;
    }

    private boolean isValidIpAddress(String ip) {
        if (TextUtils.isEmpty(ip)) {
            return false;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}