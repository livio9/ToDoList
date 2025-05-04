package com.example.todolist.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.todolist.TodoList;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.R;
import com.example.todolist.sync.SyncWorker;
import com.example.todolist.utils.LoadingStateManager;
import com.example.todolist.utils.NetworkStateMonitor;
import com.parse.ParseUser;
import android.view.View;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
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
    
    private TaskDao taskDao;
    private BottomNavigationView bottomNavigation;
    private CardView userAvatarContainer;
    private ImageView userAvatar;
    private SharedPreferences preferences;
    private Toolbar toolbar;
    
    private TasksFragment tasksFragment;
    private TaskGroupsFragment taskGroupsFragment;
    private StatisticsFragment statisticsFragment;
    private ProfileFragment profileFragment;
    
    // 积分系统常量
    private static final String PREF_USER_POINTS = "user_points";
    private static final String PREF_COMPLETED_TASKS = "completed_tasks";
    
    // 状态管理相关
    private LoadingStateManager loadingStateManager;
    private NetworkStateMonitor networkMonitor;
    private ConstraintLayout mainContainer;
    private boolean isDataSyncScheduled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 初始化SharedPreferences
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // 应用保存的主题
        applyTheme();
        
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate 开始");
        try {
            try {
                setContentView(R.layout.activity_main);
                Log.d(TAG, "布局加载完成");
            } catch (Exception e) {
                Log.e(TAG, "布局加载失败", e);
                Toast.makeText(this, "界面加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            // 初始化网络监听器
            networkMonitor = ((TodoList) getApplication()).getNetworkMonitor();
            
            // 初始化主容器和加载状态管理器
            mainContainer = findViewById(R.id.mainContainer);
            loadingStateManager = LoadingStateManager.wrap(this, mainContainer);
            loadingStateManager.setRetryListener(() -> {
                // 重试加载
                loadingStateManager.showState(LoadingStateManager.STATE_LOADING);
                initializeApp();
            });
            
            // 设置加载状态
            loadingStateManager.showState(LoadingStateManager.STATE_LOADING);
            
            // 注册网络状态变化监听
            setupNetworkListener();
            
            // 初始化应用
            initializeApp();
            
        } catch (Exception e) {
            Log.e(TAG, "onCreate 过程异常", e);
            Toast.makeText(this, "应用启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    /**
     * 初始化应用
     */
    private void initializeApp() {
        try {
            // 检查网络状态
            if (!networkMonitor.isNetworkAvailable()) {
                loadingStateManager.showState(LoadingStateManager.STATE_NETWORK_ERROR);
                return;
            }
            
            // 初始化工具栏
            try {
                toolbar = findViewById(R.id.toolbar);
                setSupportActionBar(toolbar);
                
                // 初始化头像控件
                userAvatarContainer = findViewById(R.id.userAvatarContainer);
                userAvatar = findViewById(R.id.userAvatar);
                
                // 设置头像点击事件
                if (userAvatarContainer != null) {
                    userAvatarContainer.setOnClickListener(v -> {
                        // 添加点击动画效果
                        animateAvatarClick(userAvatarContainer);
                        
                        // 直接跳转到个人中心页面
                        bottomNavigation.setSelectedItemId(R.id.navigation_profile);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "设置工具栏失败", e);
                // 工具栏失败不影响主要功能
            }

            // 初始化数据库
            try {
                taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
                if (taskDao == null) {
                    throw new Exception("taskDao is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "数据库访问失败", e);
                loadingStateManager.setErrorViewContent(0, "数据库访问失败: " + e.getMessage());
                loadingStateManager.showState(LoadingStateManager.STATE_ERROR);
                return;
            }
            
            try {
                // 初始化Fragment
                initFragments();
                
                // 设置底部导航
                setupBottomNavigation();
            } catch (Exception e) {
                Log.e(TAG, "UI初始化失败", e);
                loadingStateManager.setErrorViewContent(0, "界面初始化失败: " + e.getMessage());
                loadingStateManager.showState(LoadingStateManager.STATE_ERROR);
                return;
            }
            
            // 登录后立即拉取云端数据，恢复本地数据
            if (!isDataSyncScheduled && ParseUser.getCurrentUser() != null) {
                isDataSyncScheduled = true;
                syncDataWithDelay();
            }
            
            // 显示内容
            loadingStateManager.showState(LoadingStateManager.STATE_SUCCESS);
            
        } catch (Exception e) {
            Log.e(TAG, "应用初始化失败", e);
            loadingStateManager.setErrorViewContent(0, "应用初始化失败: " + e.getMessage());
            loadingStateManager.showState(LoadingStateManager.STATE_ERROR);
        }
    }
    
    /**
     * 设置网络状态监听器
     */
    private void setupNetworkListener() {
        try {
            networkMonitor.addListener(new NetworkStateMonitor.NetworkStateListener() {
                @Override
                public void onNetworkAvailable() {
                    // 网络恢复，重新加载数据
                    if (loadingStateManager.getCurrentState() == LoadingStateManager.STATE_NETWORK_ERROR) {
                        runOnUiThread(() -> {
                            loadingStateManager.showState(LoadingStateManager.STATE_LOADING);
                            initializeApp();
                        });
                    } else {
                        // 如果已经加载完成，只同步数据
                        runOnUiThread(() -> {
                            Snackbar.make(mainContainer, "网络已恢复", Snackbar.LENGTH_SHORT).show();
                            if (ParseUser.getCurrentUser() != null && !isDataSyncScheduled) {
                                syncDataWithDelay();
                            }
                        });
                    }
                }
                
                @Override
                public void onNetworkLost() {
                    // 网络断开，显示提示
                    runOnUiThread(() -> 
                        Snackbar.make(mainContainer, "网络连接已断开，部分功能可能不可用", Snackbar.LENGTH_LONG).show()
                    );
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "设置网络监听失败", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            // 取消注册广播接收器
            unregisterReceiver(dataUpdateReceiver);
        } catch (Exception e) {
            Log.e(TAG, "注销广播接收器失败", e);
        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            // 注册广播接收器，用于监听数据更新
            IntentFilter filter = new IntentFilter("com.example.todolist.ACTION_DATA_UPDATED");
            registerReceiver(dataUpdateReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "注册广播接收器失败", e);
        }
    }

    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 由于现在使用了Fragment，这里不直接刷新任务列表
            // 而是通过广播通知各个Fragment自行处理数据更新
            // Fragment应该在其onResume方法中刷新数据
            
            // 检查是否是任务完成广播，更新积分
            if (intent.getAction().equals("com.example.todolist.ACTION_DATA_UPDATED") && 
                intent.hasExtra("task_completed") && intent.getBooleanExtra("task_completed", false)) {
                
                // 如果是任务完成，增加积分
                if (intent.hasExtra("task_points")) {
                    int points = intent.getIntExtra("task_points", 10);
                    addUserPoints(points);
                    
                    // 显示积分获取提示
                    Toast.makeText(MainActivity.this, 
                            "恭喜完成任务！获得 " + points + " 积分", 
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private void initFragments() {
        tasksFragment = new TasksFragment();
        taskGroupsFragment = new TaskGroupsFragment();
        statisticsFragment = new StatisticsFragment();
        profileFragment = new ProfileFragment();
        
        // 默认显示任务列表页面
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, tasksFragment)
                .commit();
    }
    
    private void setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation);
        
        // 添加点击动画效果
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.navigation_tasks) {
                switchFragment(tasksFragment);
                setTitle("待办事项");
                return true;
            } else if (itemId == R.id.navigation_task_groups) {
                switchFragment(taskGroupsFragment);
                setTitle("待办集");
                return true;
            } else if (itemId == R.id.navigation_statistics) {
                switchFragment(statisticsFragment);
                setTitle("数据统计");
                return true;
            } else if (itemId == R.id.navigation_profile) {
                switchFragment(profileFragment);
                setTitle("个人中心");
                return true;
            }
            
            return false;
        });
    }
    
    private void switchFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }
    
    // 用户头像点击动画
    private void animateAvatarClick(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f, 1f);
        
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }
    
    private void syncDataWithDelay() {
        // 延迟一小段时间再同步，避免应用启动时过多的操作
        new Handler().postDelayed(() -> {
            if (!networkMonitor.isNetworkAvailable()) {
                isDataSyncScheduled = false;
                return;
            }
            
            // 从云端拉取数据
            SyncWorker.pullCloudToLocal(getApplicationContext());
            SyncWorker.pullTaskGroupsToLocal(getApplicationContext());
            
            // 上传本地修改
            SyncWorker.pushLocalToCloud(getApplicationContext());
            SyncWorker.pushTaskGroupsToCloud(getApplicationContext());
            
            // 同步完成，重置标记
            isDataSyncScheduled = false;
        }, 1000);
    }
    
    // 增加用户积分
    private void addUserPoints(int points) {
        int currentPoints = preferences.getInt(PREF_USER_POINTS, 0);
        int completedTasks = preferences.getInt(PREF_COMPLETED_TASKS, 0);
        
        preferences.edit()
            .putInt(PREF_USER_POINTS, currentPoints + points)
            .putInt(PREF_COMPLETED_TASKS, completedTasks + 1)
            .apply();
    }
    
    // 应用主题
    private void applyTheme() {
        try {
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
    
    /**
     * 获取用户积分（供ProfileFragment调用）
     * @return 用户积分
     */
    public int getUserPoints() {
        return preferences.getInt(PREF_USER_POINTS, 0);
    }
    
    /**
     * 获取已完成任务数量（供ProfileFragment调用）
     * @return 已完成任务数量
     */
    public int getCompletedTasksCount() {
        return preferences.getInt(PREF_COMPLETED_TASKS, 0);
    }
}




