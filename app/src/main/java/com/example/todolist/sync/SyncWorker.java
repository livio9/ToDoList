package com.example.todolist.sync;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.example.todolist.data.TaskGroupDao;
import com.example.todolist.data.TaskGroup;
import com.parse.ParseACL;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.FindCallback;
import com.parse.ParseException;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.*;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    
    private static Todo toTodo(ParseObject o) {
        try {
            if (o == null) return null;
            
            Todo todo = new Todo();
            todo.id = o.getString("uuid");
            
            // 确保UUID不为空
            if (todo.id == null) {
                Log.e(TAG, "Todo 解析失败: 缺少uuid字段");
                return null;
            }
            
            todo.title = o.getString("title") != null ? o.getString("title") : "";
            todo.time = o.getLong("time");
            todo.place = o.getString("place") != null ? o.getString("place") : "";
            todo.category = o.getString("category") != null ? o.getString("category") : "其他";
            todo.completed = o.has("completed") ? o.getBoolean("completed") : false;
            todo.updatedAt = o.has("clientUpdatedAt") ? o.getLong("clientUpdatedAt") : System.currentTimeMillis();
            todo.deleted = o.has("deleted") ? o.getBoolean("deleted") : false;
            todo.belongsToTaskGroup = o.has("belongsToTaskGroup") ? o.getBoolean("belongsToTaskGroup") : false;
            
            // 处理可选字段
            if (o.has("priority")) {
                todo.priority = o.getString("priority");
            }
            if (o.has("pomodoroEnabled")) {
                todo.pomodoroEnabled = o.getBoolean("pomodoroEnabled");
            }
            if (o.has("points")) {
                todo.points = o.getInt("points");
            }
            if (o.has("pomodoroMinutes")) {
                todo.pomodoroMinutes = o.getInt("pomodoroMinutes");
            }
            if (o.has("pomodoroCompletedCount")) {
                todo.pomodoroCompletedCount = o.getInt("pomodoroCompletedCount");
            }
            
            return todo;
        } catch (Exception e) {
            Log.e(TAG, "解析Todo异常: " + e.getMessage(), e);
            return null;
        }
    }
    
    private static ParseObject toParse(Todo t) {
        ParseObject o = new ParseObject("Todo");
        
        // 确保必要字段不为空
        o.put("uuid", t.id);
        o.put("title", t.title != null ? t.title : "");
        o.put("time", t.time);
        o.put("place", t.place != null ? t.place : "");
        o.put("category", t.category != null ? t.category : "其他");
        o.put("completed", t.completed);
        o.put("clientUpdatedAt", t.updatedAt);
        o.put("deleted", t.deleted);
        o.put("belongsToTaskGroup", t.belongsToTaskGroup);
        
        // 可选字段
        o.put("priority", t.priority != null ? t.priority : "中");
        o.put("pomodoroEnabled", t.pomodoroEnabled != null ? t.pomodoroEnabled : false);
        o.put("points", t.points);
        o.put("pomodoroMinutes", t.pomodoroMinutes);
        o.put("pomodoroCompletedCount", t.pomodoroCompletedCount);
        
        // 关联用户
        ParseUser currentUser = ParseUser.getCurrentUser();
        if (currentUser != null) {
            o.put("user", currentUser);
            
            // 重要：设置ACL权限
            ParseACL acl = new ParseACL(currentUser);
            acl.setReadAccess(currentUser, true);
            acl.setWriteAccess(currentUser, true);
            o.setACL(acl);
            
            Log.d(TAG, "设置Todo ACL: uuid=" + t.id + ", title=" + t.title);
        } else {
            Log.e(TAG, "无法设置ACL：当前用户为空");
        }
        
        return o;
    }
    
    private static TaskGroup toTaskGroup(ParseObject o) {
        try {
            if (o == null) return null;
            
            TaskGroup taskGroup = new TaskGroup();
            taskGroup.id = o.getString("uuid");
            
            // 确保必要字段不为null
            if (taskGroup.id == null) {
                Log.e(TAG, "TaskGroup 解析失败: 缺少uuid字段");
                return null;
            }
            
            taskGroup.title = o.getString("title") != null ? o.getString("title") : "";
            taskGroup.category = o.getString("category") != null ? o.getString("category") : "其他";
            
            // 获取estimatedDays，默认为1
            taskGroup.estimatedDays = o.has("estimatedDays") ? o.getInt("estimatedDays") : 1;
            
            // 获取子任务ID列表
            List<String> subTaskIds = o.getList("subTaskIds");
            if (subTaskIds != null) {
                taskGroup.subTaskIds = subTaskIds;
            } else {
                taskGroup.subTaskIds = new ArrayList<>();
            }
            
            // 获取删除状态，默认为false
            taskGroup.deleted = o.has("deleted") ? o.getBoolean("deleted") : false;
            
            // 获取ownerId
            if (o.has("ownerId")) {
                taskGroup.ownerId = o.getString("ownerId");
            } else if (o.has("user")) {
                ParseUser user = o.getParseUser("user");
                if (user != null) {
                    taskGroup.ownerId = user.getObjectId();
                }
            }
            
            return taskGroup;
        } catch (Exception e) {
            Log.e(TAG, "解析TaskGroup异常: " + e.getMessage(), e);
            return null;
        }
    }
    
    private static ParseObject toParseTaskGroup(TaskGroup taskGroup) {
        ParseObject o = new ParseObject("TaskGroup");
        
        // 确保必要字段不为空
        o.put("uuid", taskGroup.id);
        o.put("title", taskGroup.title != null ? taskGroup.title : "");
        o.put("category", taskGroup.category != null ? taskGroup.category : "其他");
        o.put("estimatedDays", taskGroup.estimatedDays);
        o.put("subTaskIds", taskGroup.subTaskIds != null ? taskGroup.subTaskIds : new ArrayList<String>());
        o.put("deleted", taskGroup.deleted);
        
        // 存储ownerId，保证相同用户ID
        if (taskGroup.ownerId != null) {
            o.put("ownerId", taskGroup.ownerId);
        }
        
        // 关联用户
        ParseUser currentUser = ParseUser.getCurrentUser();
        if (currentUser != null) {
            o.put("user", currentUser);
            
            // 重要：设置ACL权限
            ParseACL acl = new ParseACL(currentUser);
            acl.setReadAccess(currentUser, true);
            acl.setWriteAccess(currentUser, true);
            o.setACL(acl);
            
            Log.d(TAG, "设置TaskGroup ACL: uuid=" + taskGroup.id + ", title=" + taskGroup.title);
        } else {
            Log.e(TAG, "无法设置ACL：当前用户为空");
        }
        
        return o;
    }
    
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void pullCloudToLocal(Context applicationContext) {
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d(TAG, "用户未登录，跳过云同步");
                return;
            }
            TaskDao taskDao;
            try {
                taskDao = AppDatabase.getInstance(applicationContext).taskDao();
                if (taskDao == null) {
                    Log.e(TAG, "数据库访问失败，无法同步");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "获取TaskDao失败", e);
                return;
            }
            ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
            query.whereEqualTo("user", ParseUser.getCurrentUser());
            query.whereEqualTo("deleted", false);
            new Thread(() -> {
                try {
                    List<ParseObject> cloudDocs;
                    try {
                        cloudDocs = query.find();
                        if (cloudDocs == null) {
                            Log.w(TAG, "云端返回null，可能是网络问题");
                            return;
                        }
                        Log.d(TAG, "从云端获取到 " + cloudDocs.size() + " 个任务");
                    } catch (ParseException e) {
                        Log.e(TAG, "查询云端数据失败: " + e.getMessage(), e);
                        return;
                    }
                    // 获取本地所有任务
                    List<Todo> localTasks;
                    try {
                        localTasks = taskDao.getAll();
                        if (localTasks == null) {
                            localTasks = new ArrayList<>();
                        }
                        Log.d(TAG, "本地有 " + localTasks.size() + " 个任务");
                    } catch (Exception e) {
                        Log.e(TAG, "获取本地任务失败", e);
                        return;
                    }
                    Map<String, Todo> localMap = new HashMap<>();
                    for (Todo t : localTasks) {
                        if (t != null && t.id != null) {
                            localMap.put(t.id, t);
                        }
                    }
                    int updatedCount = 0;
                    int skippedCount = 0;
                    for (ParseObject obj : cloudDocs) {
                        try {
                            Todo cloudTodo = toTodo(obj);
                            if (cloudTodo == null || cloudTodo.id == null) {
                                Log.w(TAG, "云端任务解析失败或ID为空，跳过");
                                continue;
                            }
                            Todo localTodo = localMap.get(cloudTodo.id);
                            if (localTodo == null) {
                                Log.d(TAG, "本地不存在任务 " + cloudTodo.id + "，从云端导入");
                                taskDao.insertTodo(cloudTodo);
                                updatedCount++;
                            } else if (cloudTodo.updatedAt > localTodo.updatedAt) {
                                Log.d(TAG, "云端任务 " + cloudTodo.id + " 更新时间(" + cloudTodo.updatedAt + ")晚于本地(" + localTodo.updatedAt + ")，更新本地");
                                taskDao.insertTodo(cloudTodo);
                                updatedCount++;
                            } else {
                                Log.d(TAG, "本地任务 " + localTodo.id + " 更新时间(" + localTodo.updatedAt + ")不早于云端(" + cloudTodo.updatedAt + ")，保留本地");
                                skippedCount++;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "处理单个Todo失败，继续下一个: " + e.getMessage());
                            continue;
                        }
                    }
                    Log.d(TAG, "同步结果：更新 " + updatedCount + " 个任务，跳过 " + skippedCount + " 个任务");
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "发送广播失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "同步过程出现未捕获异常: " + e.getMessage(), e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "启动同步失败: " + e.getMessage(), e);
        }
    }

    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    public static void pushLocalToCloud(Context applicationContext) {
        try {
            if (!isNetworkAvailable(applicationContext)) {
                Log.e(TAG, "网络不可用，无法同步到云端");
                // 发送网络不可用广播
                Intent intent = new Intent("com.example.todolist.ACTION_SYNC_FAILED");
                intent.putExtra("reason", "network_unavailable");
                applicationContext.sendBroadcast(intent);
                return;
            }

            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.e(TAG, "用户未登录，无法上传到云端");
                return;
            }

            TaskDao taskDao = AppDatabase.getInstance(applicationContext).taskDao();

            new Thread(() -> {
                try {
                    // 获取所有本地任务（含已删除）
                    List<Todo> localTasks = taskDao.getAll();
                    if (localTasks == null || localTasks.isEmpty()) {
                        Log.d(TAG, "本地无任务，跳过上传");
                        return;
                    }
                    Log.d(TAG, "准备上传 " + localTasks.size() + " 个任务到云端");
                    int successCount = 0;
                    int failureCount = 0;
                    for (Todo todo : localTasks) {
                        try {
                            if (todo == null || todo.id == null) {
                                Log.w(TAG, "任务为空或ID为空，跳过");
                                continue;
                            }
                            ParseObject parseObject = toParse(todo);
                            // 同步deleted字段
                            parseObject.put("deleted", todo.deleted);
                            // 同步groupId字段
                            if (todo.groupId != null) {
                                parseObject.put("groupId", todo.groupId);
                            }
                            parseObject.save();
                            Log.d(TAG, "成功上传任务: id=" + todo.id + ", title=" + todo.title);
                            successCount++;
                        } catch (ParseException e) {
                            Log.e(TAG, "上传任务失败: id=" + todo.id + ", 错误: " + e.getMessage(), e);
                            failureCount++;
                        }
                    }
                    Log.d(TAG, "任务上传完成: 成功=" + successCount + ", 失败=" + failureCount);
                    // 通知数据已更新
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        intent.putExtra("sync_success", successCount);
                        intent.putExtra("sync_failure", failureCount);
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "发送广播失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "任务上传过程异常: " + e.getMessage(), e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "启动上传失败: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        if (ParseUser.getCurrentUser() == null) {
            Log.d(TAG, "用户未登录，跳过同步");
            return Result.success();
        }
        
        Log.d(TAG, "开始执行周期性同步工作...");
        
        // 确保两个方向的同步都执行
        try {
            pullCloudToLocal(getApplicationContext());
            pushLocalToCloud(getApplicationContext());
            pullTaskGroupsToLocal(getApplicationContext());
            pushTaskGroupsToCloud(getApplicationContext());
            
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "同步工作异常: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // 安排周期性同步任务（例如每15分钟执行一次）
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("SyncWork", ExistingPeriodicWorkPolicy.REPLACE, request);
        
        Log.d(TAG, "已安排15分钟周期性同步任务");
    }

    // 立即执行一次同步
    public static void triggerSyncNow(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueue(request);
        
        Log.d(TAG, "已触发即时同步任务");
    }

    public static void cancelPeriodicSync(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("SyncWork");
        Log.d(TAG, "已取消周期性同步任务");
    }

    // 推送本地TaskGroup到云端（含删除同步和子任务同步）
    public static void pushTaskGroupsToCloud(Context applicationContext) {
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.e(TAG, "用户未登录，无法上传TaskGroup到云端");
                return;
            }
            TaskGroupDao taskGroupDao = AppDatabase.getInstance(applicationContext).taskGroupDao();
            TaskDao taskDao = AppDatabase.getInstance(applicationContext).taskDao();
            new Thread(() -> {
                try {
                    List<TaskGroup> localTaskGroups = taskGroupDao.getAllTaskGroups();
                    if (localTaskGroups == null || localTaskGroups.isEmpty()) {
                        Log.d(TAG, "本地无TaskGroup，跳过上传");
                        return;
                    }
                    Log.d(TAG, "准备上传 " + localTaskGroups.size() + " 个TaskGroup到云端");
                    int successCount = 0;
                    int failureCount = 0;
                    for (TaskGroup taskGroup : localTaskGroups) {
                        try {
                            if (taskGroup == null || taskGroup.id == null) {
                                Log.w(TAG, "TaskGroup为空或ID为空，跳过");
                                continue;
                            }
                            if (taskGroup.ownerId == null && user != null) {
                                Log.d(TAG, "TaskGroup缺少ownerId，设置为当前用户");
                                taskGroup.ownerId = user.getObjectId();
                            }
                            ParseObject parseObject = toParseTaskGroup(taskGroup);
                            // 同步deleted字段
                            parseObject.put("deleted", taskGroup.deleted);
                            parseObject.save();
                            Log.d(TAG, "成功上传TaskGroup: id=" + taskGroup.id + ", title=" + taskGroup.title);
                            successCount++;
                            // 同步上传所有子任务
                            List<ParseObject> subTaskParseList = new ArrayList<>();
                            if (taskGroup.subTaskIds != null) {
                                for (String subTaskId : taskGroup.subTaskIds) {
                                    Todo subTask = taskDao.getTodoById(subTaskId);
                                    if (subTask != null) {
                                        ParseObject subTaskObj = toParse(subTask);
                                        subTaskObj.put("groupId", taskGroup.id);
                                        subTaskObj.put("deleted", subTask.deleted);
                                        subTaskParseList.add(subTaskObj);
                                    }
                                }
                            }
                            if (!subTaskParseList.isEmpty()) {
                                ParseObject.saveAll(subTaskParseList);
                                Log.d(TAG, "已同步上传 " + subTaskParseList.size() + " 个子任务到云端，归属于TaskGroup: " + taskGroup.id);
                            }
                        } catch (ParseException e) {
                            Log.e(TAG, "上传TaskGroup失败: id=" + taskGroup.id + ", 错误: " + e.getMessage(), e);
                            failureCount++;
                        }
                    }
                    Log.d(TAG, "TaskGroup上传完成: 成功=" + successCount + ", 失败=" + failureCount);
                    // 通知数据已更新
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "发送广播失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "TaskGroup上传过程异常: " + e.getMessage(), e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "启动TaskGroup上传失败: " + e.getMessage(), e);
        }
    }

    // 从云端拉取TaskGroup到本地（只拉未删除的）
    public static void pullTaskGroupsToLocal(Context applicationContext) {
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d(TAG, "用户未登录，跳过TaskGroup同步");
                return;
            }
            TaskGroupDao taskGroupDao;
            try {
                taskGroupDao = AppDatabase.getInstance(applicationContext).taskGroupDao();
                if (taskGroupDao == null) {
                    Log.e(TAG, "数据库访问失败，无法同步TaskGroup");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "获取TaskGroupDao失败", e);
                return;
            }
            ParseQuery<ParseObject> query = ParseQuery.getQuery("TaskGroup");
            query.whereEqualTo("user", ParseUser.getCurrentUser());
            query.whereEqualTo("deleted", false);
            new Thread(() -> {
                try {
                    List<ParseObject> cloudDocs;
                    try {
                        cloudDocs = query.find();
                        if (cloudDocs == null) {
                            Log.w(TAG, "云端TaskGroup查询返回null，可能是网络问题");
                            return;
                        }
                        Log.d(TAG, "从云端获取到 " + cloudDocs.size() + " 个TaskGroup");
                    } catch (ParseException e) {
                        Log.e(TAG, "查询云端TaskGroup失败: " + e.getMessage(), e);
                        return;
                    }
                    List<TaskGroup> localTaskGroups;
                    try {
                        localTaskGroups = taskGroupDao.getAllTaskGroups();
                        if (localTaskGroups == null) {
                            localTaskGroups = new ArrayList<>();
                        }
                        Log.d(TAG, "本地有 " + localTaskGroups.size() + " 个TaskGroup");
                    } catch (Exception e) {
                        Log.e(TAG, "获取本地TaskGroup失败", e);
                        return;
                    }
                    Map<String, TaskGroup> localMap = new HashMap<>();
                    for (TaskGroup t : localTaskGroups) {
                        if (t != null && t.id != null) {
                            localMap.put(t.id, t);
                        }
                    }
                    int updatedCount = 0;
                    int skippedCount = 0;
                    for (ParseObject obj : cloudDocs) {
                        try {
                            TaskGroup cloudTaskGroup = toTaskGroup(obj);
                            if (cloudTaskGroup == null || cloudTaskGroup.id == null) {
                                Log.w(TAG, "云端TaskGroup解析失败或ID为空，跳过");
                                continue;
                            }
                            TaskGroup localTaskGroup = localMap.get(cloudTaskGroup.id);
                            if (localTaskGroup == null) {
                                Log.d(TAG, "本地不存在TaskGroup " + cloudTaskGroup.id + "，从云端导入");
                                taskGroupDao.insertTaskGroup(cloudTaskGroup);
                                updatedCount++;
                            } else if (cloudTaskGroup.createdAt > localTaskGroup.createdAt) {
                                Log.d(TAG, "云端TaskGroup " + cloudTaskGroup.id + " 创建时间晚于本地，更新");
                                taskGroupDao.insertTaskGroup(cloudTaskGroup);
                                updatedCount++;
                            } else {
                                Log.d(TAG, "保留本地TaskGroup " + localTaskGroup.id);
                                skippedCount++;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "处理单个TaskGroup失败: " + e.getMessage());
                            continue;
                        }
                    }
                    Log.d(TAG, "TaskGroup同步结果：更新 " + updatedCount + " 个，跳过 " + skippedCount + " 个");
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "发送广播失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "TaskGroup同步失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "启动TaskGroup同步失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
