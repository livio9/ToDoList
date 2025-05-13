package com.example.todolist.ui;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.TaskGroupDao;
import com.example.todolist.data.TaskGroup;
import com.example.todolist.data.Todo;
import com.example.todolist.R;
import com.example.todolist.ai.TaskDecomposer;
import com.parse.ParseACL;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import org.json.JSONException;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import com.example.todolist.sync.SyncWorker;

public class AddEditTaskActivity extends BaseActivity {
    private static final String TAG = "AddEditTaskActivity";
    private TextInputEditText editTitle;
    private EditText editPlace;
    private MaterialAutoCompleteTextView spinnerCategory;
    private MaterialAutoCompleteTextView spinnerPriorityInput;
    private TextView textDateTime;
    private SwitchMaterial checkCompleted;
    private SwitchMaterial switchPomodoro;
    private Button buttonSave;
    private Button buttonDelete;
    private Button buttonAiDecompose;
    private MaterialCardView pomodoroCard;
    private Button buttonStartPomodoro;
    private TextView textPomodoroStatus;
    private TextView textPomodoroStatsTask;
    private ImageView imagePomodoroIcon;
    private TaskDao taskDao;
    private TaskGroupDao taskGroupDao;
    private Todo currentTodo;         // 编辑模式下传入的任务对象
    private Calendar selectedCalendar; // 选定的日期时间
    private boolean isTaskGroupMode = false; // 是否为代办集模式
    private String parentGroupId = null; // 父代办集ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_task);
        
        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        // 检查是否为创建代办集或普通任务
        isTaskGroupMode = getIntent().getBooleanExtra("task_group_mode", false);
        parentGroupId = getIntent().getStringExtra("parent_group_id");
        
        // 设置标题
        if (isTaskGroupMode) {
            getSupportActionBar().setTitle(getIntent().hasExtra("todo") ? "编辑代办集" : "创建代办集");
        } else {
            getSupportActionBar().setTitle(getIntent().hasExtra("todo") ? "编辑任务" : "添加任务");
        }
        
        toolbar.setNavigationOnClickListener(v -> {
            finish(); // 返回上一个界面
        });
        
        // 初始化 DAO
        taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        taskGroupDao = AppDatabase.getInstance(getApplicationContext()).taskGroupDao();
        
        // 获取界面控件引用
        editTitle = findViewById(R.id.editTitle);
        editPlace = findViewById(R.id.editPlace);
        spinnerCategory = findViewById(R.id.spinnerCategoryInput);
        spinnerPriorityInput = findViewById(R.id.spinnerPriorityInput);
        textDateTime = findViewById(R.id.textDateTime);
        checkCompleted = findViewById(R.id.checkCompleted);
        buttonSave = findViewById(R.id.buttonSave);
        buttonDelete = findViewById(R.id.buttonDelete);
        buttonAiDecompose = findViewById(R.id.buttonAiDecompose);
        switchPomodoro = findViewById(R.id.switchPomodoro);
        pomodoroCard = findViewById(R.id.pomodoroCard);
        buttonStartPomodoro = findViewById(R.id.buttonStartPomodoro);
        textPomodoroStatus = findViewById(R.id.textPomodoroStatus);
        textPomodoroStatsTask = findViewById(R.id.textPomodoroStatsTask);
        imagePomodoroIcon = findViewById(R.id.imagePomodoroIcon);
        
        // 设置按钮布局为横向
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        
        // 设置类别下拉框选项
        String[] categories = {"工作", "个人", "学习", "健康", "其他"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, categories);
        spinnerCategory.setAdapter(catAdapter);
        spinnerCategory.setText(categories[0], false); // 默认为第一个类别

        // 设置优先级下拉框选项
        String[] priorities = {"高", "中", "低"};
        ArrayAdapter<String> priorityAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown, priorities);
        spinnerPriorityInput.setAdapter(priorityAdapter);
        spinnerPriorityInput.setText(priorities[1], false); // 默认优先级为中

        // 确保下拉菜单可点击弹出
        spinnerCategory.setOnClickListener(v -> spinnerCategory.showDropDown());
        spinnerPriorityInput.setOnClickListener(v -> spinnerPriorityInput.showDropDown());
        
        // 判断编辑还是新增
        currentTodo = (Todo) getIntent().getSerializableExtra("todo");
        if (currentTodo != null) {
            editTitle.setText(currentTodo.title);
            editPlace.setText(currentTodo.place);
            checkCompleted.setChecked(currentTodo.completed);
            
            // 设置优先级
            String priority = currentTodo.priority != null ? currentTodo.priority : "中";
            spinnerPriorityInput.setText(priority, false);
            
            // 设置番茄时钟开关状态
            switchPomodoro.setChecked(currentTodo.pomodoroEnabled != null ? currentTodo.pomodoroEnabled : false);
            
            int catIndex = 0;
            for (int i = 0; i < categories.length; i++) {
                if (categories[i].equals(currentTodo.category)) {
                    catIndex = i;
                    break;
                }
            }
            spinnerCategory.setText(categories[catIndex], false);
            selectedCalendar = Calendar.getInstance();
            selectedCalendar.setTimeInMillis(currentTodo.time);
            updateDateTimeText();
            buttonDelete.setVisibility(Button.VISIBLE);
        } else {
            currentTodo = null;
            selectedCalendar = Calendar.getInstance();
            updateDateTimeText();
            checkCompleted.setChecked(false);
            buttonDelete.setVisibility(parentGroupId != null ? Button.VISIBLE : Button.GONE);
            switchPomodoro.setChecked(false);
        }
        
        // AI分解按钮只在代办集模式下显示
        buttonAiDecompose.setVisibility(isTaskGroupMode ? View.VISIBLE : View.GONE);

        // 番茄时钟卡片只在非代办集模式下显示
        pomodoroCard.setVisibility(isTaskGroupMode ? View.GONE : View.VISIBLE);

        // 添加番茄时钟开关监听事件
        switchPomodoro.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // 显示开始专注按钮
                buttonStartPomodoro.setVisibility(View.VISIBLE);
                // 更新状态文本和颜色
                textPomodoroStatus.setText("当前状态：已启用");
                textPomodoroStatus.setTextColor(getResources().getColor(R.color.primary));
                imagePomodoroIcon.setImageAlpha(255); // 图标显示为不透明
                
                // 如果是已有的任务，显示专注时间统计
                if (currentTodo != null) {
                    updatePomodoroStats();
                    textPomodoroStatsTask.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "已启用番茄时钟，可直接开始专注", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 隐藏开始专注按钮和统计信息
                buttonStartPomodoro.setVisibility(View.GONE);
                textPomodoroStatsTask.setVisibility(View.GONE);
                // 更新状态文本和颜色
                textPomodoroStatus.setText("当前状态：未启用");
                textPomodoroStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                imagePomodoroIcon.setImageAlpha(180); // 图标显示为半透明
            }
        });
        
        // 添加开始专注按钮点击事件
        buttonStartPomodoro.setOnClickListener(v -> {
            // 如果是新任务，先提示保存
            if (currentTodo == null) {
                new AlertDialog.Builder(this)
                    .setTitle("保存任务")
                    .setMessage("需要先保存任务才能开始专注，是否保存？")
                    .setPositiveButton("保存并开始", (dialog, which) -> {
                        // 触发保存按钮点击
                        buttonSave.performClick();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            } else {
                // 如果是已有任务，直接启动番茄计时器
                showPomodoroTimer(currentTodo);
            }
        });

        // 根据当前任务的番茄时钟状态显示或隐藏开始专注按钮
        if (currentTodo != null && currentTodo.pomodoroEnabled != null && currentTodo.pomodoroEnabled) {
            buttonStartPomodoro.setVisibility(View.VISIBLE);
            textPomodoroStatus.setText("当前状态：已启用");
            textPomodoroStatus.setTextColor(getResources().getColor(R.color.primary));
            imagePomodoroIcon.setImageAlpha(255);
            updatePomodoroStats();
            textPomodoroStatsTask.setVisibility(View.VISIBLE);
        } else {
            buttonStartPomodoro.setVisibility(View.GONE);
            textPomodoroStatsTask.setVisibility(View.GONE);
            textPomodoroStatus.setText("当前状态：未启用");
            textPomodoroStatus.setTextColor(getResources().getColor(R.color.text_secondary));
            imagePomodoroIcon.setImageAlpha(180);
        }

        textDateTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedCalendar != null) {
                cal = selectedCalendar;
            }
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            Calendar finalCal = cal;
            new DatePickerDialog(AddEditTaskActivity.this, (view, year1, month1, dayOfMonth) -> {
                selectedCalendar.set(Calendar.YEAR, year1);
                selectedCalendar.set(Calendar.MONTH, month1);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                int hour = finalCal.get(Calendar.HOUR_OF_DAY);
                int minute = finalCal.get(Calendar.MINUTE);
                new TimePickerDialog(AddEditTaskActivity.this, (view1, hourOfDay, minute1) -> {
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedCalendar.set(Calendar.MINUTE, minute1);
                    selectedCalendar.set(Calendar.SECOND, 0);
                    selectedCalendar.set(Calendar.MILLISECOND, 0);
                    updateDateTimeText();
                }, hour, minute, true).show();
            }, year, month, day).show();
        });
        
        // 地点选择点击事件
        View endIconView = ((View) editPlace.getParent().getParent()).findViewById(com.google.android.material.R.id.text_input_end_icon);
        if (endIconView != null) {
            endIconView.setOnClickListener(v -> {
                // 这里可以添加打开地图选择位置的逻辑
                Toast.makeText(AddEditTaskActivity.this, "地图功能即将上线", Toast.LENGTH_SHORT).show();
            });
        }

        buttonSave.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String place = editPlace.getText().toString().trim();
            String category = spinnerCategory.getText().toString();
            String priority = spinnerPriorityInput.getText().toString();
            boolean completed = checkCompleted.isChecked();
            boolean pomodoroEnabled = switchPomodoro.isChecked();
            
            if (TextUtils.isEmpty(title)) {
                editTitle.setError("请输入任务标题");
                return;
            }
            
            // 保存到本地数据库
            new Thread(() -> {
                try {
                    if (currentTodo != null) {
                        // 编辑现有任务
                        currentTodo.title = title;
                        currentTodo.place = place;
                        currentTodo.time = selectedCalendar.getTimeInMillis();
                        currentTodo.category = category;
                        currentTodo.priority = priority;
                        currentTodo.completed = completed;
                        currentTodo.pomodoroEnabled = pomodoroEnabled;
                        currentTodo.updatedAt = System.currentTimeMillis();
                        
                        // 更新积分
                        currentTodo.points = currentTodo.calculatePoints();
                        
                        // 保存到本地数据库
                        taskDao.updateTodo(currentTodo);
                    } else {
                        // 新建任务
                        String uuid = UUID.randomUUID().toString();
                        Todo newTodo = new Todo(uuid, title, selectedCalendar.getTimeInMillis(), place, category, completed);
                        
                        // 设置优先级和番茄时钟
                        newTodo.priority = priority;
                        newTodo.pomodoroEnabled = pomodoroEnabled;
                        
                        // 如果是属于代办集的子任务
                        if (parentGroupId != null) {
                            newTodo.belongsToTaskGroup = true;
                        }
                        
                        // 先保存到本地数据库并设置为当前任务
                        taskDao.insertTodo(newTodo);
                        currentTodo = newTodo;
                        
                        // 如果是属于代办集的子任务，处理ACL
                        if (parentGroupId != null) {
                            final String finalParentGroupId = parentGroupId;
                            final Todo finalNewTodo = newTodo; // 需要 final 才能在 lambda 中使用

                            ParseQuery<ParseObject> parentGroupQuery = ParseQuery.getQuery("TaskGroup");
                            parentGroupQuery.whereEqualTo("uuid", finalParentGroupId);
                            parentGroupQuery.include("ACL"); // 非常重要，确保ACL被获取
                            parentGroupQuery.getFirstInBackground((parentParseGroup, e) -> {
                                if (e == null && parentParseGroup != null) {
                                    ParseACL parentACL = parentParseGroup.getACL();
                                    if (parentACL != null) {
                                        ParseObject todoParseObject = SyncWorker.toParse(finalNewTodo); // 使用 SyncWorker 中的转换方法
                                        todoParseObject.setACL(parentACL); // 应用父ACL
                                        todoParseObject.saveInBackground(eSave -> { // 保存带ACL的Todo到Parse
                                            if (eSave == null) {
                                                Log.d(TAG, "Nuevo sub-Todo guardado en Parse con ACL heredada.");
                                            } else {
                                                Log.e(TAG, "Error al guardar sub-Todo en Parse con ACL: " + eSave.getMessage());
                                            }
                                        });
                                    } else {
                                        Log.w(TAG, "El TaskGroup padre no tiene ACL o no se pudo obtener.");
                                        // 此时新子任务会使用 SyncWorker.toParse 中设置的默认ACL（仅创建者）
                                        // 可以选择是否要强制同步一次父TaskGroup的ACL
                                        ParseObject todoParseObject = SyncWorker.toParse(finalNewTodo);
                                        todoParseObject.saveInBackground(); // 保存，使用默认ACL
                                    }
                                } else {
                                    Log.e(TAG, "No se pudo obtener el TaskGroup padre para heredar ACL: " + (e != null ? e.getMessage() : "Not found"));
                                    ParseObject todoParseObject = SyncWorker.toParse(finalNewTodo);
                                    todoParseObject.saveInBackground(); // 保存，使用默认ACL
                                }
                            });
                        } else { // 普通任务，不属于代办集
                            // 同步到云端
                            ParseObject todoParseObject = SyncWorker.toParse(newTodo);
                            todoParseObject.saveInBackground();
                        }
                    }
                    
                    // 如果启用了番茄时钟并且当前处于编辑模式，提醒用户可以使用番茄定时
                    if (pomodoroEnabled && getIntent().hasExtra("todo")) {
                        runOnUiThread(() -> {
                            new AlertDialog.Builder(AddEditTaskActivity.this)
                                .setTitle("番茄时钟已启用")
                                .setMessage("是否立即开始专注时间？")
                                .setPositiveButton("开始专注", (dialog, which) -> {
                                    showPomodoroTimer(currentTodo);
                                })
                                .setNegativeButton("稍后", null)
                                .show();
                        });
                    }
                    
                    runOnUiThread(() -> {
                        Toast.makeText(AddEditTaskActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                        finish(); // 返回上一个界面
                    });
                } catch (Exception e) {
                    Log.e(TAG, "保存任务失败", e);
                    runOnUiThread(() -> {
                        Toast.makeText(AddEditTaskActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
        
        buttonDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除这个任务吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (currentTodo != null) {
                            new Thread(() -> {
                                // 标记为已删除而不是物理删除
                                currentTodo.deleted = true;
                                taskDao.insertTodo(currentTodo);
                                
                                // 如果是子任务，从代办集中移除
                                if (parentGroupId != null && !TextUtils.isEmpty(parentGroupId)) {
                                    TaskGroup group = taskGroupDao.getTaskGroupById(parentGroupId);
                                    if (group != null) {
                                        group.removeSubTask(currentTodo.uuid);
                                        taskGroupDao.insertTaskGroup(group);
                                    }
                                }
                                
                                runOnUiThread(() -> {
                                    Toast.makeText(AddEditTaskActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                });
                            }).start();
                        } else if (parentGroupId != null) {
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        
        buttonAiDecompose.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(AddEditTaskActivity.this, "请先输入任务标题", Toast.LENGTH_SHORT).show();
                return;
            }
            
            new DecomposeTaskAsyncTask().execute(title);
        });
    }
    
    // 显示番茄时钟对话框
    public void showPomodoroTimer(Todo task) {
        PomodoroTimerDialog dialog = new PomodoroTimerDialog(this, task);
        dialog.setOnTimerCompletedListener(isBreakCompleted -> {
            if (isBreakCompleted) {
                Toast.makeText(this, "休息结束，开始新的专注", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "专注完成，开始休息", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void updateDateTimeText() {
        if (selectedCalendar != null) {
            String dateTimeStr = String.format("%tY年%<tm月%<td日 %<tH:%<tM", selectedCalendar);
            textDateTime.setText(dateTimeStr);
        }
    }
    
    /**
     * 显示任务分解结果对话框
     */
    private void showDecompositionResultDialog(TaskDecomposer.DecompositionResult result) {
        // 创建子任务列表的视图
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_task_decomposition, null);
        TextView textMainTask = dialogView.findViewById(R.id.textMainTask);
        TextView textCategory = dialogView.findViewById(R.id.textCategory);
        TextView textEstimatedDays = dialogView.findViewById(R.id.textEstimatedDays);
        RecyclerView recyclerSubTasks = dialogView.findViewById(R.id.recyclerSubTasks);
        
        // 设置主任务信息
        textMainTask.setText(result.getMainTask());
        textCategory.setText("推荐类别: " + result.getCategory());
        textEstimatedDays.setText("预计天数: " + result.getEstimatedDays());
        
        // 设置子任务列表
        recyclerSubTasks.setLayoutManager(new LinearLayoutManager(this));
        SubTaskAdapter adapter = new SubTaskAdapter(result.getSubTasks());
        recyclerSubTasks.setAdapter(adapter);
        
        // 创建并显示对话框
        new AlertDialog.Builder(this)
            .setTitle("任务分解结果")
            .setView(dialogView)
            .setPositiveButton("保存为代办集", (dialog, which) -> {
                // 创建代办集和子任务
                saveAsTaskGroup(result);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 保存为代办集
     */
    private void saveAsTaskGroup(TaskDecomposer.DecompositionResult result) {
        // 创建代办集
        String groupId = UUID.randomUUID().toString();
        TaskGroup taskGroup = new TaskGroup(
            groupId,
            result.getMainTask(),
            result.getCategory(),
            result.getEstimatedDays()
        );
        
        // 保存代办集
        new Thread(() -> {
            taskGroupDao.insertTaskGroup(taskGroup);
            
            // 获取当前任务的一些信息作为子任务的默认值
            String place = editPlace.getText().toString().trim();
            String category = result.getCategory();
            if (TextUtils.isEmpty(category) || !isValidCategory(category)) {
                category = spinnerCategory.getText().toString();
            }
            
            // 设置第一个子任务的开始时间
            Calendar taskCalendar = Calendar.getInstance();
            if (selectedCalendar != null) {
                taskCalendar.setTimeInMillis(selectedCalendar.getTimeInMillis());
            }
            
            // 为每个子任务创建新的Todo对象并保存
            final String finalCategory = category;
            for (TaskDecomposer.SubTask subTask : result.getSubTasks()) {
                String subTaskId = UUID.randomUUID().toString();
                Todo newTask = new Todo(
                    subTaskId,
                    subTask.getTitle(),
                    taskCalendar.getTimeInMillis(),
                    place,
                    finalCategory,
                    false
                );
                // 重要：标记这个任务属于代办集，不应显示在主页面
                newTask.updatedAt = System.currentTimeMillis();
                newTask.belongsToTaskGroup = true;  // 标记为属于代办集
                
                // 将任务时间递增3小时，让子任务时间有序排列
                taskCalendar.add(Calendar.HOUR_OF_DAY, 3);
                
                // 保存到本地数据库
                taskDao.insertTodo(newTask);
                
                // 添加到代办集
                taskGroup.addSubTask(newTask.uuid);
            }
            
            // 更新代办集
            taskGroupDao.insertTaskGroup(taskGroup);
        }).start();
        
        Toast.makeText(this, "已创建代办集：" + result.getMainTask(), Toast.LENGTH_SHORT).show();
        finish(); // 关闭当前页面返回列表
    }
    
    /**
     * 检查类别是否在预定义的类别列表中
     */
    private boolean isValidCategory(String category) {
        String[] validCategories = {"工作", "个人", "学习", "其他"};
        for (String valid : validCategories) {
            if (valid.equalsIgnoreCase(category)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 子任务列表适配器
     */
    private class SubTaskAdapter extends RecyclerView.Adapter<SubTaskAdapter.ViewHolder> {
        private List<TaskDecomposer.SubTask> subTasks;
        
        public SubTaskAdapter(List<TaskDecomposer.SubTask> subTasks) {
            this.subTasks = subTasks;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = getLayoutInflater().inflate(R.layout.item_subtask, parent, false);
            return new ViewHolder(itemView);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            TaskDecomposer.SubTask task = subTasks.get(position);
            holder.textTitle.setText(task.getTitle());
            holder.textDescription.setText(task.getDescription());
            holder.textEstimatedHours.setText(String.format("预计耗时: %.1f小时", task.getEstimatedHours()));
        }
        
        @Override
        public int getItemCount() {
            return subTasks.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textTitle;
            TextView textDescription;
            TextView textEstimatedHours;
            
            ViewHolder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textSubtaskTitle);
                textDescription = itemView.findViewById(R.id.textSubtaskDescription);
                textEstimatedHours = itemView.findViewById(R.id.textSubtaskTime);
            }
        }
    }
    
    /**
     * 后台任务分解异步任务
     */
    private class DecomposeTaskAsyncTask extends AsyncTask<String, Void, TaskDecomposer.DecompositionResult> {
        private ProgressDialog progressDialog;
        private Exception error;
        
        @Override
        protected void onPreExecute() {
            // 显示进度对话框
            progressDialog = new ProgressDialog(AddEditTaskActivity.this);
            progressDialog.setMessage("正在分解任务...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        
        @Override
        protected TaskDecomposer.DecompositionResult doInBackground(String... params) {
            try {
                String taskTitle = params[0];
                return TaskDecomposer.decomposeTask(taskTitle);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "任务分解失败", e);
                error = e;
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(TaskDecomposer.DecompositionResult result) {
            // 关闭进度对话框
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            
            if (result != null) {
                showDecompositionResultDialog(result);
            } else {
                String errorMsg = "未知错误";
                if (error != null) {
                    errorMsg = error.getMessage();
                    
                    // 对OpenRouter API错误进行特殊处理
                    if (errorMsg.contains("API返回错误: Internal Server Error") || 
                        errorMsg.contains("500")) {
                        errorMsg = "AI服务器暂时不可用，请稍后再试";
                    } else if (errorMsg.contains("API响应中缺少choices字段")) {
                        errorMsg = "AI服务返回格式异常，请稍后再试";
                    } else if (errorMsg.contains("无法从API响应中提取有效的JSON数据")) {
                        errorMsg = "AI返回数据格式错误，请稍后再试";
                    } else if (errorMsg.contains("timed out")) {
                        errorMsg = "AI服务连接超时，请检查网络后重试";
                    }
                }
                
                // 显示错误提示
                AlertDialog.Builder builder = new AlertDialog.Builder(AddEditTaskActivity.this);
                builder.setTitle("任务分解失败")
                       .setMessage("抱歉，无法分解任务：" + errorMsg)
                       .setPositiveButton("重试", (dialog, which) -> {
                           // 重新尝试，使用当前编辑框中的标题
                           String title = editTitle.getText().toString().trim();
                           if (!title.isEmpty()) {
                               new DecomposeTaskAsyncTask().execute(title);
                           }
                       })
                       .setNegativeButton("取消", null)
                       .show();
                
                Log.e(TAG, "任务分解失败: " + (error != null ? error.toString() : "未知错误"), error);
            }
        }
    }

    /**
     * 更新番茄时钟统计信息显示
     */
    private void updatePomodoroStats() {
        if (currentTodo != null) {
            int hours = currentTodo.pomodoroMinutes / 60;
            int mins = currentTodo.pomodoroMinutes % 60;
            
            String statsText = String.format("已完成番茄钟: %d 次 | 总专注时间: %d小时%d分钟", 
                currentTodo.pomodoroCompletedCount,
                hours,
                mins);
            textPomodoroStatsTask.setText(statsText);
        }
    }
}
