package com.example.hakimichat;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * 简单的全局音乐播放器单例，使用 Application context 播放 raw 资源，播放完成后自动释放。
 */
public class MusicPlayer {
    private static final String TAG = "MusicPlayer";
    private static MusicPlayer instance;
    private MediaPlayer mediaPlayer;

    private MusicPlayer() {}

    public static synchronized MusicPlayer getInstance() {
        if (instance == null) instance = new MusicPlayer();
        return instance;
    }

    /**
     * 使用 application context 播放 raw 资源，若已有正在播放的则先停止并释放。
     */
    public void play(Context ctx, int rawResId) {
        try {
            Log.d(TAG, "play rawResId=" + rawResId);
            stop();
            Context appCtx = ctx.getApplicationContext();
            mediaPlayer = MediaPlayer.create(appCtx, rawResId);
            if (mediaPlayer == null) {
                Log.w(TAG, "MediaPlayer.create returned null");
                return;
            }
            mediaPlayer.setOnCompletionListener(mp -> {
                try { mp.release(); } catch (Exception ignored) {}
                mediaPlayer = null;
            });
            mediaPlayer.start();
        } catch (Exception e) {
            Log.w(TAG, "failed to start music", e);
            try { if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; } } catch (Exception ignored) {}
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
}
