package com.example.todolist.ui;

import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.example.todolist.R;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsActivity extends BaseActivity {
    private TextView textTotalTasks;
    private TextView textCompletedTasks;
    private TextView textPendingTasks;
    private ProgressBar progressCompletionRate;
    private TextView textCompletionRate;
    private ProgressBar progressWork;
    private TextView textWorkCount;
    private ProgressBar progressPersonal;
    private TextView textPersonalCount;
    private ProgressBar progressOther;
    private TextView textOtherCount;
    private TaskDao taskDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 初始化视图
        initViews();
        
        // 初始化数据库
        taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        
        // 加载统计数据
        loadStatistics();
    }

    private void initViews() {
        // 任务总览相关视图
        textTotalTasks = findViewById(R.id.textTotalTasks);
        textCompletedTasks = findViewById(R.id.textCompletedTasks);
        textPendingTasks = findViewById(R.id.textPendingTasks);
        
        // 完成率相关视图
        progressCompletionRate = findViewById(R.id.progressCompletionRate);
        textCompletionRate = findViewById(R.id.textCompletionRate);
        
        // 分类统计相关视图
        progressWork = findViewById(R.id.progressWork);
        textWorkCount = findViewById(R.id.textWorkCount);
        progressPersonal = findViewById(R.id.progressPersonal);
        textPersonalCount = findViewById(R.id.textPersonalCount);
        progressOther = findViewById(R.id.progressOther);
        textOtherCount = findViewById(R.id.textOtherCount);
    }

    private void loadStatistics() {
        // 在后台线程中获取所有（未删除的）任务数据
        new Thread(() -> {
            String currentUserId = CurrentUserUtil.getCurrentUserId();
            // 获取所有可见任务
            List<Todo> allTasks = taskDao.getVisibleTasksForUser();
            
            // 如果没有任务，设置默认值并返回
            if (allTasks == null || allTasks.isEmpty()) {
                runOnUiThread(this::setDefaultValues);
                return;
            }
            
            // 统计数据计算
            final int totalTasks = allTasks.size();
            int completedTasks = 0;
            
            // 分类任务计数
            Map<String, Integer> categoryCounts = new HashMap<>();
            categoryCounts.put("工作", 0);
            categoryCounts.put("个人", 0);
            categoryCounts.put("其他", 0);
            
            // 统计各项数据
            for (Todo task : allTasks) {
                // 计算已完成任务
                if (task.completed) {
                    completedTasks++;
                }
                
                // 计算各分类任务数量
                String category = task.category;
                if (category != null && !category.isEmpty()) {
                    categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
                } else {
                    // 没有分类的任务归为"其他"
                    categoryCounts.put("其他", categoryCounts.get("其他") + 1);
                }
            }
            
            // 计算未完成任务
            final int pendingTasks = totalTasks - completedTasks;
            final int completedTasksFinal = completedTasks;
            
            // 计算完成率
            final int completionRate = (totalTasks > 0) ? (completedTasksFinal * 100 / totalTasks) : 0;
            
            // 获取各分类计数
            final int workCount = categoryCounts.get("工作");
            final int personalCount = categoryCounts.get("个人");
            final int otherCount = categoryCounts.get("其他");
            
            // 计算分类比例进度（最大100）
            final int workProgress = (totalTasks > 0) ? (workCount * 100 / totalTasks) : 0;
            final int personalProgress = (totalTasks > 0) ? (personalCount * 100 / totalTasks) : 0;
            final int otherProgress = (totalTasks > 0) ? (otherCount * 100 / totalTasks) : 0;
            
            // 在UI线程更新界面
            runOnUiThread(() -> {
                // 更新任务总览数据
                textTotalTasks.setText(String.valueOf(totalTasks));
                textCompletedTasks.setText(String.valueOf(completedTasksFinal));
                textPendingTasks.setText(String.valueOf(pendingTasks));
                
                // 更新完成率
                progressCompletionRate.setProgress(completionRate);
                textCompletionRate.setText(completionRate + "%");
                
                // 更新分类统计
                textWorkCount.setText(String.valueOf(workCount));
                progressWork.setProgress(workProgress);
                
                textPersonalCount.setText(String.valueOf(personalCount));
                progressPersonal.setProgress(personalProgress);
                
                textOtherCount.setText(String.valueOf(otherCount));
                progressOther.setProgress(otherProgress);
            });
        }).start();
    }
    
    private void setDefaultValues() {
        // 任务总览默认值
        textTotalTasks.setText("0");
        textCompletedTasks.setText("0");
        textPendingTasks.setText("0");
        
        // 完成率默认值
        progressCompletionRate.setProgress(0);
        textCompletionRate.setText("0%");
        
        // 分类统计默认值
        textWorkCount.setText("0");
        progressWork.setProgress(0);
        textPersonalCount.setText("0");
        progressPersonal.setProgress(0);
        textOtherCount.setText("0");
        progressOther.setProgress(0);
    }
} 