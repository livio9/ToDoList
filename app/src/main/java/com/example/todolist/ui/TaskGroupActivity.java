package com.example.todolist.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todolist.R;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskGroup;
import com.example.todolist.data.TaskGroupDao;
import com.example.todolist.data.Todo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TaskGroupActivity extends AppCompatActivity {
    private TaskGroup taskGroup;
    private RecyclerView recyclerSubTasks;
    private TaskAdapter adapter;
    private List<Todo> subTasks = new ArrayList<>();
    private TextView textGroupName;
    private TextView textCategory;
    private TextView textEstimatedDays;
    private TextView textCreatedAt;
    private FloatingActionButton fabAddTask;
    private TaskGroupDao taskGroupDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_group);

        // 获取传递的代办集ID
        String groupId = getIntent().getStringExtra("group_id");
        if (groupId == null) {
            Toast.makeText(this, "代办集不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化DAO
        taskGroupDao = AppDatabase.getInstance(this).taskGroupDao();

        // 初始化视图
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("代办集详情");

        textGroupName = findViewById(R.id.textGroupName);
        textCategory = findViewById(R.id.textCategory);
        textEstimatedDays = findViewById(R.id.textEstimatedDays);
        textCreatedAt = findViewById(R.id.textCreatedAt);
        recyclerSubTasks = findViewById(R.id.recyclerSubTasks);
        fabAddTask = findViewById(R.id.fabAddTask);

        // 设置RecyclerView
        recyclerSubTasks.setLayoutManager(new LinearLayoutManager(this));
        
        // 创建适配器
        adapter = new TaskAdapter(this, subTasks);
        
        // 设置点击事件
        adapter.setOnItemClickListener(todo -> {
            // 点击子任务打开编辑页面
            Intent intent = new Intent(TaskGroupActivity.this, AddEditTaskActivity.class);
            intent.putExtra("todo", todo);
            intent.putExtra("parent_group_id", groupId);
            startActivity(intent);
        });
        
        // 设置长按事件
        adapter.setOnItemLongClickListener(todo -> {
            // 长按删除任务
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase.getInstance(TaskGroupActivity.this).taskDao().logicalDeleteTodo(todo.uuid);
                
                // 从代办集中移除
                if (taskGroup != null) {
                    taskGroup.subTaskIds.remove(todo.uuid);
                    taskGroupDao.insertTaskGroup(taskGroup);
                }
                
                runOnUiThread(() -> {
                    int position = subTasks.indexOf(todo);
                    if (position != -1) {
                        subTasks.remove(position);
                        adapter.notifyItemRemoved(position);
                        Toast.makeText(TaskGroupActivity.this, "任务已删除", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
        
        recyclerSubTasks.setAdapter(adapter);

        // 添加新任务按钮
        fabAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditTaskActivity.class);
            intent.putExtra("parent_group_id", groupId);
            startActivity(intent);
        });

        // 加载代办集和子任务
        loadTaskGroup(groupId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新代办集和子任务
        if (taskGroup != null) {
            loadTaskGroup(taskGroup.uuid);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTaskGroup(String groupId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            // 加载代办集
            taskGroup = taskGroupDao.getTaskGroupById(groupId);
            if (taskGroup == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "代办集不存在", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            // 加载子任务
            List<Todo> tasks = new ArrayList<>();
            if (taskGroup.subTaskIds != null && !taskGroup.subTaskIds.isEmpty()) {
                tasks = taskGroupDao.getSubTasksByIds(taskGroup.subTaskIds);
            }

            // 更新UI
            List<Todo> finalTasks = tasks;
            runOnUiThread(() -> {
                // 显示代办集信息
                textGroupName.setText(taskGroup.title);
                textCategory.setText("类别: " + taskGroup.category);
                textEstimatedDays.setText("预计完成天数: " + taskGroup.estimatedDays);
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                textCreatedAt.setText("创建时间: " + sdf.format(taskGroup.createdAt));

                // 更新子任务列表
                subTasks.clear();
                subTasks.addAll(finalTasks);
                adapter.notifyDataSetChanged();

                // 如果没有子任务，显示提示
                findViewById(R.id.textNoTasks).setVisibility(
                        subTasks.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
} 