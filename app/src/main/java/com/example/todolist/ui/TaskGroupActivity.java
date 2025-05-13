package com.example.todolist.ui;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todolist.R;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskGroup;
import com.example.todolist.data.TaskGroupDao;
import com.example.todolist.data.Todo;
import com.example.todolist.sync.SyncWorker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.parse.ParseACL;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import androidx.core.content.ContextCompat;

public class TaskGroupActivity extends BaseActivity {
    private TaskGroup taskGroup;
    private RecyclerView recyclerSubTasks;
    private TaskAdapter adapter;
    private List<Todo> subTasks = new ArrayList<>();
    private TextView textGroupName;
    private TextView textCategory;
    private TextView textEstimatedDays;
    private TextView textCreatedAt;
    private FloatingActionButton fabAddTask;
    private Button buttonSaveTaskGroup;
    private Button buttonDeleteTaskGroup;
    private TaskGroupDao taskGroupDao;
    private Button buttonShareTaskGroup; // 新增按钮引用

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
        
        // 初始化保存和删除按钮
        buttonSaveTaskGroup = findViewById(R.id.buttonSaveTaskGroup);
        buttonDeleteTaskGroup = findViewById(R.id.buttonDeleteTaskGroup);

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
                AppDatabase.getInstance(TaskGroupActivity.this).taskDao().markAsDeleted(todo.id, System.currentTimeMillis());
                
                // 从代办集中移除
                if (taskGroup != null) {
                    taskGroup.subTaskIds.remove(todo.id);
                    taskGroupDao.insertTaskGroup(taskGroup);
                    
                    // 同步到云端
                    SyncWorker.pushTaskGroupsToCloud(TaskGroupActivity.this);
                    SyncWorker.pushLocalToCloud(TaskGroupActivity.this);
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
        
        // 设置保存按钮点击事件
        buttonSaveTaskGroup.setOnClickListener(v -> {
            saveTaskGroup();
        });
        
        // 设置删除按钮点击事件
        buttonDeleteTaskGroup.setOnClickListener(v -> {
            showDeleteConfirmationDialog();
        });

        buttonShareTaskGroup = findViewById(R.id.buttonShareTaskGroup);

        // 只有 TaskGroup 的创建者才能共享 (简单判断)
        // 您可能需要更复杂的逻辑来判断是否是创建者
        ParseUser currentUser = ParseUser.getCurrentUser();
        // 假设 taskGroup.userObjectId 存储了创建者的 objectId (这需要在 TaskGroup 本地模型和同步时处理)
        // 为了简单，我们先假设当前用户如果是 TaskGroup 的 "user" 字段，则可以共享。
        // 在 loadTaskGroup 成功后，我们会检查 currentUser.getObjectId().equals(taskGroup.getParseUser("user").getObjectId())

        buttonShareTaskGroup.setOnClickListener(v -> {
            if (taskGroup != null && ParseUser.getCurrentUser() != null) {
                // 检查当前用户是否是该 TaskGroup 的创建者 (需要 taskGroup 对象中有创建者信息)
                // 简化：我们假设创建者是 o.put("user", currentUser) 中的 user。
                // 从Parse获取TaskGroup对象以检查所有者。
                fetchTaskGroupFromParseAndShowShareDialog(taskGroup.uuid);

            } else {
                Toast.makeText(this, "无法共享此代办集", Toast.LENGTH_SHORT).show();
            }
        });

        // 加载代办集和子任务
        loadTaskGroup(groupId);
    }

    private void fetchTaskGroupFromParseAndShowShareDialog(String taskGroupId) {
        if (taskGroupId == null) return;
        ProgressDialog progressDialog = ProgressDialog.show(this, "", "正在加载权限信息...", true);

        ParseQuery<ParseObject> query = ParseQuery.getQuery("TaskGroup");
        query.whereEqualTo("uuid", taskGroupId);
        query.include("user"); // 确保获取了user指针的完整信息
        query.getFirstInBackground((parseTaskGroupObject, e) -> {
            progressDialog.dismiss();
            if (e == null && parseTaskGroupObject != null) {
                ParseUser owner = parseTaskGroupObject.getParseUser("user");
                ParseUser currentUser = ParseUser.getCurrentUser();
                if (currentUser != null && owner != null && currentUser.getObjectId().equals(owner.getObjectId())) {
                    // 当前用户是所有者，可以共享
                    showShareDialog(parseTaskGroupObject);
                } else {
                    Toast.makeText(TaskGroupActivity.this, "只有创建者才能共享此代办集", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("TaskGroupActivity_Share", "获取 TaskGroup 失败: " + (e != null ? e.getMessage() : "Objeto no encontrado"));
                Toast.makeText(TaskGroupActivity.this, "加载代办集信息失败", Toast.LENGTH_SHORT).show();
            }
        });
    }


    // 新增方法：显示共享对话框
    private void showShareDialog(ParseObject parseTaskGroupObject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("共享代办集: " + parseTaskGroupObject.getString("title"));

        final EditText input = new EditText(this);
        input.setHint("输入对方的用户名 (邮箱)");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        builder.setView(input);

        builder.setPositiveButton("共享", (dialog, which) -> {
            String usernameToShareWith = input.getText().toString().trim();
            if (!TextUtils.isEmpty(usernameToShareWith)) {
                shareTaskGroupWithUsername(parseTaskGroupObject, usernameToShareWith);
            } else {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // 新增方法：根据用户名共享
    private void shareTaskGroupWithUsername(ParseObject parseTaskGroupObject, String usernameToShareWith) {
        ProgressDialog progressDialog = ProgressDialog.show(this, "", "正在共享...", true);

        ParseQuery<ParseUser> userQuery = ParseUser.getQuery();
        userQuery.whereEqualTo("username", usernameToShareWith); // Parse 默认 username 是邮箱
        userQuery.getFirstInBackground((sharedUser, eUser) -> {
            if (eUser == null && sharedUser != null) {
                ParseACL acl = parseTaskGroupObject.getACL();
                if (acl == null) { // 如果对象没有ACL (理论上创建时应该已设置)
                    Log.w("TaskGroupActivity_Share", "TaskGroup objectId " + parseTaskGroupObject.getObjectId() + " 没有ACL，将创建一个新的。");
                    acl = new ParseACL(ParseUser.getCurrentUser()); // 创建者始终有权
                }

                // 授予读写权限
                acl.setReadAccess(sharedUser, true);
                acl.setWriteAccess(sharedUser, true); // 协作者可编辑

                parseTaskGroupObject.setACL(acl);
                parseTaskGroupObject.saveInBackground(eSave -> {
                    progressDialog.dismiss();
                    if (eSave == null) {
                        Toast.makeText(TaskGroupActivity.this, "成功共享给 " + usernameToShareWith, Toast.LENGTH_SHORT).show();
                        // （可选）可以考虑通知被共享用户
                    } else {
                        Log.e("TaskGroupActivity_Share", "保存ACL失败: " + eSave.getMessage());
                        Toast.makeText(TaskGroupActivity.this, "共享失败: " + eSave.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

                // --- 关键：共享子任务 ---
                // 获取此 TaskGroup 的所有子任务，并为它们设置相同的 ACL
                shareSubTasksACL(taskGroup.subTaskIds, acl);


            } else {
                progressDialog.dismiss();
                Log.e("TaskGroupActivity_Share", "找不到用户 '" + usernameToShareWith + "': " + (eUser != null ? eUser.getMessage() : ""));
                Toast.makeText(TaskGroupActivity.this, "找不到用户: " + usernameToShareWith, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 新增方法：递归或批量更新子任务的ACL
    private void shareSubTasksACL(List<String> subTaskIds, ParseACL newACL) {
        if (subTaskIds == null || subTaskIds.isEmpty() || newACL == null) {
            return;
        }
        Log.d("TaskGroupActivity_Share", "开始为 " + subTaskIds.size() + " 个子任务设置新的ACL");

        ParseQuery<ParseObject> subTaskQuery = ParseQuery.getQuery("Todo");
        subTaskQuery.whereContainedIn("uuid", subTaskIds); // 根据 uuid (本地ID) 查询
        subTaskQuery.findInBackground((subTaskObjects, e) -> {
            if (e == null) {
                if (subTaskObjects != null && !subTaskObjects.isEmpty()) {
                    Log.d("TaskGroupActivity_Share", "找到 " + subTaskObjects.size() + " 个子任务 ParseObjects 进行ACL更新");
                    List<ParseObject> objectsToSave = new ArrayList<>();
                    for (ParseObject subTaskObj : subTaskObjects) {
                        subTaskObj.setACL(newACL); // 直接使用父 TaskGroup 的新 ACL
                        objectsToSave.add(subTaskObj);
                    }
                    ParseObject.saveAllInBackground(objectsToSave, eSaveAll -> {
                        if (eSaveAll == null) {
                            Log.i("TaskGroupActivity_Share", "所有子任务的ACL更新成功");
                        } else {
                            Log.e("TaskGroupActivity_Share", "批量更新子任务ACL失败: " + eSaveAll.getMessage());
                        }
                    });
                } else {
                    Log.d("TaskGroupActivity_Share", "未找到与 subTaskIds 匹配的子任务 ParseObjects");
                }
            } else {
                Log.e("TaskGroupActivity_Share", "查询子任务失败: " + e.getMessage());
            }
        });
    }


    /**
     * 保存代办集
     */
    private void saveTaskGroup() {
        if (taskGroup == null) {
            Toast.makeText(this, "代办集不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 可以在这里添加对表单的验证和更新逻辑
        // 目前简单地保存现有的taskGroup对象
        
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 保存到本地数据库
                taskGroupDao.insertTaskGroup(taskGroup);
                
                // 同步到云端
                SyncWorker.pushTaskGroupsToCloud(this);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "代办集已保存", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定要删除此代办集吗？该操作将同时删除所有子任务且不可恢复。")
            .setPositiveButton("删除", (dialog, which) -> {
                deleteTaskGroup();
            })
            .setNegativeButton("取消", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    /**
     * 删除代办集
     */
    private void deleteTaskGroup() {
        if (taskGroup == null) {
            Toast.makeText(this, "代办集不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 先删除所有子任务
                if (taskGroup.subTaskIds != null && !taskGroup.subTaskIds.isEmpty()) {
                    for (String taskId : taskGroup.subTaskIds) {
                        AppDatabase.getInstance(this).taskDao().markAsDeleted(taskId, System.currentTimeMillis());
                    }
                }
                
                // 设置删除标记
                taskGroup.deleted = true;
                taskGroupDao.insertTaskGroup(taskGroup);
                
                // 同步到云端
                SyncWorker.pushTaskGroupsToCloud(this);
                SyncWorker.pushLocalToCloud(this);
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "代办集已删除", Toast.LENGTH_SHORT).show();
                    // 关闭当前页面
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
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

    /**
     * 加载代办集和子任务
     */
    private void loadTaskGroup(String groupId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 加载代办集
                taskGroup = taskGroupDao.getTaskGroupById(groupId);
                if (taskGroup == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "代办集不存在", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // 更新UI显示代办集信息
                runOnUiThread(() -> {
                    textGroupName.setText(taskGroup.title);
                    textCategory.setText("类别: " + taskGroup.category);
                    
                    // 设置类别标签颜色
                    int backgroundColor;
                    switch (taskGroup.category) {
                        case "工作":
                            backgroundColor = ContextCompat.getColor(this, R.color.category_work);
                            break;
                        case "学习":
                            backgroundColor = ContextCompat.getColor(this, R.color.category_study);
                            break;
                        case "个人":
                            backgroundColor = ContextCompat.getColor(this, R.color.category_personal);
                            break;
                        case "健康":
                            backgroundColor = ContextCompat.getColor(this, R.color.category_health);
                            break;
                        default:
                            backgroundColor = ContextCompat.getColor(this, R.color.category_other);
                            break;
                    }
                    
                    // 使用颜色作为文本颜色
                    textCategory.setTextColor(backgroundColor);
                    
                    textEstimatedDays.setText("预计完成天数: " + taskGroup.estimatedDays);
                    
                    // 格式化日期
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    textCreatedAt.setText("创建时间: " + sdf.format(taskGroup.createdAt));
                });

                // 加载子任务
                List<String> subTaskIds = taskGroup.subTaskIds;
                List<Todo> tasks = new ArrayList<>();
                
                if (subTaskIds != null && !subTaskIds.isEmpty()) {
                    for (String taskId : subTaskIds) {
                        Todo task = AppDatabase.getInstance(this).taskDao().getTodoById(taskId);
                        if (task != null && !task.deleted) {
                            tasks.add(task);
                        }
                    }
                }
                
                final List<Todo> finalTasks = tasks;
                
                runOnUiThread(() -> {
                    // 更新任务列表
                    subTasks.clear();
                    subTasks.addAll(finalTasks);
                    adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
} 