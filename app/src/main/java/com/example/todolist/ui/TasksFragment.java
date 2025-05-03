package com.example.todolist.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todolist.R;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TasksFragment extends Fragment {
    private TaskDao taskDao;
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private View emptyView;
    private Chip spinnerTime;
    private Chip spinnerCategory;
    private Chip spinnerStatus;
    private List<Todo> allTasks = new ArrayList<>();

    private static final String TAG = "TasksFragment";

    public TasksFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tasks, container, false);


        try {
            // 初始化数据库
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        } catch (Exception e) {
            Log.e(TAG, "数据库访问失败", e);
            Toast.makeText(requireContext(), "数据库访问失败", Toast.LENGTH_SHORT).show();
            return view;
        }

        try {
            // 初始化任务列表UI组件
            recyclerView = view.findViewById(R.id.recyclerView);
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            emptyView = view.findViewById(R.id.emptyView);
            
            // 设置筛选器
            spinnerTime = view.findViewById(R.id.spinnerTime);
            spinnerCategory = view.findViewById(R.id.spinnerCategory);
            spinnerStatus = view.findViewById(R.id.spinnerStatus);
            setupFilterSpinners();

            // 设置任务列表适配器
            adapter = new TaskAdapter(requireContext(), allTasks);
            recyclerView.setAdapter(adapter);

            // 设置任务点击事件
            adapter.setOnItemClickListener(todo -> {
                Intent editIntent = new Intent(requireContext(), AddEditTaskActivity.class);
                editIntent.putExtra("todo", todo);
                startActivity(editIntent);
            });

            // 设置任务长按删除事件
            adapter.setOnItemLongClickListener(todo -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("删除确认")
                        .setMessage("确定删除该待办事项吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            try {
                                // 软删除操作
                                todo.deleted = true;
                                todo.clientUpdatedAt = System.currentTimeMillis();
                                new Thread(() -> {
                                    try {
                                        taskDao.insertTodo(todo);
                                    } catch (Exception e) {
                                        Log.e(TAG, "删除任务失败", e);
                                    }
                                }).start();
                                
                                try {
                                    ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
                                    query.whereEqualTo("uuid", todo.uuid);
                                    query.getFirstInBackground((object, e) -> {
                                        if (object != null) {
                                            object.put("deleted", true);
                                            object.put("clientUpdatedAt", todo.clientUpdatedAt);
                                            object.saveInBackground();
                                        }
                                    });

                                } catch (Exception e) {
                                    Log.e(TAG, "更新Firebase失败", e);
                                }
                                
                                allTasks.remove(todo);
                                applyFiltersAndRefresh();
                                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.e(TAG, "删除操作失败", e);
                                Toast.makeText(requireContext(), "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            // 设置添加任务按钮
            FloatingActionButton fabAdd = view.findViewById(R.id.fabAdd);
            fabAdd.setOnClickListener(v -> {
                Intent addIntent = new Intent(requireContext(), AddEditTaskActivity.class);
                startActivity(addIntent);
            });
            
            // 加载任务数据
            loadTasks();

        } catch (Exception e) {
            Log.e(TAG, "UI初始化失败", e);
        }

        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadTasks();
    }
    
    private void loadTasks() {
        try {
            new Thread(() -> {
                try {
                    Log.d(TAG, "正在加载所有非代办集任务...");
                    List<Todo> dbTasks = taskDao != null ? taskDao.getVisibleTodos() : new ArrayList<>();
                    if (dbTasks == null) {
                        Log.e(TAG, "数据库查询返回 null");
                        dbTasks = new ArrayList<>();
                    }
                    Log.d(TAG, "成功加载 " + dbTasks.size() + " 个非代办集任务");
                    allTasks.clear();
                    allTasks.addAll(dbTasks);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(this::applyFiltersAndRefresh);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "在加载任务中发生异常", e);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "加载任务失败", Toast.LENGTH_SHORT).show());
                    }
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "加载任务执行失败", e);
        }
    }

    private void setupFilterSpinners() {
        // 时间过滤器选项
        String[] timeOptions = {"全部时间", "今天", "本周内", "已过期"};
        spinnerTime.setText(timeOptions[0]);
        
        // 点击时显示选项对话框
        spinnerTime.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                .setTitle("选择时间过滤")
                .setItems(timeOptions, (dialog, which) -> {
                    spinnerTime.setText(timeOptions[which]);
                    applyFiltersAndRefresh();
                })
                .show();
        });

        // 类别过滤器选项
        String[] categoryOptions = {"全部类别", "工作", "个人", "学习", "其他"};
        spinnerCategory.setText(categoryOptions[0]);

        // 点击时显示选项对话框
        spinnerCategory.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                .setTitle("选择类别过滤")
                .setItems(categoryOptions, (dialog, which) -> {
                    spinnerCategory.setText(categoryOptions[which]);
                    applyFiltersAndRefresh();
                })
                .show();
        });

        // 状态过滤器选项
        String[] statusOptions = {"全部状态", "未完成", "已完成"};
        spinnerStatus.setText(statusOptions[0]);

        // 点击时显示选项对话框
        spinnerStatus.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                .setTitle("选择状态过滤")
                .setItems(statusOptions, (dialog, which) -> {
                    spinnerStatus.setText(statusOptions[which]);
                    applyFiltersAndRefresh();
                })
                .show();
        });
    }

    private void applyFiltersAndRefresh() {
        // 创建过滤后的任务列表
        List<Todo> filteredTasks = new ArrayList<>(allTasks);
        
        // 应用时间过滤
        String timeFilter = spinnerTime.getText().toString();
        if (!timeFilter.equals("全部时间")) {
            Calendar now = Calendar.getInstance();
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            
            Calendar endOfWeek = Calendar.getInstance();
            endOfWeek.add(Calendar.DAY_OF_YEAR, 7);
            
            filteredTasks.removeIf(todo -> {
                Calendar todoTime = Calendar.getInstance();
                todoTime.setTimeInMillis(todo.time);
                
                if (timeFilter.equals("今天")) {
                    Calendar tomorrow = (Calendar) today.clone();
                    tomorrow.add(Calendar.DAY_OF_YEAR, 1);
                    return !(todoTime.compareTo(today) >= 0 && todoTime.compareTo(tomorrow) < 0);
                } else if (timeFilter.equals("本周内")) {
                    return !(todoTime.compareTo(now) >= 0 && todoTime.compareTo(endOfWeek) <= 0);
                } else if (timeFilter.equals("已过期")) {
                    return todoTime.compareTo(now) >= 0;
                }
                return false;
            });
        }
        
        // 应用类别过滤
        String categoryFilter = spinnerCategory.getText().toString();
        if (!categoryFilter.equals("全部类别")) {
            filteredTasks.removeIf(todo -> !todo.category.equals(categoryFilter));
        }
        
        // 应用状态过滤
        String statusFilter = spinnerStatus.getText().toString();
        if (statusFilter.equals("未完成")) {
            filteredTasks.removeIf(todo -> todo.completed);
        } else if (statusFilter.equals("已完成")) {
            filteredTasks.removeIf(todo -> !todo.completed);
        }
        
        // 更新适配器数据
        adapter.updateData(filteredTasks);
        
        // 更新空视图状态
        emptyView.setVisibility(filteredTasks.isEmpty() ? View.VISIBLE : View.GONE);
    }
} 