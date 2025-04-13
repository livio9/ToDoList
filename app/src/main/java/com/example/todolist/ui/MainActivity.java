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
import java.util.UUID;

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

//    private void uploadTestTodo() {
//        FirebaseAuth auth = FirebaseAuth.getInstance();
//        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
//
//        if (auth.getCurrentUser() == null) {
//            Toast.makeText(this, "请先登录账号", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        String uid = auth.getCurrentUser().getUid();
//        String taskId = UUID.randomUUID().toString(); // 生成唯一任务 ID
//
//        // 构造一个测试 Todo 对象
//        Todo testTodo = new Todo();
//        testTodo.id = taskId;
//        testTodo.title = "测试任务（Hello Firestore）";
//        testTodo.place = "云端";
//        testTodo.category = "测试";
//        testTodo.completed = false;
//        testTodo.time = System.currentTimeMillis() + 3600 * 1000; // 一小时后
//        testTodo.updatedAt = System.currentTimeMillis();
//        testTodo.deleted = false;
//
//        // 上传到 Firestore
//        firestore.collection("users")
//                .document(uid)
//                .collection("tasks")
//                .document(taskId)
//                .set(testTodo)
//                .addOnSuccessListener(aVoid -> {
//                    Toast.makeText(this, "测试任务上传成功", Toast.LENGTH_SHORT).show();
//                    Log.d("FirestoreTest", "测试任务写入成功：" + taskId);
//                })
//                .addOnFailureListener(e -> {
//                    Toast.makeText(this, "测试任务上传失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
//                    Log.e("FirestoreTest", "写入失败", e);
//                });
//    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate 开始");
        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "布局加载完成");
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(this, allTasks);
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(todo -> {
            Intent editIntent = new Intent(MainActivity.this, AddEditTaskActivity.class);
            editIntent.putExtra("todo", todo);
            startActivity(editIntent);
        });
        adapter.setOnItemLongClickListener(todo -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("删除确认")
                    .setMessage("确定删除该待办事项吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        // 软删除操作
                        todo.deleted = true;
                        todo.updatedAt = System.currentTimeMillis();
                        new Thread(() -> taskDao.insertTodo(todo)).start();
                        if (auth.getCurrentUser() != null) {
                            firestore.collection("users")
                                    .document(auth.getCurrentUser().getUid())
                                    .collection("tasks").document(todo.id)
                                    .update("deleted", true, "updatedAt", todo.updatedAt);
                        }
                        allTasks.remove(todo);
                        applyFiltersAndRefresh();
                        Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        spinnerTime = findViewById(R.id.spinnerTime);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        setupFilterSpinners();

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent addIntent = new Intent(MainActivity.this, AddEditTaskActivity.class);
            startActivity(addIntent);
        });

        // 登录后立即拉取云端数据，恢复本地数据
        if (auth.getCurrentUser() != null) {
            SyncWorker.schedulePeriodicSync(getApplicationContext());
            SyncWorker.triggerSyncNow(getApplicationContext());
            // 增加拉云操作，避免本地为空造成的问题
            SyncWorker.pullCloudToLocal(getApplicationContext());
        }
        Log.d("MainActivity", "onCreate 结束");
//        uploadTestTodo(); // 测试上传一条任务

    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(() -> {
            try {
                List<Todo> dbTasks = taskDao.getVisibleTodos();
                if (dbTasks == null) {
                    Log.e("MainActivity", "数据库查询返回 null");
                }
                allTasks.clear();
                allTasks.addAll(dbTasks);
                runOnUiThread(this::applyFiltersAndRefresh);
            } catch (Exception e) {
                Log.e("MainActivity", "在 onResume 中发生异常", e);
            }
        }).start();
    }

    private void setupFilterSpinners() {
        String[] timeOptions = {"全部时间", "今天", "本周内", "已过期"};
        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, timeOptions);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTime.setAdapter(timeAdapter);

        String[] categoryOptions = {"全部类别", "工作", "个人", "其他"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryOptions);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        String[] statusOptions = {"全部", "已完成", "未完成"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        spinnerTime.setOnItemSelectedListener(new SpinnerSelectionListener());
        spinnerCategory.setOnItemSelectedListener(new SpinnerSelectionListener());
        spinnerStatus.setOnItemSelectedListener(new SpinnerSelectionListener());
    }

    private void applyFiltersAndRefresh() {
        String timeFilter = spinnerTime.getSelectedItem().toString();
        String categoryFilter = spinnerCategory.getSelectedItem().toString();
        String statusFilter = spinnerStatus.getSelectedItem().toString();
        List<Todo> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startToday = cal.getTimeInMillis();
        long endToday = startToday + 24 * 60 * 60 * 1000;
        long endWeek = startToday + 7 * 24 * 60 * 60 * 1000;
        for (Todo todo : allTasks) {
            if (statusFilter.equals("已完成") && !todo.completed) continue;
            if (statusFilter.equals("未完成") && todo.completed) continue;
            if (!categoryFilter.equals("全部类别") && !todo.category.equals(categoryFilter)) continue;
            if (timeFilter.equals("今天")) {
                if (todo.time < startToday || todo.time >= endToday) continue;
            } else if (timeFilter.equals("本周内")) {
                if (todo.time < startToday || todo.time >= endWeek) continue;
            } else if (timeFilter.equals("已过期")) {
                if (todo.time >= now) continue;
            }
            filtered.add(todo);
        }
        adapter.updateList(filtered);
    }

    private class SpinnerSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
            applyFiltersAndRefresh();
        }
        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            SyncWorker.cancelPeriodicSync(getApplicationContext());
            new Thread(() -> taskDao.deleteAll()).start();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return true;
        }
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
            refreshDataFromDb();
        }
    };

    private void refreshDataFromDb() {
        new Thread(() -> {
            List<Todo> dbTasks = taskDao.getVisibleTodos();
            runOnUiThread(() -> {
                allTasks.clear();
                allTasks.addAll(dbTasks);
                applyFiltersAndRefresh();
            });
        }).start();
    }

}


