package com.example.todolist.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.todolist.R;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.example.todolist.sync.SyncWorker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    
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
    
    private TextView textUsername;
    private TextView textEmail;
    private TextView textTotalPoints;
    private TextView textCompletedTasks;
    private TextView quickPointsIndicator;
    private MaterialButton buttonRewards;
    private MaterialButton buttonLogout;
    private CardView settingSync;
    private CardView settingNotification;
    private CardView settingAbout;
    
    // 主题选择卡片
    private MaterialCardView themeDefault;
    private MaterialCardView themeRed;
    private MaterialCardView themeGreen;
    private MaterialCardView themePurple;
    private MaterialCardView themePink;
    private MaterialCardView themeOrange;
    private MaterialCardView themeYellow;
    private MaterialCardView themeBrown;
    private MaterialCardView themeBlack;

    private FirebaseAuth auth;
    private TaskDao taskDao;
    private SharedPreferences preferences;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        try {
            // 初始化SharedPreferences
            preferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // 初始化Firebase
            auth = FirebaseAuth.getInstance();
            
            // 初始化数据库
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
            
            // 初始化UI组件
            textUsername = view.findViewById(R.id.textUsername);
            textEmail = view.findViewById(R.id.textEmail);
            textTotalPoints = view.findViewById(R.id.textTotalPoints);
            textCompletedTasks = view.findViewById(R.id.textCompletedTasks);
            quickPointsIndicator = view.findViewById(R.id.quickPointsIndicator);
            buttonRewards = view.findViewById(R.id.buttonRewards);
            
            // 设置用户信息
            setupUserInfo();
            
            // 设置设置项点击事件
            settingSync = view.findViewById(R.id.settingSync);
            settingNotification = view.findViewById(R.id.settingNotification);
            settingAbout = view.findViewById(R.id.settingAbout);
            
            settingSync.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "正在同步数据...", Toast.LENGTH_SHORT).show();
                SyncWorker.pullCloudToLocal(requireContext());
                SyncWorker.pushLocalToCloud(requireContext());
            });
            
            settingNotification.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "通知设置功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            settingAbout.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "关于应用功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            // 设置兑换奖励按钮
            buttonRewards.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "奖励兑换功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            // 设置退出登录按钮
            buttonLogout = view.findViewById(R.id.buttonLogout);
            buttonLogout.setOnClickListener(v -> {
                logoutUser();
            });
            
            // 初始化主题卡片
            initThemeCards(view);
            
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
        }
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTaskStatistics();
        
        // 更新当前主题高亮显示
        updateThemeSelection();
    }
    
    /**
     * 初始化主题选择卡片
     */
    private void initThemeCards(View view) {
        try {
            // 查找主题卡片
            themeDefault = view.findViewById(R.id.themeDefault);
            themeRed = view.findViewById(R.id.themeRed);
            themeGreen = view.findViewById(R.id.themeGreen);
            themePurple = view.findViewById(R.id.themePurple);
            themePink = view.findViewById(R.id.themePink);
            themeOrange = view.findViewById(R.id.themeOrange);
            themeYellow = view.findViewById(R.id.themeYellow);
            themeBrown = view.findViewById(R.id.themeBrown);
            themeBlack = view.findViewById(R.id.themeBlack);
            
            // 设置点击事件
            themeDefault.setOnClickListener(v -> applyTheme(THEME_DEFAULT));
            themeRed.setOnClickListener(v -> applyTheme(THEME_RED));
            themeGreen.setOnClickListener(v -> applyTheme(THEME_GREEN));
            themePurple.setOnClickListener(v -> applyTheme(THEME_PURPLE));
            themePink.setOnClickListener(v -> applyTheme(THEME_PINK));
            themeOrange.setOnClickListener(v -> applyTheme(THEME_ORANGE));
            themeYellow.setOnClickListener(v -> applyTheme(THEME_YELLOW));
            themeBrown.setOnClickListener(v -> applyTheme(THEME_BROWN));
            themeBlack.setOnClickListener(v -> applyTheme(THEME_BLACK));
            
            // 更新当前主题高亮显示
            updateThemeSelection();
        } catch (Exception e) {
            Log.e(TAG, "初始化主题卡片失败", e);
        }
    }
    
    /**
     * 更新当前主题选择高亮
     */
    private void updateThemeSelection() {
        try {
            // 获取当前主题
            int currentTheme = preferences.getInt(PREF_THEME, THEME_DEFAULT);
            
            // 重置所有卡片
            resetAllThemeCards();
            
            // 高亮当前主题
            MaterialCardView selectedCard;
            switch (currentTheme) {
                case THEME_RED:
                    selectedCard = themeRed;
                    break;
                case THEME_GREEN:
                    selectedCard = themeGreen;
                    break;
                case THEME_PURPLE:
                    selectedCard = themePurple;
                    break;
                case THEME_PINK:
                    selectedCard = themePink;
                    break;
                case THEME_ORANGE:
                    selectedCard = themeOrange;
                    break;
                case THEME_YELLOW:
                    selectedCard = themeYellow;
                    break;
                case THEME_BROWN:
                    selectedCard = themeBrown;
                    break;
                case THEME_BLACK:
                    selectedCard = themeBlack;
                    break;
                case THEME_DEFAULT:
                default:
                    selectedCard = themeDefault;
                    break;
            }
            
            if (selectedCard != null) {
                // 设置选中卡片的特殊样式
                selectedCard.setCardElevation(12f);
                selectedCard.setStrokeWidth(3);
                
                int colorRes;
                switch (currentTheme) {
                    case THEME_RED:
                        colorRes = R.color.theme2_primary;
                        break;
                    case THEME_GREEN:
                        colorRes = R.color.theme3_primary;
                        break;
                    case THEME_PURPLE:
                        colorRes = R.color.theme4_primary;
                        break;
                    case THEME_PINK:
                        colorRes = R.color.theme5_primary;
                        break;
                    case THEME_ORANGE:
                        colorRes = R.color.theme6_primary;
                        break;
                    case THEME_YELLOW:
                        colorRes = R.color.theme7_primary_darkest;
                        break;
                    case THEME_BROWN:
                        colorRes = R.color.theme8_primary;
                        break;
                    case THEME_BLACK:
                        colorRes = R.color.theme9_primary;
                        break;
                    case THEME_DEFAULT:
                    default:
                        colorRes = R.color.theme1_primary;
                        break;
                }
                
                selectedCard.setStrokeColor(ContextCompat.getColor(requireContext(), colorRes));
                
                // 为所选卡片添加缩放效果
                selectedCard.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "更新主题选择UI失败", e);
        }
    }
    
    /**
     * 重置所有主题卡片状态
     */
    private void resetAllThemeCards() {
        if (themeDefault != null) {
            themeDefault.setCardElevation(4f);
            themeDefault.setStrokeWidth(0);
            themeDefault.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themeRed != null) {
            themeRed.setCardElevation(4f);
            themeRed.setStrokeWidth(0);
            themeRed.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themeGreen != null) {
            themeGreen.setCardElevation(4f);
            themeGreen.setStrokeWidth(0);
            themeGreen.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themePurple != null) {
            themePurple.setCardElevation(4f);
            themePurple.setStrokeWidth(0);
            themePurple.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themePink != null) {
            themePink.setCardElevation(4f);
            themePink.setStrokeWidth(0);
            themePink.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themeOrange != null) {
            themeOrange.setCardElevation(4f);
            themeOrange.setStrokeWidth(0);
            themeOrange.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themeYellow != null) {
            themeYellow.setCardElevation(4f);
            themeYellow.setStrokeWidth(0);
            themeYellow.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themeBrown != null) {
            themeBrown.setCardElevation(4f);
            themeBrown.setStrokeWidth(0);
            themeBrown.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        
        if (themeBlack != null) {
            themeBlack.setCardElevation(4f);
            themeBlack.setStrokeWidth(0);
            themeBlack.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
    }
    
    /**
     * 应用选定的主题
     */
    private void applyTheme(int themeId) {
        try {
            // 保存主题设置
            preferences.edit().putInt(PREF_THEME, themeId).apply();
            
            // 更新主题高亮
            updateThemeSelection();
            
            // 应用主题
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                activity.setApplicationTheme(themeId);
                
                // 显示成功消息
                String themeName;
                switch (themeId) {
                    case THEME_RED:
                        themeName = "红色系";
                        break;
                    case THEME_GREEN:
                        themeName = "绿色系";
                        break;
                    case THEME_PURPLE:
                        themeName = "紫色系";
                        break;
                    case THEME_PINK:
                        themeName = "粉色系";
                        break;
                    case THEME_ORANGE:
                        themeName = "橙色系";
                        break;
                    case THEME_YELLOW:
                        themeName = "黄色系";
                        break;
                    case THEME_BROWN:
                        themeName = "大地色系";
                        break;
                    case THEME_BLACK:
                        themeName = "黑色系";
                        break;
                    case THEME_DEFAULT:
                    default:
                        themeName = "蓝色系";
                        break;
                }
                Toast.makeText(requireContext(), "主题已设置为" + themeName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "应用主题失败", e);
            Toast.makeText(requireContext(), "主题设置失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupUserInfo() {
        try {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                // 设置用户名（使用邮箱前缀）
                String email = user.getEmail();
                String userName = email != null ? email.split("@")[0] : "用户";
                textUsername.setText(userName);
                textEmail.setText(email != null ? email : "未设置邮箱");
            } else {
                textUsername.setText("未登录用户");
                textEmail.setText("请登录账号");
            }
        } catch (Exception e) {
            Log.e(TAG, "设置用户信息失败", e);
            textUsername.setText("加载失败");
            textEmail.setText("请稍后重试");
        }
    }
    
    private void loadTaskStatistics() {
        try {
            // 从MainActivity获取积分信息
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                int points = activity.getUserPoints();
                int completedTasksCount = activity.getCompletedTasksCount();
                
                // 更新UI
                textTotalPoints.setText(String.valueOf(points));
                textCompletedTasks.setText(String.valueOf(completedTasksCount));
                
                // 更新快捷积分显示
                quickPointsIndicator.setText("积分: " + points);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载统计数据失败", e);
        }
    }
    
    private void logoutUser() {
        try {
            // 弹出确认对话框
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("退出登录")
                    .setMessage("确定要退出登录吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        // 登出Firebase
                        FirebaseAuth.getInstance().signOut();
                        
                        // 返回登录页
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "退出登录失败", e);
            Toast.makeText(requireContext(), "退出登录失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }
} 