package com.example.todolist.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.todolist.R;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.auth.SessionManager;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.TaskGroupDao;
import com.example.todolist.data.Todo;
import com.example.todolist.sync.SyncWorker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.parse.ParseFile;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import de.hdodenhof.circleimageview.CircleImageView;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService; // 引入 ExecutorService
import java.util.concurrent.Executors; // 引入 Executors


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

    private TaskDao taskDao;
    private SharedPreferences preferences;

    private CircleImageView profileImageLarge;

    private SessionManager sessionManager; // 添加 SessionManager 实例
    private ExecutorService databaseExecutor; // 用于数据库操作的线程池


    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        try {
            // 初始化SharedPreferences
            preferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // 初始化数据库
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
            
            // 初始化UI组件
            textUsername = view.findViewById(R.id.textUsername);
            textEmail = view.findViewById(R.id.textEmail);
            textTotalPoints = view.findViewById(R.id.textTotalPoints);
            textCompletedTasks = view.findViewById(R.id.textCompletedTasks);
            quickPointsIndicator = view.findViewById(R.id.quickPointsIndicator);
            buttonRewards = view.findViewById(R.id.buttonRewards);
            
            // 初始化大头像
            profileImageLarge = view.findViewById(R.id.profileImageLarge);
            profileImageLarge.setOnClickListener(v -> showAvatarOptions());

            sessionManager = SessionManager.getInstance(requireContext()); // 初始化 SessionManager
            databaseExecutor = Executors.newSingleThreadExecutor(); // 初始化线程池
            
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
                Intent intent = new Intent(requireContext(), AboutActivity.class);
                startActivity(intent);
            });
            
            // 设置兑换奖励按钮
            buttonRewards.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "奖励兑换功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            // 设置退出登录按钮
            buttonLogout = view.findViewById(R.id.buttonLogout);
            buttonLogout.setOnClickListener(v -> {
//                logoutUser();
                showLogoutConfirmationDialog();
            });

            
            // 初始化主题卡片
            initThemeCards(view);
            
            // 设置用户名点击修改事件
            textUsername.setOnClickListener(v -> showChangeUsernameDialog());
            
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
        }
        
        return view;
    }

    private void showLogoutConfirmationDialog() {
        try {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("退出登录")
                    .setMessage("确定要退出登录并清除所有本地数据吗？此操作不可恢复。") // 强调会清除数据
                    .setPositiveButton("确定退出", (dialog, which) -> {
                        performLogoutAndClearData(); // 调用执行登出和数据清理的方法
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "显示登出确认对话框失败", e);
            Toast.makeText(requireContext(), "操作失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }
    private void performLogoutAndClearData() {
        try {
            // 1. Parse 用户登出 (必须首先执行，以获取正确的 userId)
            ParseUser currentUser = ParseUser.getCurrentUser();
            if (currentUser == null) {
                // 如果用户已经为空，可能之前已经登出或会话无效，直接清理并跳转
                Log.w(TAG, "ParseUser.getCurrentUser() 为空，可能已登出，直接清理本地数据。");
                clearLocalDataAndNavigateToLogin(null); // 传入null，因为没有当前用户
                return;
            }

            String userIdToClear = currentUser.getObjectId(); // 获取当前用户的 ID
            Log.d(TAG, "准备为用户 " + userIdToClear + " 清理数据并登出。");

            ParseUser.logOutInBackground(e -> {
                if (e == null) {
                    Log.d(TAG, "Parse 用户成功登出。");
                    // 2. 清理本地数据 (在后台线程执行)
                    clearLocalDataAndNavigateToLogin(userIdToClear);
                } else {
                    Log.e(TAG, "Parse 登出失败", e);
                    // 即便 Parse 登出失败，也尝试清理本地数据并跳转，但给用户提示
                    Toast.makeText(requireContext(), "云端登出失败，但仍会清理本地数据。", Toast.LENGTH_LONG).show();
                    clearLocalDataAndNavigateToLogin(userIdToClear);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "退出登录并清理数据过程中发生错误", e);
            Toast.makeText(requireContext(), "操作失败，请重试", Toast.LENGTH_SHORT).show();
            // 即使发生未知错误，也尝试清理并跳转，防止用户数据残留
            if (ParseUser.getCurrentUser() != null) {
                clearLocalDataAndNavigateToLogin(ParseUser.getCurrentUser().getObjectId());
            } else {
                clearLocalDataAndNavigateToLogin(null); // 如果无法获取用户ID，则尝试清理所有
            }
        }
    }

    private void clearLocalDataAndNavigateToLogin(final String userId) {
        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                TaskDao taskDao = db.taskDao();
                TaskGroupDao taskGroupDao = db.taskGroupDao();

                Log.d(TAG, "开始清理所有 Room 数据库数据...");
                int tasksDeleted = taskDao.deleteAll();
                int groupsDeleted = taskGroupDao.deleteAllTaskGroupsUnfiltered();
                Log.d(TAG, "所有 Room 数据已清理。删除了 " + tasksDeleted + " 个任务和 " + groupsDeleted + " 个任务组。");

                // 清理 SharedPreferences (这部分逻辑不变，因为 SharedPreferences 通常是应用级别的，也应该在登出时重置)
                if (sessionManager != null) {
                    Log.d(TAG, "开始清理会话信息 (SessionManager)...");
                    sessionManager.clearUserSession();
                    Log.d(TAG, "会话信息已清理。");
                }

                SharedPreferences.Editor prefsEditor = preferences.edit();
                prefsEditor.remove(PREF_THEME);
                prefsEditor.remove("user_points");
                prefsEditor.remove("completed_tasks");
                prefsEditor.apply();
                Log.d(TAG, "其他 SharedPreferences 数据已清理。");

                // Parse 本地缓存由 ParseUser.logOut() 处理

                // 跳转到登录页 (在主线程执行)
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // ... (UI 清理和跳转逻辑不变) ...
                        Log.d(TAG, "所有本地数据清理完毕，跳转到登录页。");
                        Toast.makeText(requireContext(), "已退出登录并清除所有本地数据", Toast.LENGTH_SHORT).show(); // 消息可以更明确
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    });
                }
            } catch (Exception ex) {
                Log.e(TAG, "清理所有本地数据时发生错误", ex);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "清理本地数据失败，请手动清除应用数据。", Toast.LENGTH_LONG).show();
                        // 即使清理失败，也尝试跳转到登录页
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    });
                }
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 确保头像点击事件正确设置
        profileImageLarge = view.findViewById(R.id.profileImageLarge);
        if (profileImageLarge != null) {
            profileImageLarge.setOnClickListener(v -> showAvatarOptions());
        }
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
            ParseUser user = ParseUser.getCurrentUser();
            if (user != null) {
                // 先直接显示当前用户信息
                displayUserInfo(user);
                
                // 然后尝试从Parse服务器刷新用户信息
                user.fetchInBackground((object, e) -> {
                    if (e == null) {
                        // 成功刷新后，获取最新用户信息并显示
                        ParseUser refreshedUser = ParseUser.getCurrentUser();
                        displayUserInfo(refreshedUser);
                    } else {
                        Log.e(TAG, "刷新用户信息失败", e);
                    }
                });
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
    
    private void displayUserInfo(ParseUser user) {
        try {
            // 获取邮箱 - 尝试多种方式获取
            String email = user.getEmail(); // 标准邮箱字段
            
            // 如果标准字段为空，尝试从userEmail字段获取
            if (email == null || email.isEmpty()) {
                String userEmail = user.getString("userEmail");
                if (userEmail != null && !userEmail.isEmpty()) {
                    email = userEmail;
                    // 顺便更新标准邮箱字段
                    user.setEmail(email);
                    user.saveInBackground();
                }
            }
            
            // 如果仍然为空，尝试使用用户名（如果它看起来像邮箱）
            if (email == null || email.isEmpty()) {
                String username = user.getUsername();
                if (username != null && username.contains("@")) {
                    email = username;
                }
            }
            
            Log.d(TAG, "用户邮箱: " + (email != null ? email : "null"));
            
            // 获取用户名和自定义名称
            final String username = user.getUsername();
            final String customName = user.getString("displayName");
            Log.d(TAG, "用户名: " + username + ", 自定义名称: " + customName);
            
            // 设置显示的用户名 - 优先使用自定义名称，否则提示可设置
            final String userName = (customName != null && !customName.trim().isEmpty()) 
                ? customName 
                : "未设置用户名";
            
            // 存储最终要显示的邮箱
            final String finalEmail = email;
            
            // 加载用户头像
            loadUserAvatar(user);
            
            // 确保在主线程中更新UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 用户名和邮箱显示设置
                    textUsername.setText(userName);
                    textUsername.setSingleLine(true);
                    textUsername.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    
                    // 设置邮箱信息
                    if (finalEmail != null && !finalEmail.isEmpty()) {
                        textEmail.setText(finalEmail);
                    } else {
                        textEmail.setText("未设置邮箱");
                    }
                    textEmail.setSingleLine(true);
                    textEmail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textEmail.setVisibility(View.VISIBLE);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "解析用户信息失败", e);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    textUsername.setText("未设置用户名");
                    textEmail.setText("未设置邮箱");
                });
            }
        }
    }
    
    /**
     * 加载用户头像
     */
    private void loadUserAvatar(ParseUser user) {
        try {
            // 尝试从Parse获取头像
            ParseFile avatarFile = user.getParseFile("avatar");
            if (avatarFile != null) {
                avatarFile.getDataInBackground((data, e) -> {
                    if (e == null && data != null) {
                        // 转换为Bitmap并设置
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (bitmap != null && getActivity() != null && profileImageLarge != null) {
                            getActivity().runOnUiThread(() -> {
                                profileImageLarge.setImageBitmap(bitmap);
                            });
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "加载头像失败", e);
        }
    }
    
    private void loadTaskStatistics() {
        try {
            // 统计所有已完成任务
            String currentUserId = com.example.todolist.ui.CurrentUserUtil.getCurrentUserId();
            List<Todo> allTasks = taskDao.getAllTasksForUser();
            int completedCount = 0;
            int points = 0;
            for (Todo todo : allTasks) {
                if (todo.completed) {
                    completedCount++;
                    // 按优先级计分
                    if ("高".equals(todo.priority)) {
                        points += 3;
                    } else if ("中".equals(todo.priority)) {
                        points += 2;
                    } else if ("低".equals(todo.priority)) {
                        points += 1;
                    }
                }
            }
            // 更新UI
            final int finalPoints = points;
            final int finalCompletedCount = completedCount;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    textTotalPoints.setText(String.valueOf(finalPoints));
                    textCompletedTasks.setText(String.valueOf(finalCompletedCount));
                    quickPointsIndicator.setText("积分: " + finalPoints);
                });
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
                        // 登出Parse
                        ParseUser.logOut();
                        
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

    // 显示头像选项对话框
    private void showAvatarOptions() {
        if (getContext() == null) return;
        
        String[] options = {"从相册选择", "取消"};
        
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setTitle("更换头像")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // 打开相册选择图片
                    openGallery();
                }
            })
            .show();
    }

    // 打开相册选择图片
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    // 显示修改用户名对话框
    private void showChangeUsernameDialog() {
        if (getContext() == null) return;
        
        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入新的用户名");
        
        // 设置当前用户名作为默认值
        ParseUser user = ParseUser.getCurrentUser();
        if (user != null) {
            String currentName = user.getString("displayName");
            if (currentName != null && !currentName.isEmpty()) {
                input.setText(currentName);
            }
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setTitle("修改用户名")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String newUsername = input.getText().toString().trim();
                if (!newUsername.isEmpty()) {
                    saveUsername(newUsername);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // 保存用户名到Parse
    private void saveUsername(String newUsername) {
        ParseUser user = ParseUser.getCurrentUser();
        if (user != null) {
            user.put("displayName", newUsername);
            user.saveInBackground(e -> {
                if (e == null) {
                    // 更新UI显示
                    textUsername.setText(newUsername);
                    Toast.makeText(getContext(), "用户名已更新", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // 处理返回结果
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_GALLERY && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            if (selectedImage != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getActivity().getContentResolver(), selectedImage);
                    uploadAvatar(bitmap);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading image", e);
                    Toast.makeText(getContext(), "加载图片失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // 上传头像到Parse
    private void uploadAvatar(Bitmap bitmap) {
        // 在上传前先显示在UI
        if (bitmap != null && profileImageLarge != null) {
            // 更新头像
            profileImageLarge.setImageBitmap(bitmap);
        }
        
        // 将Bitmap转换为Parse文件
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        byte[] imageData = stream.toByteArray();
        
        // 创建Parse文件
        ParseFile imageFile = new ParseFile("avatar.jpg", imageData);
        imageFile.saveInBackground((SaveCallback) e -> {
            if (e == null) {
                // 保存文件引用到用户资料
                ParseUser user = ParseUser.getCurrentUser();
                if (user != null) {
                    user.put("avatar", imageFile);
                    user.saveInBackground(e2 -> {
                        if (e2 == null) {
                            Toast.makeText(getContext(), "头像已更新", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Error saving user avatar reference", e2);
                            Toast.makeText(getContext(), "更新失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                Log.e(TAG, "Error saving avatar file", e);
                Toast.makeText(getContext(), "上传头像失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 头像请求码
    private static final int REQUEST_GALLERY = 1001;
} 