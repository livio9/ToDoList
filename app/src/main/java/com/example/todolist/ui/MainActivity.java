package com.example.todolist.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
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
    private TaskDao taskDao;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private BottomNavigationView bottomNavigation;
    
    private TasksFragment tasksFragment;
    private TaskGroupsFragment taskGroupsFragment;
    private StatisticsFragment statisticsFragment;
    private ProfileFragment profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
                Toolbar toolbar = findViewById(R.id.toolbar);
                setSupportActionBar(toolbar);
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
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }
}


