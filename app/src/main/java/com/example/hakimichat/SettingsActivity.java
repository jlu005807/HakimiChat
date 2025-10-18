package com.example.hakimichat;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置界面 - 包括深色模式设置
 */
public class SettingsActivity extends AppCompatActivity {
    
    private RadioGroup rgThemeMode;
    private RadioButton rbLight;
    private RadioButton rbDark;
    private RadioButton rbSystem;
    private ThemeManager themeManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题
        ThemeManager.getInstance(this).initTheme();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        themeManager = ThemeManager.getInstance(this);
        
        initViews();
        loadCurrentTheme();
        setupListeners();
    }
    
    private void initViews() {
        // 设置标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("设置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        rgThemeMode = findViewById(R.id.rgThemeMode);
        rbLight = findViewById(R.id.rbLight);
        rbDark = findViewById(R.id.rbDark);
        rbSystem = findViewById(R.id.rbSystem);
    }
    
    private void loadCurrentTheme() {
        int currentMode = themeManager.getThemeMode();
        
        switch (currentMode) {
            case ThemeManager.MODE_LIGHT:
                rbLight.setChecked(true);
                break;
            case ThemeManager.MODE_DARK:
                rbDark.setChecked(true);
                break;
            case ThemeManager.MODE_SYSTEM:
                rbSystem.setChecked(true);
                break;
        }
    }
    
    private void setupListeners() {
        rgThemeMode.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            
            if (checkedId == R.id.rbLight) {
                mode = ThemeManager.MODE_LIGHT;
            } else if (checkedId == R.id.rbDark) {
                mode = ThemeManager.MODE_DARK;
            } else {
                mode = ThemeManager.MODE_SYSTEM;
            }
            
            // 保存并应用主题
            themeManager.setThemeMode(mode);
            
            // 重新创建Activity以应用主题
            recreate();
        });
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
