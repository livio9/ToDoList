package com.example.todolist.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import androidx.core.content.ContextCompat;

import com.parse.ParseACL;
import com.parse.ParseCloud;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import android.app.ProgressDialog;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

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
    private Button buttonShareTaskGroup;
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
        
        // 初始化保存和删除按钮
        buttonSaveTaskGroup = findViewById(R.id.buttonSaveTaskGroup);
        buttonDeleteTaskGroup = findViewById(R.id.buttonDeleteTaskGroup);
        buttonShareTaskGroup = findViewById(R.id.buttonShareTaskGroup);
        buttonShareTaskGroup.setVisibility(View.VISIBLE);

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
                AppDatabase.getInstance(TaskGroupActivity.this).taskDao().logicalDeleteTodoForUser(todo.id, todo.userId);
                
                // 从代办集中移除
                if (taskGroup != null) {
                    taskGroup.removeSubTask(todo.id);
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
            String currentUserId = CurrentUserUtil.getCurrentUserId();
            if (currentUserId == null) {
                Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, AddEditTaskActivity.class);
            intent.putExtra("parent_group_id", groupId);
            // No need to pass currentUserId explicitly if AddEditTaskActivity gets it itself
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

        // 设置共享按钮点击事件
        buttonShareTaskGroup.setOnClickListener(v -> {
            if (taskGroup != null && com.parse.ParseUser.getCurrentUser() != null) {
                fetchTaskGroupFromParseAndShowShareDialog(taskGroup.id);
            } else {
                Toast.makeText(this, "无法共享此代办集", Toast.LENGTH_SHORT).show();
            }
        });

        // 加载代办集和子任务
        loadTaskGroup(groupId);
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
                String currentUserId = CurrentUserUtil.getCurrentUserId();
                // 先删除所有子任务
                if (taskGroup.subTaskIds != null && !taskGroup.subTaskIds.isEmpty()) {
                    for (String taskId : taskGroup.subTaskIds) {
                        AppDatabase.getInstance(this).taskDao().logicalDeleteTodoForUser(taskId, currentUserId);
                    }
                }
                
                // 设置删除标记
                taskGroup.deleted = true;
                taskGroup.touch();
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
            loadTaskGroup(taskGroup.id);
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
                String currentUserId = CurrentUserUtil.getCurrentUserId();
                // 加载代办集
                taskGroup = taskGroupDao.getTaskGroupByIdForUser(groupId, currentUserId);
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
                        Todo task = AppDatabase.getInstance(this).taskDao().getTodoByIdForUser(taskId, currentUserId);
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

    // 添加共享相关方法
    private void fetchTaskGroupFromParseAndShowShareDialog(String uuid) {
        if (uuid == null) return;
        ProgressDialog progressDialog = ProgressDialog.show(this, "", "正在准备共享信息...", true);
        
        // 首先确保当前TaskGroup已经上传到云端
        new Thread(() -> {
            try {
                // 先尝试上传当前TaskGroup到云端，确保云端有数据
                if (taskGroup != null) {
                    android.util.Log.d("TaskGroupActivity", "先尝试将当前TaskGroup上传到云端: " + uuid);
                    
                    // 先上传TaskGroup本身
                    com.parse.ParseObject taskGroupObject = new com.parse.ParseObject("TaskGroup");
                    taskGroupObject.put("uuid", taskGroup.id);
                    taskGroupObject.put("title", taskGroup.title != null ? taskGroup.title : "");
                    taskGroupObject.put("category", taskGroup.category != null ? taskGroup.category : "其他");
                    taskGroupObject.put("estimatedDays", taskGroup.estimatedDays);
                    taskGroupObject.put("createdAt", taskGroup.createdAt);
                    taskGroupObject.put("subTaskIds", taskGroup.subTaskIds != null ? taskGroup.subTaskIds : new ArrayList<String>());
                    taskGroupObject.put("deleted", taskGroup.deleted);
                    
                    // 设置ownerId
                    com.parse.ParseUser currentUser = com.parse.ParseUser.getCurrentUser();
                    if (currentUser != null) {
                        taskGroupObject.put("user", currentUser);
                        taskGroupObject.put("ownerId", currentUser.getObjectId());
                        
                        // 设置ACL权限
                        com.parse.ParseACL acl = new com.parse.ParseACL(currentUser);
                        acl.setReadAccess(currentUser, true);
                        acl.setWriteAccess(currentUser, true);
                        taskGroupObject.setACL(acl);
                    }
                    
                    // 同步方法保存，确保上传成功
                    try {
                        taskGroupObject.save();
                        android.util.Log.d("TaskGroupActivity", "成功上传TaskGroup到云端: " + uuid);
                    } catch (com.parse.ParseException e) {
                        if (e.getCode() == com.parse.ParseException.OBJECT_NOT_FOUND) {
                            android.util.Log.w("TaskGroupActivity", "TaskGroup不存在，创建新记录: " + e.getMessage());
                        } else if (e.getCode() == com.parse.ParseException.DUPLICATE_VALUE) {
                            android.util.Log.d("TaskGroupActivity", "TaskGroup已存在，继续操作: " + e.getMessage());
                        } else {
                            android.util.Log.e("TaskGroupActivity", "上传TaskGroup失败: " + e.getMessage(), e);
                        }
                    }
                }
                
                // 然后查询云端数据
                com.parse.ParseQuery<com.parse.ParseObject> query = com.parse.ParseQuery.getQuery("TaskGroup");
                query.whereEqualTo("uuid", uuid);
                query.include("user");
                
                runOnUiThread(() -> {
                    progressDialog.setMessage("正在加载共享信息...");
                });
                
                try {
                    // 使用findInBackground而不是getFirstInBackground
                    query.findInBackground((parseTaskGroupObjects, e) -> {
                        progressDialog.dismiss();
                        
                        if (e == null && parseTaskGroupObjects != null && !parseTaskGroupObjects.isEmpty()) {
                            android.util.Log.d("TaskGroupActivity", "成功获取云端TaskGroup: " + parseTaskGroupObjects.get(0).getObjectId());
                            showShareDialog(parseTaskGroupObjects.get(0));
                        } else {
                            String errorMsg = (e != null) ? e.getMessage() : "未找到代办集信息";
                            android.util.Log.e("TaskGroupActivity", "获取云端TaskGroup失败: " + errorMsg);
                            
                            // 再次尝试直接创建
                            if (e != null && e.getCode() == com.parse.ParseException.OBJECT_NOT_FOUND && taskGroup != null) {
                                runOnUiThread(() -> {
                                    Toast.makeText(TaskGroupActivity.this, "正在准备共享信息，请稍后再试", Toast.LENGTH_SHORT).show();
                                    // 触发后台同步
                                    com.example.todolist.sync.SyncWorker.pushTaskGroupsToCloud(TaskGroupActivity.this);
                                });
                            } else {
                                runOnUiThread(() -> {
                                    new AlertDialog.Builder(TaskGroupActivity.this)
                                        .setTitle("共享准备失败")
                                        .setMessage("无法从云端获取代办集信息，请确保网络连接正常并重试。")
                                        .setPositiveButton("重试", (dialog, which) -> {
                                            fetchTaskGroupFromParseAndShowShareDialog(uuid);
                                        })
                                        .setNegativeButton("取消", null)
                                        .show();
                                });
                            }
                        }
                    });
                } catch (Exception ex) {
                    progressDialog.dismiss();
                    android.util.Log.e("TaskGroupActivity", "查询执行异常: " + ex.getMessage(), ex);
                    runOnUiThread(() -> {
                        Toast.makeText(TaskGroupActivity.this, "查询出错，请稍后再试", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                progressDialog.dismiss();
                android.util.Log.e("TaskGroupActivity", "整体操作异常: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(TaskGroupActivity.this, "操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showShareDialog(ParseObject parseTaskGroupObject) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("共享代办集: " + parseTaskGroupObject.getString("title"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);
        final EditText input = new EditText(this);
        input.setHint("请输入用户名或邮箱");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        layout.addView(input);
        TextView extraInfo = new TextView(this);
        extraInfo.setText("提示:\n• 可以使用对方的邮箱或用户名\n• 对方必须已经注册并激活账号");
        extraInfo.setTextSize(12);
        extraInfo.setPadding(0, 16, 0, 0);
        layout.addView(extraInfo);
        builder.setView(layout);
        builder.setPositiveButton("共享", (dialog, which) -> {
            String emailToShareWith = input.getText().toString().trim();
            if (!TextUtils.isEmpty(emailToShareWith)) {
                shareTaskGroupWithUsername(parseTaskGroupObject, emailToShareWith);
            } else {
                Toast.makeText(this, "请输入用户名或邮箱", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void shareTaskGroupWithUsername(ParseObject parseTaskGroupObject, String emailToShareWith) {
        ProgressDialog progressDialog = ProgressDialog.show(this, "", "正在共享...", true);
        HashMap<String, String> params = new HashMap<>();
        params.put("usernameToFind", emailToShareWith);
        android.util.Log.d("TaskGroupActivity", "开始查找用户: " + emailToShareWith);
        ParseCloud.callFunctionInBackground("findUserToShareWith", params, (result, e) -> {
            progressDialog.dismiss();
            if (e == null && result != null) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                String userId = (String) resultMap.get("objectId");
                if (userId == null) {
                    android.util.Log.e("TaskGroupActivity", "云函数返回的用户ID为空");
                    Toast.makeText(this, "查找用户失败: 用户不存在", Toast.LENGTH_SHORT).show();
                    return;
                }
                android.util.Log.d("TaskGroupActivity", "找到用户: " + userId);
                ParseUser userToShareWith = ParseUser.createWithoutData(ParseUser.class, userId);
                ParseACL acl = parseTaskGroupObject.getACL();
                if (acl == null) {
                    acl = new ParseACL(ParseUser.getCurrentUser());
                }
                acl.setReadAccess(userToShareWith, true);
                acl.setWriteAccess(userToShareWith, true);
                parseTaskGroupObject.setACL(acl);
                final ParseACL finalAcl = acl;
                android.util.Log.d("TaskGroupActivity", "开始保存TaskGroup ACL");
                parseTaskGroupObject.saveInBackground(eSave -> {
                    if (eSave == null) {
                        android.util.Log.d("TaskGroupActivity", "TaskGroup ACL保存成功");
                        Toast.makeText(this, "成功共享", Toast.LENGTH_SHORT).show();
                        // 递归更新所有子任务的ACL
                        if (taskGroup != null && taskGroup.subTaskIds != null && !taskGroup.subTaskIds.isEmpty()) {
                            android.util.Log.d("TaskGroupActivity", "开始更新子任务ACL，子任务数量: " + taskGroup.subTaskIds.size());
                            shareSubTasksACL(taskGroup.subTaskIds, finalAcl);
                        }
                        // 同步本地 taskGroup 到云端，确保本地和云端数据一致
                        SyncWorker.pushTaskGroupsToCloud(this);
                    } else {
                        android.util.Log.e("TaskGroupActivity", "保存TaskGroup ACL失败: " + eSave.getMessage());
                        Toast.makeText(this, "共享失败: " + eSave.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                android.util.Log.e("TaskGroupActivity", "查找用户失败: " + (e != null ? e.getMessage() : "结果为空"));
                Toast.makeText(this, "查找用户失败: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void shareSubTasksACL(List<String> subTaskIds, ParseACL newACL) {
        if (subTaskIds == null || subTaskIds.isEmpty() || newACL == null) {
            return;
        }
        android.util.Log.d("TaskGroupActivity", "开始查询子任务，数量: " + subTaskIds.size());
        com.parse.ParseQuery<com.parse.ParseObject> subTaskQuery = com.parse.ParseQuery.getQuery("Todo");
        subTaskQuery.whereContainedIn("uuid", subTaskIds);
        subTaskQuery.findInBackground((subTaskObjects, e) -> {
            if (e == null && subTaskObjects != null && !subTaskObjects.isEmpty()) {
                android.util.Log.d("TaskGroupActivity", "找到子任务数量: " + subTaskObjects.size());
                List<com.parse.ParseObject> objectsToSave = new ArrayList<>();
                for (com.parse.ParseObject subTaskObj : subTaskObjects) {
                    subTaskObj.setACL(newACL);
                    objectsToSave.add(subTaskObj);
                }
                com.parse.ParseObject.saveAllInBackground(objectsToSave, eSaveAll -> {
                    if (eSaveAll == null) {
                        android.util.Log.d("TaskGroupActivity", "子任务ACL更新成功");
                    } else {
                        android.util.Log.e("TaskGroupActivity", "子任务ACL更新失败: " + eSaveAll.getMessage());
                    }
                });
            } else {
                android.util.Log.e("TaskGroupActivity", "查询子任务失败: " + (e != null ? e.getMessage() : "未找到子任务"));
            }
        });
    }
} 