# Android基础架构详解

## 项目目录结构

ToDoList采用标准的分层目录结构，遵循关注点分离原则，有助于代码组织和维护：

```
app/
├── src/main/
│   ├── java/com/example/todolist/
│   │   ├── data/          # 数据层：模型类、数据库和存储操作
│   │   │   ├── model/     # 数据模型实体类
│   │   │   ├── dao/       # 数据访问对象接口
│   │   │   └── repository/# 数据仓库层
│   │   ├── ui/            # 用户界面层：Activity、Fragment和适配器
│   │   │   ├── activity/  # 应用的Activity类
│   │   │   ├── fragment/  # 各种界面Fragment
│   │   │   ├── adapter/   # RecyclerView适配器
│   │   │   ├── dialog/    # 自定义对话框
│   │   │   └── widget/    # 自定义视图组件
│   │   ├── utils/         # 工具类：辅助函数和通用逻辑
│   │   ├── sync/          # 同步模块：数据云同步相关
│   │   ├── auth/          # 认证模块：用户登录与注册
│   │   ├── ai/            # AI功能：智能任务分解
│   │   ├── service/       # 后台服务：如通知服务、同步服务
│   │   └── TodoList.java  # 应用主类：初始化全局配置
│   ├── res/               # 资源文件
│   │   ├── layout/        # 布局XML文件
│   │   ├── drawable/      # 图像资源
│   │   ├── values/        # 常量值：字符串、颜色、尺寸等
│   │   ├── menu/          # 菜单定义
│   │   └── xml/           # 其他XML配置
│   └── AndroidManifest.xml # 应用配置清单
└── build.gradle           # 模块构建配置
```

这种分层结构的优势：
- **模块化设计**：每个模块负责特定功能，便于理解和维护
- **代码复用**：通用功能集中在工具类中
- **测试友好**：各层可以单独测试
- **团队协作**：多人开发时可以并行工作在不同模块
- **扩展性好**：新功能可以作为新模块添加，不影响现有代码

## 四大核心组件

Android系统的四大核心组件是构建应用的基础，在ToDoList中对这些组件进行了全面应用：

### 1. Activity

Activity是用户与应用交互的入口点，负责UI界面的展示和用户输入处理。ToDoList中的主要Activity包括：

- **MainActivity**：应用主界面，管理底部导航和Fragment切换
- **AddEditTaskActivity**：任务添加和编辑界面
- **TaskDetailActivity**：任务详情查看界面
- **LoginActivity**：用户登录界面

关键实现示例：
```java
public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private TasksFragment tasksFragment;
    private TaskGroupsFragment taskGroupsFragment;
    private StatisticsFragment statisticsFragment;
    private ProfileFragment profileFragment;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化Fragment实例
        tasksFragment = new TasksFragment();
        taskGroupsFragment = new TaskGroupsFragment();
        statisticsFragment = new StatisticsFragment();
        profileFragment = new ProfileFragment();
        
        // 设置底部导航
        setupBottomNavigation();
        
        // 默认显示任务列表Fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, tasksFragment)
                .commit();
        }
    }
    
    private void setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            
            // 根据选择的菜单项切换Fragment
            if (itemId == R.id.navigation_tasks) {
                selectedFragment = tasksFragment;
                setTitle(R.string.title_tasks);
            } else if (itemId == R.id.navigation_groups) {
                selectedFragment = taskGroupsFragment;
                setTitle(R.string.title_task_groups);
            } else if (itemId == R.id.navigation_statistics) {
                selectedFragment = statisticsFragment;
                setTitle(R.string.title_statistics);
            } else if (itemId == R.id.navigation_profile) {
                selectedFragment = profileFragment;
                setTitle(R.string.title_profile);
            }
            
            // 执行Fragment切换
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, selectedFragment)
                    .commit();
                return true;
            }
            
            return false;
        });
    }
}
```

### 2. Service

Service用于在后台执行长时间运行的操作，不提供用户界面。ToDoList应用中的Service主要用于：

- **SyncService**：在后台同步数据到云端
- **ReminderService**：处理任务到期提醒
- **PomodoroTimerService**：管理番茄工作法计时器

关键实现示例：
```java
public class PomodoroTimerService extends Service {
    private static final String CHANNEL_ID = "pomodoro_timer_channel";
    private CountDownTimer countDownTimer;
    private long remainingTimeMillis;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private final IBinder binder = new PomodoroTimerBinder();
    
    // 用于与Activity通信的Binder
    public class PomodoroTimerBinder extends Binder {
        PomodoroTimerService getService() {
            return PomodoroTimerService.this;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                // 获取计时时长，默认25分钟
                long durationMillis = intent.getLongExtra(EXTRA_DURATION, 25 * 60 * 1000);
                startTimer(durationMillis);
            } else if (ACTION_PAUSE.equals(action)) {
                pauseTimer();
            } else if (ACTION_RESUME.equals(action)) {
                resumeTimer();
            } else if (ACTION_STOP.equals(action)) {
                stopTimer();
            }
        }
        
        return START_STICKY;
    }
    
    private void startTimer(long durationMillis) {
        // 创建通知渠道
        createNotificationChannel();
        
        // 启动前台服务并显示通知
        startForeground(NOTIFICATION_ID, buildNotification("Pomodoro Timer Running", 100, 0));
        
        // 启动计时器
        remainingTimeMillis = durationMillis;
        isRunning = true;
        isPaused = false;
        
        countDownTimer = new CountDownTimer(remainingTimeMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingTimeMillis = millisUntilFinished;
                
                // 更新通知显示剩余时间
                int progress = (int) ((durationMillis - millisUntilFinished) * 100 / durationMillis);
                updateNotification(formatTime(millisUntilFinished), progress);
                
                // 广播计时器更新事件
                Intent tickIntent = new Intent(ACTION_TIMER_TICK);
                tickIntent.putExtra(EXTRA_TIME_REMAINING, millisUntilFinished);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(tickIntent);
            }
            
            @Override
            public void onFinish() {
                isRunning = false;
                remainingTimeMillis = 0;
                
                // 广播计时器完成事件
                Intent finishIntent = new Intent(ACTION_TIMER_FINISH);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(finishIntent);
                
                // 更新通知
                updateNotification("Completed!", 100);
                
                // 停止前台服务
                stopForeground(false);
            }
        }.start();
    }
    
    // 其他方法：pauseTimer(), resumeTimer(), stopTimer(), 通知相关方法等
    // ...
}
```

### 3. BroadcastReceiver

BroadcastReceiver用于接收和响应系统或应用发出的广播消息。ToDoList中的主要接收器包括：

- **TaskReminderReceiver**：接收任务提醒闹钟广播
- **BootCompletedReceiver**：接收系统启动完成广播，重新设置任务提醒
- **DataSyncReceiver**：接收数据同步完成广播

关键实现示例：
```java
public class TaskReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 从Intent中获取任务ID
        String taskId = intent.getStringExtra("task_id");
        if (taskId == null) return;
        
        // 从数据库获取任务详情
        AppExecutors.getInstance().diskIO().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            Todo task = db.taskDao().getTodoById(taskId);
            
            if (task != null && !task.completed && !task.deleted) {
                // 在主线程创建并显示通知
                AppExecutors.getInstance().mainThread().execute(() -> {
                    showTaskReminderNotification(context, task);
                });
            }
        });
    }
    
    private void showTaskReminderNotification(Context context, Todo task) {
        // 创建通知渠道
        createNotificationChannel(context);
        
        // 创建查看任务的PendingIntent
        Intent viewIntent = new Intent(context, TaskDetailActivity.class);
        viewIntent.putExtra("task_id", task.id);
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent viewPendingIntent = PendingIntent.getActivity(
                context, 0, viewIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // 创建"标记完成"的PendingIntent
        Intent completeIntent = new Intent(context, TaskActionReceiver.class);
        completeIntent.setAction("com.example.todolist.ACTION_COMPLETE_TASK");
        completeIntent.putExtra("task_id", task.id);
        PendingIntent completePendingIntent = PendingIntent.getBroadcast(
                context, 1, completeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("任务提醒")
                .setContentText(task.title)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(task.title + (task.place != null ? " @ " + task.place : "")))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(viewPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_check, "完成", completePendingIntent);
        
        // 显示通知
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(task.id.hashCode(), builder.build());
        }
    }
    
    private void createNotificationChannel(Context context) {
        // 创建通知渠道（仅Android 8.0及以上需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Task Reminders";
            String description = "Notifications for task due dates";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
```

### 4. ContentProvider

ContentProvider用于管理数据共享，使应用数据可以被其他应用访问。ToDoList主要通过Room实现数据访问，但也为未来的数据共享功能预留了扩展：

```java
public class TaskContentProvider extends ContentProvider {
    private static final String AUTHORITY = "com.example.todolist.provider";
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    
    public static final Uri TASKS_CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath("tasks").build();
    
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int TASKS = 100;
    private static final int TASK_ID = 101;
    
    static {
        uriMatcher.addURI(AUTHORITY, "tasks", TASKS);
        uriMatcher.addURI(AUTHORITY, "tasks/#", TASK_ID);
    }
    
    private AppDatabase database;
    
    @Override
    public boolean onCreate() {
        database = AppDatabase.getInstance(getContext());
        return true;
    }
    
    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final SQLiteDatabase db = database.getOpenHelper().getReadableDatabase();
        Cursor cursor;
        
        switch (uriMatcher.match(uri)) {
            case TASKS:
                cursor = db.query("todos", projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case TASK_ID:
                String id = uri.getLastPathSegment();
                cursor = db.query("todos", projection, "_id=?", new String[]{id}, null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }
    
    // 实现其他ContentProvider方法...
    // getType(), insert(), update(), delete()
}
```

## Activity生命周期详解

Activity生命周期是Android应用开发中的核心概念，正确管理生命周期对于创建流畅且无错误的用户体验至关重要。ToDoList应用中对生命周期的处理遵循了最佳实践：

### 生命周期完整流程

```java
public class AddEditTaskActivity extends AppCompatActivity {
    private Todo currentTask;
    private EditText editTextTitle;
    private EditText editTextPlace;
    private TextView textViewDate;
    private TextView textViewTime;
    private Spinner spinnerCategory;
    private CheckBox checkBoxPomodoro;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_task);
        
        // 初始化视图组件
        editTextTitle = findViewById(R.id.editTextTitle);
        editTextPlace = findViewById(R.id.editTextPlace);
        textViewDate = findViewById(R.id.textViewDate);
        textViewTime = findViewById(R.id.textViewTime);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        checkBoxPomodoro = findViewById(R.id.checkBoxPomodoro);
        
        // 恢复保存的实例状态
        if (savedInstanceState != null) {
            String savedTaskId = savedInstanceState.getString("current_task_id");
            if (savedTaskId != null) {
                loadTaskById(savedTaskId);
            }
        } else {
            // 检查是否为编辑现有任务
            Intent intent = getIntent();
            String taskId = intent.getStringExtra("task_id");
            if (taskId != null) {
                loadTaskById(taskId);
            }
        }
        
        // 设置UI事件监听器
        setupEventListeners();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Activity即将可见，可以进行准备工作
        Log.d(TAG, "AddEditTaskActivity: onStart()");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Activity现在处于前台并可交互
        // 适合刷新动态数据或开始动画
        Log.d(TAG, "AddEditTaskActivity: onResume()");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Activity失去焦点，用户即将离开
        // 适合保存草稿数据，停止动画或媒体播放
        saveTaskDraft();
        Log.d(TAG, "AddEditTaskActivity: onPause()");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Activity不再可见
        // 释放可在后台保留的资源
        Log.d(TAG, "AddEditTaskActivity: onStop()");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Activity即将被销毁
        // 释放所有剩余资源
        Log.d(TAG, "AddEditTaskActivity: onDestroy()");
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存关键数据以便在Activity重建时恢复
        if (currentTask != null) {
            outState.putString("current_task_id", currentTask.id);
        }
        Log.d(TAG, "AddEditTaskActivity: onSaveInstanceState()");
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 从保存的状态恢复数据
        // 通常onCreate会处理，这里是额外保障
        Log.d(TAG, "AddEditTaskActivity: onRestoreInstanceState()");
    }
    
    // 加载任务数据
    private void loadTaskById(String taskId) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            // 在后台线程中从数据库加载任务
            AppDatabase db = AppDatabase.getInstance(this);
            Todo task = db.taskDao().getTodoById(taskId);
            
            // 切换到主线程更新UI
            runOnUiThread(() -> {
                if (task != null) {
                    currentTask = task;
                    populateUI(task);
                }
            });
        });
    }
    
    // 填充UI组件
    private void populateUI(Todo task) {
        editTextTitle.setText(task.title);
        editTextPlace.setText(task.place);
        
        // 设置日期和时间
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(task.time);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        textViewDate.setText(dateFormat.format(calendar.getTime()));
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        textViewTime.setText(timeFormat.format(calendar.getTime()));
        
        // 设置类别
        if (task.category != null) {
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerCategory.getAdapter();
            int position = adapter.getPosition(task.category);
            if (position >= 0) {
                spinnerCategory.setSelection(position);
            }
        }
        
        // 设置番茄工作法选项
        checkBoxPomodoro.setChecked(task.pomodoroEnabled != null && task.pomodoroEnabled);
    }
    
    // 保存草稿
    private void saveTaskDraft() {
        // 获取用户输入的数据并保存到偏好设置中
        SharedPreferences prefs = getSharedPreferences("task_drafts", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("draft_title", editTextTitle.getText().toString());
        editor.putString("draft_place", editTextPlace.getText().toString());
        editor.putString("draft_date", textViewDate.getText().toString());
        editor.putString("draft_time", textViewTime.getText().toString());
        editor.putString("draft_category", spinnerCategory.getSelectedItem().toString());
        editor.putBoolean("draft_pomodoro", checkBoxPomodoro.isChecked());
        
        editor.apply();
    }
}
```

### 生命周期管理最佳实践

1. **初始化资源**：在`onCreate()`中初始化UI组件、设置监听器和加载初始数据
2. **开始/恢复操作**：在`onResume()`中开始动画、刷新数据和注册广播接收器
3. **暂停操作**：在`onPause()`中保存草稿数据、停止动画和取消注册广播接收器 
4. **保存状态**：在`onSaveInstanceState()`中保存关键数据，应对配置变更（如屏幕旋转）情况
5. **释放资源**：在`onStop()`和`onDestroy()`中释放资源，避免内存泄漏
6. **适当的异步操作**：耗时操作（如数据库访问）放在后台线程执行，UI更新在主线程完成 

## OkHttp网络请求实现

### 1. 依赖配置
项目使用OkHttp 4.11.0版本进行网络请求，在`build.gradle.kts`中配置：
```kotlin
// OkHttp 网络请求库
implementation("com.squareup.okhttp3:okhttp:4.11.0")
```

### 2. 主要应用场景
项目主要在AI任务分解模块中使用OkHttp，用于与OpenRouter.ai API进行通信，实现智能任务分解功能。

### 3. OkHttp配置
在`TaskDecomposer.java`中配置了OkHttpClient实例，设置了合理的超时时间：
```java
private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时
        .readTimeout(30, TimeUnit.SECONDS)     // 读取超时
        .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时
        .build();
```

### 4. 网络请求实现
#### 4.1 请求构建
使用OkHttp的Builder模式构建请求：
```java
Request request = new Request.Builder()
        .url(BASE_URL + "/chat/completions")
        .addHeader("Authorization", "Bearer " + API_KEY)
        .post(RequestBody.create(requestBody.toString(), JSON))
        .build();
```

#### 4.2 请求执行
使用同步方式执行请求，并处理响应：
```java
try (Response response = CLIENT.newCall(request).execute()) {
    if (!response.isSuccessful()) {
        throw new IOException("API请求失败: " + response.code() + " " + response.message());
    }
    String responseBody = response.body().string();
    // 处理响应数据...
}
```

### 5. 错误处理
实现了完整的错误处理机制：
- HTTP状态码检查
- API错误响应处理
- JSON解析异常处理
- 网络异常处理

### 6. 性能优化
1. 使用单例OkHttpClient实例
2. 设置合理的超时时间
3. 使用try-with-resources自动关闭响应
4. 异步处理响应数据

### 7. 安全性考虑
1. API密钥通过Authorization头部传递
2. 使用HTTPS协议
3. 请求体使用JSON格式，确保数据安全传输

### 8. 最佳实践
1. 统一的错误处理机制
2. 清晰的日志记录
3. 合理的超时设置
4. 规范的代码结构

### 9. 未来优化方向
1. 实现请求重试机制
2. 添加请求缓存
3. 实现请求拦截器
4. 添加网络状态监控
5. 实现请求队列管理 