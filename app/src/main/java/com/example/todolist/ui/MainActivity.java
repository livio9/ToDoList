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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;

import com.example.todolist.TodoList;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.R;
import com.example.todolist.sync.SyncWorker;
import com.example.todolist.utils.LoadingStateManager;
import com.example.todolist.utils.NetworkStateMonitor;
import com.parse.ParseUser;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {
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
    private LinearLayout navigation_tasks, navigation_task_groups, navigation_statistics, navigation_profile;
    private View currentSelectedNav;
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

            // 检查是否需要定时刷新任务列表
            Intent intent = getIntent();
            if (intent != null && intent.getBooleanExtra("extra_refresh_tasks", false)) {
                // 延迟执行，确保Fragment已初始化
                new Handler().postDelayed(() -> {
                    refreshTasksFragmentPeriodically();
                }, 500);
            }
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
                        
                        // 切换到个人中心页面
                        if (navigation_profile != null) {
                            navigation_profile.performClick();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "设置工具栏失败", e);
                // 工具栏失败不影响主要功能
            }

            // 初始化数据库
            initData();
            
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
//            registerReceiver(dataUpdateReceiver, filter);
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
    private BroadcastReceiver syncStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SyncWorker.ACTION_SYNC_COMPLETED.equals(action)) {
                String syncType = intent.getStringExtra(SyncWorker.EXTRA_SYNC_TYPE);
                int successCount = intent.getIntExtra(SyncWorker.EXTRA_SUCCESS_COUNT, 0);
                int failureCount = intent.getIntExtra(SyncWorker.EXTRA_FAILURE_COUNT, 0);
                String errorMessage = intent.getStringExtra(SyncWorker.EXTRA_SYNC_ERROR_MESSAGE);

                Log.d("MainActivity_SyncLog", "接收到同步完成广播: 类型=" + syncType + ", 成功=" + successCount + ", 失败=" + failureCount);

                // 根据 syncType 判断是哪个同步完成了，然后刷新对应的列表
                if ("todo".equals(syncType)) {
                    // 刷新 Todo 列表的逻辑
                    if (tasksFragment != null && tasksFragment.isAdded()) {
                        tasksFragment.loadTasks(); // 假设 TasksFragment 有一个公共的刷新方法
                    }
                } else if ("task_group".equals(syncType)) {
                    // 刷新 TaskGroup 列表的逻辑
                    if (taskGroupsFragment != null && taskGroupsFragment.isAdded()) {
                        taskGroupsFragment.loadTaskGroups(); // 假设 TaskGroupsFragment 有一个公共的刷新方法
                    }
                }

                // 可以给用户一个提示
                String message;
                if (failureCount > 0) {
                    message = String.format(Locale.getDefault(), "%s同步部分失败: %d 成功, %d 失败. %s",
                            "待办事项".equals(syncType) ? "任务" : "待办集",
                            successCount,
                            failureCount,
                            errorMessage != null ? errorMessage : "");
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                } else if (successCount > 0) {
                    // message = String.format(Locale.getDefault(), "%s数据已同步 (%d 项)",
                    //        "待办事项".equals(syncType) ? "任务" : "待办集",
                    //        successCount);
                    // Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    // 成功的提示可以更柔和或不提示，避免过多打扰
                } else {
                    // 没有成功也没有失败，可能是没有数据需要同步
                    Log.d("MainActivity_SyncLog", syncType + " 同步完成，没有数据更新。");
                }

            } else if ("com.example.todolist.ACTION_SYNC_FAILED".equals(action)) {
                String syncType = intent.getStringExtra(SyncWorker.EXTRA_SYNC_TYPE);
                String reason = intent.getStringExtra("reason");
                Log.w("MainActivity_SyncLog", "接收到同步失败广播: 类型=" + syncType + ", 原因=" + reason);
                String typeName = "待办事项".equals(syncType) ? "任务" : "待办集";
                if ("network_unavailable".equals(reason)) {
                    Toast.makeText(context, typeName + "同步失败：网络不可用", Toast.LENGTH_LONG).show();
                } else if ("user_not_logged_in".equals(reason)) {
                    // 通常不会到这里，因为 SyncWorker 会先检查
                } else {
                    Toast.makeText(context, typeName + "同步失败，请稍后重试", Toast.LENGTH_LONG).show();
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
        try {
            // 找到底部导航的四个按钮
            navigation_tasks = findViewById(R.id.navigation_tasks);
            navigation_task_groups = findViewById(R.id.navigation_task_groups);
            navigation_statistics = findViewById(R.id.navigation_statistics);
            navigation_profile = findViewById(R.id.navigation_profile);
            
            // 默认选中第一个按钮
            updateNavSelection(navigation_tasks);
            
            // 为各个按钮设置点击事件
            navigation_tasks.setOnClickListener(v -> {
                updateNavSelection(v);
                switchFragment(tasksFragment);
                setTitle("待办事项");
            });
            
            navigation_task_groups.setOnClickListener(v -> {
                updateNavSelection(v);
                switchFragment(taskGroupsFragment);
                setTitle("待办集");
            });
            
            navigation_statistics.setOnClickListener(v -> {
                updateNavSelection(v);
                switchFragment(statisticsFragment);
                setTitle("同步");
            });
            
            navigation_profile.setOnClickListener(v -> {
                updateNavSelection(v);
                switchFragment(profileFragment);
                setTitle("个人中心");
            });

            // 添加关于应用的点击事件
            View settingAbout = findViewById(R.id.settingAbout);
            settingAbout.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(intent);
            });
        } catch (Exception e) {
            Log.e(TAG, "设置底部导航失败", e);
        }
    }
    
    // 更新底部导航选中状态
    private void updateNavSelection(View selected) {
        // 重置所有导航项的状态
        for (View view : new View[]{navigation_tasks, navigation_task_groups, navigation_statistics, navigation_profile}) {
            // 找到图标和文字
            ImageView icon = (ImageView) ((LinearLayout) view).getChildAt(0);
            TextView text = (TextView) ((LinearLayout) view).getChildAt(1);
            
            // 获取主题色
            int themeColor = getThemeColor();
            
            // 通过颜色深浅区分选中状态
            if (view == selected) {
                // 选中项使用100%的主题色
                icon.setColorFilter(themeColor);
                text.setTextColor(themeColor);
                // 设置为选中状态，但不再使用背景变化
                view.setSelected(true);
            } else {
                // 未选中项使用70%透明度的主题色
                int alphaColor = (themeColor & 0x00FFFFFF) | 0xB0000000;
                icon.setColorFilter(alphaColor);
                text.setTextColor(alphaColor);
                view.setSelected(false);
            }
        }
        
        // 记录当前选中的导航项
        currentSelectedNav = selected;
    }
    
    // 获取当前主题色
    private int getThemeColor() {
        int themeColor = getResources().getColor(R.color.theme1_primary); // 默认主题色
        try {
            // 根据当前主题获取主题色
            int themeIndex = preferences.getInt(PREF_THEME, THEME_DEFAULT);
            switch (themeIndex) {
                case THEME_RED:
                    themeColor = getResources().getColor(R.color.theme2_primary);
                    break;
                case THEME_GREEN:
                    themeColor = getResources().getColor(R.color.theme3_primary);
                    break;
                case THEME_PURPLE:
                    themeColor = getResources().getColor(R.color.theme4_primary);
                    break;
                case THEME_PINK:
                    themeColor = getResources().getColor(R.color.theme5_primary);
                    break;
                case THEME_ORANGE:
                    themeColor = getResources().getColor(R.color.theme6_primary);
                    break;
                case THEME_YELLOW:
                    themeColor = getResources().getColor(R.color.theme7_primary);
                    break;
                case THEME_BROWN:
                    themeColor = getResources().getColor(R.color.theme8_primary);
                    break;
                case THEME_BLACK:
                    themeColor = getResources().getColor(R.color.theme9_primary);
                    break;
                default:
                    themeColor = getResources().getColor(R.color.theme1_primary);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取主题色失败", e);
        }
        return themeColor;
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

    private void initData() {
        // 初始化数据
        try {
            Log.d(TAG, "开始初始化数据...");
            
            // 初始化数据库组件
            taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
            if (taskDao == null) {
                throw new Exception("taskDao is null");
            }
            
            Log.d(TAG, "数据库初始化成功");
            
            // 数据加载异常处理
            loadingStateManager.setRetryListener(new LoadingStateManager.RetryListener() {
                @Override
                public void onRetry() {
                    Log.d(TAG, "用户触发重试加载数据");
                    initData();
                }
            });

            // 检查版本号，进行数据库升级后的修复操作
            int currentDbVersion = 0;
            try {
                currentDbVersion = getSharedPreferences("app_preferences", MODE_PRIVATE).getInt("db_version", 0);
            } catch (Exception e) {
                Log.e(TAG, "获取保存的数据库版本号失败", e);
            }
            
            // 数据库版本为6，但保存的版本低于6，说明可能刚刚进行过迁移
            int latestDbVersion = 6; // 当前最新版本号
            if (currentDbVersion < latestDbVersion) {
                Log.d(TAG, "检测到数据库版本升级: " + currentDbVersion + " -> " + latestDbVersion);
                
                // 修复可能由版本升级导致的问题
                try {
                    new Thread(() -> {
                        try {
                            String currentUserId = ParseUser.getCurrentUser().getObjectId();
                            // 获取所有任务
//                            List<Todo> allTasks = taskDao.getAllTasksForUser(currentUserId);
                            List<Todo> allTasks = taskDao.getAllTasks();
                            Log.d(TAG, "获取到 " + allTasks.size() + " 个任务进行迁移检查");
                            
                            int fixedCount = 0;
                            // 检查并修复每个任务
                            for (Todo task : allTasks) {
                                boolean needUpdate = false;
                                
                                // 检查并修复新增字段的默认值
                                if (task.pomodoroMinutes < 0) {
                                    task.pomodoroMinutes = 0;
                                    needUpdate = true;
                                }
                                
                                if (task.pomodoroCompletedCount < 0) {
                                    task.pomodoroCompletedCount = 0;
                                    needUpdate = true;
                                }
                                
                                // 如果需要更新，保存回数据库
                                if (needUpdate) {
                                    taskDao.updateTodo(task);
                                    fixedCount++;
                                }
                            }
                            
                            Log.d(TAG, "修复了 " + fixedCount + " 个受数据库迁移影响的任务");
                            
                            // 保存当前版本号
                            getSharedPreferences("app_preferences", MODE_PRIVATE)
                                .edit()
                                .putInt("db_version", latestDbVersion)
                                .apply();
                            
                        } catch (Exception e) {
                            Log.e(TAG, "版本迁移修复操作失败", e);
                        }
                    }).start();
                } catch (Exception e) {
                    Log.e(TAG, "创建版本修复线程失败", e);
                }
            }
            
            // 同步服务器数据
            ParseUser currentUser = ParseUser.getCurrentUser();
            if (currentUser != null) {
                Log.d(TAG, "当前登录用户: " + currentUser.getUsername());
                // 启动同步服务
                SyncWorker.triggerSyncNow(this);
            } else {
                Log.d(TAG, "未登录，跳过同步");
            }

            // 底部导航栏处理
            setupBottomNavigation();

            // 标记加载为成功
            loadingStateManager.showState(LoadingStateManager.STATE_SUCCESS);

        } catch (Exception e) {
            Log.e(TAG, "数据初始化失败: " + e.getMessage(), e);
            
            // 显示加载失败状态
            loadingStateManager.showState(LoadingStateManager.STATE_ERROR);
        }
    }

    // 定时刷新TasksFragment
    private void refreshTasksFragmentPeriodically() {
        final int[] count = {0};
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (tasksFragment == null) {
                        tasksFragment = (TasksFragment) getSupportFragmentManager().findFragmentByTag("TasksFragment");
                    }
                    if (tasksFragment != null) {
                        tasksFragment.loadTasks();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "定时刷新任务列表异常", e);
                }
                count[0]++;
                if (count[0] < 10) {
                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.post(runnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // 将"任务统计"菜单项标题改为"同步"
        MenuItem syncItem = menu.findItem(R.id.action_statistics);
        if (syncItem != null) {
            syncItem.setTitle("同步");
            syncItem.setIcon(android.R.drawable.stat_notify_sync);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_statistics) {
            // 点击"同步"按钮，自动同步数据
            Toast.makeText(this, "正在同步数据...", Toast.LENGTH_SHORT).show();
            SyncWorker.pullCloudToLocal(this);
            SyncWorker.pullTaskGroupsToLocal(this);
            SyncWorker.pushLocalToCloud(this);
            SyncWorker.pushTaskGroupsToCloud(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}




