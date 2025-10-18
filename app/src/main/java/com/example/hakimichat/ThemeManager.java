package com.example.hakimichat;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 深色模式管理类
 * 管理应用的主题模式（浅色/深色/跟随系统）
 */
public class ThemeManager {
    
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    
    // 主题模式常量
    public static final int MODE_LIGHT = 0;      // 浅色模式
    public static final int MODE_DARK = 1;       // 深色模式
    public static final int MODE_SYSTEM = 2;     // 跟随系统
    
    private static ThemeManager instance;
    private final SharedPreferences prefs;
    
    private ThemeManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取单例实例
     */
    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取当前主题模式
     * @return MODE_LIGHT, MODE_DARK, 或 MODE_SYSTEM
     */
    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }
    
    /**
     * 设置主题模式
     * @param mode MODE_LIGHT, MODE_DARK, 或 MODE_SYSTEM
     */
    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyTheme(mode);
    }
    
    /**
     * 应用主题
     */
    public void applyTheme(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                android.util.Log.d("ThemeManager", "应用浅色模式");
                break;
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                android.util.Log.d("ThemeManager", "应用深色模式");
                break;
            case MODE_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                android.util.Log.d("ThemeManager", "跟随系统模式");
                break;
        }
    }
    
    /**
     * 初始化主题（在Application中调用）
     */
    public void initTheme() {
        int mode = getThemeMode();
        applyTheme(mode);
    }
    
    /**
     * 获取主题模式名称
     */
    public String getThemeModeName(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                return "浅色模式";
            case MODE_DARK:
                return "深色模式";
            case MODE_SYSTEM:
                return "跟随系统";
            default:
                return "未知";
        }
    }
    
    /**
     * 判断当前是否为深色模式
     */
    public static boolean isDarkMode(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode 
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
