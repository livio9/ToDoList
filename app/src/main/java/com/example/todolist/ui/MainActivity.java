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
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.R;
import com.example.todolist.sync.SyncWorker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
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
            
            try {
                auth = FirebaseAuth.getInstance();
                firestore = FirebaseFirestore.getInstance();
            } catch (Exception e) {
                Log.e(TAG, "Firebase初始化失败", e);
                auth = null;
                firestore = null;
                Toast.makeText(this, "云同步功能不可用", Toast.LENGTH_SHORT).show();
            }

            try {
                taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
                if (taskDao == null) {
                    throw new Exception("taskDao is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "数据库访问失败", e);
                Toast.makeText(this, "数据库访问失败，应用可能无法正常工作", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            
            try {
                // 初始化Fragment
                initFragments();
                
                // 设置底部导航
                setupBottomNavigation();
            } catch (Exception e) {
                Log.e(TAG, "UI初始化失败", e);
            }
            
            // 登录后立即拉取云端数据，恢复本地数据
            try {
                if (auth != null && auth.getCurrentUser() != null) {
                    SyncWorker.schedulePeriodicSync(getApplicationContext());
                    SyncWorker.triggerSyncNow(getApplicationContext());
                    // 增加拉云操作，避免本地为空造成的问题
                    SyncWorker.pullCloudToLocal(getApplicationContext());
                }
            } catch (Exception e) {
                Log.e(TAG, "同步操作失败", e);
            }
            
            Log.d(TAG, "onCreate 结束");
        } catch (Exception e) {
            Log.e(TAG, "onCreate总体执行失败", e);
            // 如果整个onCreate失败，显示一个提示并重新启动应用
            Toast.makeText(this, "应用初始化失败，请稍后重试", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fragment now handles the task loading
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 移除原有菜单，因为功能已移至底部导航栏和个人页面
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(dataUpdateReceiver, new IntentFilter("com.example.todolist.ACTION_DATA_UPDATED"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(dataUpdateReceiver);
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
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f);
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.9f, 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.9f, 1f);
        
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);
        scaleUpX.setDuration(100);
        scaleUpY.setDuration(100);
        
        AnimatorSet scaleDown = new AnimatorSet();
        scaleDown.play(scaleDownX).with(scaleDownY);
        scaleDown.setInterpolator(new AccelerateDecelerateInterpolator());
        
        AnimatorSet scaleUp = new AnimatorSet();
        scaleUp.play(scaleUpX).with(scaleUpY);
        scaleUp.setInterpolator(new AccelerateDecelerateInterpolator());
        
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(scaleDown).before(scaleUp);
        animatorSet.start();
    }
    
    // 积分系统方法
    public int getUserPoints() {
        return preferences.getInt(PREF_USER_POINTS, 0);
    }
    
    public void addUserPoints(int points) {
        int currentPoints = getUserPoints();
        int newPoints = currentPoints + points;
        
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREF_USER_POINTS, newPoints);
        
        // 增加已完成任务数量
        int completedTasks = preferences.getInt(PREF_COMPLETED_TASKS, 0);
        editor.putInt(PREF_COMPLETED_TASKS, completedTasks + 1);
        
        editor.apply();
        
        // 通知个人中心页面更新积分
        Intent intent = new Intent("com.example.todolist.ACTION_POINTS_UPDATED");
        intent.putExtra("new_points", newPoints);
        sendBroadcast(intent);
    }
    
    public int getCompletedTasksCount() {
        return preferences.getInt(PREF_COMPLETED_TASKS, 0);
    }

    /**
     * 设置应用主题颜色
     * @param themeId 主题ID
     */
    public void setApplicationTheme(int themeId) {
        try {
            // 保存主题设置到SharedPreferences
            preferences.edit().putInt(PREF_THEME, themeId).apply();
            
            // 通知用户需要重新启动应用以完全应用主题
            Toast.makeText(this, "主题已更改，即将重启应用以应用新主题", Toast.LENGTH_SHORT).show();
            
            // 延迟500毫秒后重新启动应用
            new Handler().postDelayed(() -> {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }, 500);
        } catch (Exception e) {
            Log.e(TAG, "应用主题失败", e);
        }
    }
    
    /**
     * 根据保存的主题ID设置应用主题
     */
    private void applyTheme() {
        int themeId = preferences.getInt(PREF_THEME, THEME_DEFAULT);
        
        switch (themeId) {
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
            case THEME_DEFAULT:
            default:
                setTheme(R.style.Theme_ToDoList);
                break;
        }
    }
}


