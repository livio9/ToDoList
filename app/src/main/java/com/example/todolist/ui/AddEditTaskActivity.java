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
import com.parse.ParseObject;
import com.parse.ParseUser;
import com.parse.ParseQuery;

import org.json.JSONException;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class AddEditTaskActivity extends AppCompatActivity {
    private static final String TAG = "AddEditTaskActivity";
    private TextInputEditText editTitle;
    private EditText editPlace;
    private MaterialAutoCompleteTextView spinnerCategory;
    private TextView textDateTime;
    private SwitchMaterial checkCompleted;
    private Button buttonSave;
    private Button buttonDelete;
    private Button buttonAiDecompose;
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
        
        // 初始化 DAO 和 Firebase 引用
        taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        taskGroupDao = AppDatabase.getInstance(getApplicationContext()).taskGroupDao();
        
        // 获取界面控件引用
        editTitle = findViewById(R.id.editTitle);
        editPlace = findViewById(R.id.editPlace);
        spinnerCategory = findViewById(R.id.spinnerCategoryInput);
        textDateTime = findViewById(R.id.textDateTime);
        checkCompleted = findViewById(R.id.checkCompleted);
        buttonSave = findViewById(R.id.buttonSave);
        buttonDelete = findViewById(R.id.buttonDelete);
        buttonAiDecompose = findViewById(R.id.buttonAiDecompose);
        
        // 设置按钮布局为横向
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        
        // 设置类别下拉框选项
        String[] categories = {"工作", "个人", "学习", "其他"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categories);
        spinnerCategory.setAdapter(catAdapter);

        // 判断编辑还是新增
        currentTodo = (Todo) getIntent().getSerializableExtra("todo");
        if (currentTodo != null) {
            editTitle.setText(currentTodo.title);
            editPlace.setText(currentTodo.place);
            checkCompleted.setChecked(currentTodo.completed);
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
        }

        // AI分解按钮只在代办集模式下显示
        buttonAiDecompose.setVisibility(isTaskGroupMode ? View.VISIBLE : View.GONE);

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

        buttonSave.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String place = editPlace.getText().toString().trim();
            String category = spinnerCategory.getText().toString();
            boolean completed = checkCompleted.isChecked();
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(AddEditTaskActivity.this, "标题不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            long time = selectedCalendar.getTimeInMillis();
            if (currentTodo == null) {
                String newId = UUID.randomUUID().toString();
                currentTodo = new Todo(newId, title, time, place, category, completed);
                // 新创建的任务，根据是否有父代办集来设置标志
                currentTodo.belongsToTaskGroup = (parentGroupId != null && !TextUtils.isEmpty(parentGroupId));
            } else {
                currentTodo.title = title;
                currentTodo.place = place;
                currentTodo.category = category;
                currentTodo.time = time;
                currentTodo.completed = completed;
                // 确保编辑现有任务时不会丢失 belongsToTaskGroup 标志
                // 如果有parentGroupId，则确保设置为true
                if (parentGroupId != null && !TextUtils.isEmpty(parentGroupId)) {
                    currentTodo.belongsToTaskGroup = true;
                }
            }
            // 每次保存更新更新时间，并确保标记未删除
            currentTodo.updatedAt = System.currentTimeMillis();
            currentTodo.deleted = false;

            // 如果是子任务，关联到代办集
            if (parentGroupId != null && !TextUtils.isEmpty(parentGroupId)) {
                // 标记为属于代办集的任务
                currentTodo.belongsToTaskGroup = true;

                new Thread(() -> {
                    TaskGroup group = taskGroupDao.getTaskGroupById(parentGroupId);
                    if (group != null) {
                        group.addSubTask(currentTodo.id);
                        taskGroupDao.insertTaskGroup(group);
                    }
                }).start();
            }

            new Thread(() -> taskDao.insertTodo(currentTodo)).start();

            // 同步到云端
            ParseObject todoObject = new ParseObject("Todo");
            todoObject.put("id", currentTodo.id);
            todoObject.put("title", currentTodo.title);
            todoObject.put("time", currentTodo.time);
            todoObject.put("place", currentTodo.place);
            todoObject.put("category", currentTodo.category);
            todoObject.put("completed", currentTodo.completed);
            todoObject.put("updatedAt", currentTodo.updatedAt);
            todoObject.put("deleted", currentTodo.deleted);
            todoObject.put("belongsToTaskGroup", currentTodo.belongsToTaskGroup);
            todoObject.put("user", ParseUser.getCurrentUser());
            todoObject.saveInBackground();

            Toast.makeText(AddEditTaskActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });

        // 删除操作：采用软删除
        buttonDelete.setOnClickListener(v -> {
            if (currentTodo != null) {
                currentTodo.deleted = true;
                currentTodo.updatedAt = System.currentTimeMillis();
                new Thread(() -> taskDao.insertTodo(currentTodo)).start();
                ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
                query.whereEqualTo("id", currentTodo.id);
                query.getFirstInBackground((object, e) -> {
                    if (object != null) {
                        object.put("deleted", true);
                        object.put("updatedAt", currentTodo.updatedAt);
                        object.saveInBackground();
                    }
                });

                Toast.makeText(AddEditTaskActivity.this, "任务已删除", Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        // 设置AI任务分解按钮点击事件
        buttonAiDecompose.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(AddEditTaskActivity.this, "请先输入任务标题", Toast.LENGTH_SHORT).show();
                return;
            }

            // 执行任务分解
            new DecomposeTaskAsyncTask().execute(title);
        });
    }

    private void updateDateTimeText() {
        if (selectedCalendar == null) {
            selectedCalendar = Calendar.getInstance();
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        String formatted = sdf.format(selectedCalendar.getTime());
        textDateTime.setText(formatted);
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
                taskGroup.addSubTask(newTask.id);

                // 同步到云端
                ParseObject todoObject = new ParseObject("Todo");
                todoObject.put("id", newTask.id);
                todoObject.put("title", newTask.title);
                todoObject.put("time", newTask.time);
                todoObject.put("place", newTask.place);
                todoObject.put("category", newTask.category);
                todoObject.put("completed", newTask.completed);
                todoObject.put("updatedAt", newTask.updatedAt);
                todoObject.put("deleted", newTask.deleted);
                todoObject.put("belongsToTaskGroup", newTask.belongsToTaskGroup);
                todoObject.put("user", ParseUser.getCurrentUser());
                todoObject.saveInBackground();

            }
            
            // 更新代办集
            taskGroupDao.insertTaskGroup(taskGroup);
            
            // 同步代办集到云端
            ParseObject groupObject = new ParseObject("TaskGroup");
            groupObject.put("id", taskGroup.id);
            groupObject.put("title", taskGroup.title);
            groupObject.put("category", taskGroup.category);
            groupObject.put("estimatedDays", taskGroup.estimatedDays);
            groupObject.put("user", ParseUser.getCurrentUser());
            groupObject.saveInBackground();

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
                String errorMsg = error != null ? error.getMessage() : "未知错误";
                Toast.makeText(AddEditTaskActivity.this, 
                    "任务分解失败: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        }
    }
}
