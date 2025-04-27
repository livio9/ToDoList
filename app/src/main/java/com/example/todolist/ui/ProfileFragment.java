package com.example.todolist.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.todolist.R;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.example.todolist.sync.SyncWorker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    
    private TextView textUserName;
    private TextView textUserEmail;
    private TextView textTotalTasks;
    private TextView textCompletedTasks;
    private TextView textCompletionRate;
    private Button buttonLogout;
    private LinearLayout layoutTheme;
    private LinearLayout layoutNotifications;
    private LinearLayout layoutSync;
    private LinearLayout layoutHelp;
    
    private FirebaseAuth auth;
    private TaskDao taskDao;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        try {
            // 初始化Firebase
            auth = FirebaseAuth.getInstance();
            
            // 初始化数据库
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
            
            // 初始化UI组件
            textUserName = view.findViewById(R.id.textUserName);
            textUserEmail = view.findViewById(R.id.textUserEmail);
            textTotalTasks = view.findViewById(R.id.textTotalTasks);
            textCompletedTasks = view.findViewById(R.id.textCompletedTasks);
            textCompletionRate = view.findViewById(R.id.textCompletionRate);
            
            // 设置用户信息
            setupUserInfo();
            
            // 加载任务统计数据
            loadTaskStatistics();
            
            // 设置设置项点击事件
            layoutTheme = view.findViewById(R.id.layoutTheme);
            layoutNotifications = view.findViewById(R.id.layoutNotifications);
            layoutSync = view.findViewById(R.id.layoutSync);
            layoutHelp = view.findViewById(R.id.layoutHelp);
            
            layoutTheme.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "主题设置功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            layoutNotifications.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "通知设置功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            layoutSync.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "正在同步数据...", Toast.LENGTH_SHORT).show();
                SyncWorker.pullCloudToLocal(requireContext());
                SyncWorker.pushLocalToCloud(requireContext());
            });
            
            layoutHelp.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "帮助与反馈功能即将上线", Toast.LENGTH_SHORT).show();
            });
            
            // 设置退出登录按钮
            buttonLogout = view.findViewById(R.id.buttonLogout);
            buttonLogout.setOnClickListener(v -> {
                logoutUser();
            });
            
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
        }
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTaskStatistics();
    }
    
    private void setupUserInfo() {
        try {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                // 设置用户名（使用邮箱前缀）
                String email = user.getEmail();
                String userName = email != null ? email.split("@")[0] : "用户";
                textUserName.setText(userName);
                textUserEmail.setText(email != null ? email : "未设置邮箱");
            } else {
                textUserName.setText("未登录用户");
                textUserEmail.setText("请登录账号");
            }
        } catch (Exception e) {
            Log.e(TAG, "设置用户信息失败", e);
            textUserName.setText("加载失败");
            textUserEmail.setText("请稍后重试");
        }
    }
    
    private void loadTaskStatistics() {
        try {
            new Thread(() -> {
                try {
                    // 加载任务数据
                    List<Todo> tasks = taskDao.getVisibleTodos();
                    
                    // 计算统计信息
                    int totalTasks = tasks.size();
                    int completedTasks = 0;
                    
                    for (Todo task : tasks) {
                        if (task.completed) {
                            completedTasks++;
                        }
                    }
                    
                    // 计算完成率
                    final int completionRate = totalTasks > 0 ? (completedTasks * 100) / totalTasks : 0;
                    
                    // 更新UI
                    final int finalTotalTasks = totalTasks;
                    final int finalCompletedTasks = completedTasks;
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            textTotalTasks.setText(String.valueOf(finalTotalTasks));
                            textCompletedTasks.setText(String.valueOf(finalCompletedTasks));
                            textCompletionRate.setText(completionRate + "%");
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "加载任务统计失败", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "创建统计线程失败", e);
        }
    }
    
    private void logoutUser() {
        try {
            // 退出登录
            FirebaseAuth.getInstance().signOut();
            
            // 取消定期同步
            SyncWorker.cancelPeriodicSync(requireContext());
            
            // 清空本地数据库
            new Thread(() -> {
                try {
                    taskDao.deleteAll();
                } catch (Exception e) {
                    Log.e(TAG, "清空数据库失败", e);
                }
            }).start();
            
            // 返回登录页面
            Intent loginIntent = new Intent(requireContext(), LoginActivity.class);
            loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(loginIntent);
            
            // 结束当前Activity
            if (getActivity() != null) {
                getActivity().finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "退出登录失败", e);
            Toast.makeText(requireContext(), "退出登录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 