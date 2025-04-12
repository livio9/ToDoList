package com.example.todolist.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.example.todolist.auth.LoginActivity;
import com.example.todolist.R;
import com.example.todolist.sync.SyncWorker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TaskDao taskDao;
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private Spinner spinnerTime;
    private Spinner spinnerCategory;
    private Spinner spinnerStatus;
    private List<Todo> allTasks = new ArrayList<>();
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate 开始");
        // 加载主界面布局
        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "布局加载完成");
        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // 初始化 Firebase Auth 和 Firestore
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        // 获取本地数据库 DAO
        taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        // 初始化 RecyclerView 和 Adapter
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(this, allTasks);
        recyclerView.setAdapter(adapter);

        // 配置 RecyclerView item 点击和长按事件
        adapter.setOnItemClickListener(todo -> {
            // 点击任务项，跳转到编辑页面
            Intent editIntent = new Intent(MainActivity.this, AddEditTaskActivity.class);
            editIntent.putExtra("todo", todo);
            startActivity(editIntent);
        });
        adapter.setOnItemLongClickListener(todo -> {
            // 长按任务项，弹出删除确认对话框
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("删除确认")
                    .setMessage("确定删除该待办事项吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        // 在本地数据库删除任务
                        new Thread(() -> taskDao.deleteTodo(todo)).start();
                        // 删除云端对应任务文档
                        if (auth.getCurrentUser() != null) {
                            firestore.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("tasks").document(todo.id)
                                    .delete();
                        }
                        // 从当前列表数据中移除，并刷新列表
                        allTasks.remove(todo);
                        applyFiltersAndRefresh();
                        Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 初始化筛选下拉菜单 Spinner
        spinnerTime = findViewById(R.id.spinnerTime);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        setupFilterSpinners();

        // 添加任务的浮动按钮点击事件
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            // 打开添加任务页面
            Intent addIntent = new Intent(MainActivity.this, AddEditTaskActivity.class);
            startActivity(addIntent);
        });

        // 安排 WorkManager 定时同步任务，并立即触发一次同步
        if (auth.getCurrentUser() != null) {
            SyncWorker.schedulePeriodicSync(getApplicationContext());
            SyncWorker.triggerSyncNow(getApplicationContext());
        }
        Log.d("MainActivity", "onCreate 结束");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 返回主界面时，从数据库查询最新任务列表，并根据当前筛选条件刷新显示
        new Thread(() -> {
            try {
                List<Todo> dbTasks = taskDao.getAll();
                if (dbTasks == null) {
                    Log.e("MainActivity", "数据库查询返回 null");
                }
                allTasks.clear();
                assert dbTasks != null;
                allTasks.addAll(dbTasks);
                runOnUiThread(this::applyFiltersAndRefresh);
            } catch (Exception e) {
                Log.e("MainActivity", "在 onResume 中发生异常", e);
            }
        }).start();
    }

    // 设置筛选 Spinner 的选项和值变化监听
    private void setupFilterSpinners() {
        // 时间筛选选项
        String[] timeOptions = {"全部时间", "今天", "本周内", "已过期"};
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, timeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTime.setAdapter(timeAdapter);
        // 类别筛选选项（包含“全部类别”）
        String[] categoryOptions = {"全部类别", "工作", "个人", "其他"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryOptions);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        // 状态筛选选项
        String[] statusOptions = {"全部", "已完成", "未完成"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        // 为 Spinner 设置选择监听，在选项改变时刷新列表
        spinnerTime.setOnItemSelectedListener(new SpinnerSelectionListener());
        spinnerCategory.setOnItemSelectedListener(new SpinnerSelectionListener());
        spinnerStatus.setOnItemSelectedListener(new SpinnerSelectionListener());
    }

    // 根据筛选条件过滤任务列表并刷新 RecyclerView 列表显示
    private void applyFiltersAndRefresh() {
        String timeFilter = spinnerTime.getSelectedItem().toString();
        String categoryFilter = spinnerCategory.getSelectedItem().toString();
        String statusFilter = spinnerStatus.getSelectedItem().toString();
        List<Todo> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        // 计算今天和本周的时间范围
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startToday = cal.getTimeInMillis();
        long endToday = startToday + 24 * 60 * 60 * 1000;
        long endWeek = startToday + 7 * 24 * 60 * 60 * 1000;
        for (Todo todo : allTasks) {
            // 状态筛选
            if (statusFilter.equals("已完成") && !todo.completed) continue;
            if (statusFilter.equals("未完成") && todo.completed) continue;
            // 类别筛选
            if (!categoryFilter.equals("全部类别") && !todo.category.equals(categoryFilter)) continue;
            // 时间筛选
            if (timeFilter.equals("今天")) {
                if (todo.time < startToday || todo.time >= endToday) continue;
            } else if (timeFilter.equals("本周内")) {
                if (todo.time < startToday || todo.time >= endWeek) continue;
            } else if (timeFilter.equals("已过期")) {
                if (todo.time >= now) continue;
            }
            // 若通过所有筛选条件，则加入列表
            filtered.add(todo);
        }
        // 更新 RecyclerView 显示过滤后的列表
        adapter.updateList(filtered);
    }

    // 下拉菜单选择事件监听内部类
    private class SpinnerSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            applyFiltersAndRefresh();
        }
        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 加载菜单（包含退出登录选项）
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            // 退出登录操作
            FirebaseAuth.getInstance().signOut();
            // 取消定时同步任务
            SyncWorker.cancelPeriodicSync(getApplicationContext());
            // 清空本地数据表
            new Thread(() -> taskDao.deleteAll()).start();
            // 回到登录界面
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
