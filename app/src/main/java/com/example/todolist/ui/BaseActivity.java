package com.example.todolist.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.todolist.R;
import com.parse.ParseUser;

/**
 * 基础Activity类，所有Activity都应该继承这个类
 * 用于统一处理主题设置等通用功能
 */
public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    // 主题相关常量
    private static final String PREFS_NAME = "todo_prefs";
    private static final String PREF_THEME = "app_theme";
    private static final int THEME_DEFAULT = 0;   // 蓝色系
    private static final int THEME_RED = 1;       // 红色系
    private static final int THEME_GREEN = 2;     // 绿色系
    private static final int THEME_PURPLE = 3;    // 紫色系
    private static final int THEME_PINK = 4;      // 粉色系
    private static final int THEME_ORANGE = 5;    // 橙色系
    private static final int THEME_YELLOW = 6;    // 黄色系
    private static final int THEME_BROWN = 7;     // 大地色系
    private static final int THEME_BLACK = 8;     // 黑色系

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 在super.onCreate之前应用主题
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    /**
     * 应用主题
     */
    protected void applyTheme() {
        try {
            SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            int themeIndex = preferences.getInt(PREF_THEME, THEME_DEFAULT);
            
            switch (themeIndex) {
                case THEME_RED:
                    setTheme(R.style.Theme_ToDoList_Red);
                    break;
                case THEME_GREEN:
                    setTheme(R.style.Theme_ToDoList_Green);
                    break;
                case THEME_PURPLE:
                    setTheme(R.style.Theme_ToDoList_Purple);
                    break;
                case THEME_PINK:
                    setTheme(R.style.Theme_ToDoList_Pink);
                    break;
                case THEME_ORANGE:
                    setTheme(R.style.Theme_ToDoList_Orange);
                    break;
                case THEME_YELLOW:
                    setTheme(R.style.Theme_ToDoList_Yellow);
                    break;
                case THEME_BROWN:
                    setTheme(R.style.Theme_ToDoList_Brown);
                    break;
                case THEME_BLACK:
                    setTheme(R.style.Theme_ToDoList_Black);
                    break;
                default:
                    setTheme(R.style.Theme_ToDoList);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "应用主题失败", e);
            // 使用默认主题
            setTheme(R.style.Theme_ToDoList);
        }
    }
    
    /**
     * 设置应用主题并立即应用（供ProfileFragment调用）
     * @param themeId 主题ID
     */
    public void setApplicationTheme(int themeId) {
        try {
            // 保存主题设置
            SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            preferences.edit().putInt(PREF_THEME, themeId).apply();
            
            // 重新创建Activity以应用新主题
            Intent intent = getIntent();
            finish();
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Log.e(TAG, "设置应用主题失败", e);
        }
    }
}