package com.example.hakimichat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 应用启动界面（启动动画）
 * - 显示 background startground.png
 * - 在中间播放 icon.gif（使用 GifView）
 * - 可选地播放 res/raw 中指定的音乐，随机选择
 * - 支持最小动画时长配置（毫秒），并在音乐播放完毕后结束
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";

    private GifView gifView;
    // music playback now handled by MusicPlayer singleton so audio can continue after splash
    private boolean minDurationElapsed = false;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_splash);

        gifView = findViewById(R.id.gifView);
        gifView.setGifResource(R.drawable.icon);

        // 最小显示时长（毫秒）: 优先级 — intent extra > shared prefs > default resource
        int minDuration = getIntent().getIntExtra("splash_duration_ms", -1);
        if (minDuration <= 0) {
            // 从 shared prefs 中读取
            SharedPreferences p = getSharedPreferences("app_settings", MODE_PRIVATE);
            minDuration = p.getInt("splash_duration_ms", -1);
        }
        if (minDuration <= 0) {
            // fallback to resource integer if present
            try {
                minDuration = getResources().getInteger(R.integer.splash_duration_ms);
            } catch (Exception e) {
                minDuration = 3000; // default 3s
            }
        }

        // 延时器：到达最小时长后标记
        handler.postDelayed(() -> {
            minDurationElapsed = true;
            checkAndFinish();
        }, minDuration);

        // 尝试播放随机音乐（如果配置了），但不必等音乐结束再结束启动画面。
        List<Integer> musicRes = collectMusicResIds();
        Log.d(TAG, "found music count=" + musicRes.size());
        if (!musicRes.isEmpty()) {
            int idx = new Random().nextInt(musicRes.size());
            int resId = musicRes.get(idx);
            Log.d(TAG, "playing splash music resId=" + resId);
            // 使用 MusicPlayer 单例播放，这样音乐在 SplashActivity 结束后还能继续
            MusicPlayer.getInstance().play(this, resId);
        }
    }

    private List<Integer> collectMusicResIds() {
        List<Integer> list = new ArrayList<>();
        try {
            String[] names = getResources().getStringArray(R.array.splash_music);
            for (String name : names) {
                if (name == null || name.trim().isEmpty()) continue;
                int resId = getResources().getIdentifier(name.trim(), "raw", getPackageName());
                if (resId != 0) list.add(resId);
            }
        } catch (Exception e) {
            // 数组不存在或解析出错
            Log.i(TAG, "no splash_music configured or error reading it");
        }
        return list;
    }

    private void checkAndFinish() {
        // 现在只等最小时长结束，音乐不影响启动画面的关闭
        if (minDurationElapsed) {
            startMainAndFinish();
        }
    }

    private void startMainAndFinish() {
        // 不在此处停止音乐，MusicPlayer 管理播放周期，音乐在主界面继续播放直到完成或被其他逻辑停止
        Intent it = new Intent(this, MainActivity.class);
        startActivity(it);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 不在这里释放全局音乐播放器（它应在播放完成后自动释放），只清理 handler
        handler.removeCallbacksAndMessages(null);
    }
}
