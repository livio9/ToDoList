package com.example.todolist.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.todolist.R;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;

import java.util.List;

public class StatisticsFragment extends Fragment {
    private static final String TAG = "StatisticsFragment";
    
    private TextView textCompletionRate;
    private TextView textTasksCompleted;
    private ProgressBar progressCompletion;
    private TextView textWorkCount;
    private TextView textPersonalCount;
    private TextView textOtherCount;
    private TextView textTrendDescription;

    private TaskDao taskDao;

    public StatisticsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);
        
        try {
            // 初始化数据库
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
            
            // 初始化UI组件
            textCompletionRate = view.findViewById(R.id.textCompletionRate);
            textTasksCompleted = view.findViewById(R.id.textTasksCompleted);
            progressCompletion = view.findViewById(R.id.progressCompletion);
            textWorkCount = view.findViewById(R.id.textWorkCount);
            textPersonalCount = view.findViewById(R.id.textPersonalCount);
            textOtherCount = view.findViewById(R.id.textOtherCount);
            textTrendDescription = view.findViewById(R.id.textTrendDescription);
            
            // 加载统计数据
            loadStatistics();
            
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
        }
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }
    
    private void loadStatistics() {
        try {
            new Thread(() -> {
                try {
                    // 从数据库加载所有非删除的任务
                    List<Todo> tasks = taskDao.getVisibleTodos();
                    
                    // 计算统计信息
                    int totalTasks = tasks.size();
                    int completedTasks = 0;
                    int workTasks = 0;
                    int personalTasks = 0;
                    int otherTasks = 0;
                    
                    for (Todo task : tasks) {
                        if (task.completed) {
                            completedTasks++;
                        }
                        
                        switch (task.category) {
                            case "工作":
                                workTasks++;
                                break;
                            case "个人":
                                personalTasks++;
                                break;
                            default:
                                otherTasks++;
                                break;
                        }
                    }
                    
                    // 计算完成率
                    final int completionRate = totalTasks > 0 ? (completedTasks * 100) / totalTasks : 0;
                    
                    // 生成趋势描述
                    final String trendDescription = generateTrendDescription(completedTasks, totalTasks);
                    
                    // 在UI线程更新界面
                    final int finalCompletedTasks = completedTasks;
                    final int finalTotalTasks = totalTasks;
                    final int finalWorkTasks = workTasks;
                    final int finalPersonalTasks = personalTasks;
                    final int finalOtherTasks = otherTasks;
                    
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            // 更新完成率
                            textCompletionRate.setText("完成率: " + completionRate + "%");
                            textTasksCompleted.setText("已完成: " + finalCompletedTasks + " / " + finalTotalTasks);
                            progressCompletion.setProgress(completionRate);
                            
                            // 更新分类统计
                            textWorkCount.setText(String.valueOf(finalWorkTasks));
                            textPersonalCount.setText(String.valueOf(finalPersonalTasks));
                            textOtherCount.setText(String.valueOf(finalOtherTasks));
                            
                            // 更新趋势描述
                            textTrendDescription.setText(trendDescription);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "加载统计数据失败", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "创建统计线程失败", e);
        }
    }
    
    private String generateTrendDescription(int completed, int total) {
        if (total == 0) {
            return "暂无任务数据，无法分析趋势";
        }
        
        int completionRate = (completed * 100) / total;
        
        if (completionRate >= 80) {
            return "你的任务完成率非常高 (" + completionRate + "%)，继续保持！";
        } else if (completionRate >= 50) {
            return "你的任务完成率还不错 (" + completionRate + "%)，再接再厉！";
        } else if (completionRate >= 20) {
            return "你的任务完成率有点低 (" + completionRate + "%)，加油哦！";
        } else {
            return "你的任务完成率很低 (" + completionRate + "%)，需要更加努力了！";
        }
    }
} 