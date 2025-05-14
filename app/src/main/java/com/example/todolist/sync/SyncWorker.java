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
    // 广播 Action 和 Extras 定义
    public static final String ACTION_SYNC_COMPLETED = "com.example.todolist.ACTION_SYNC_COMPLETED";
    public static final String ACTION_SYNC_FAILED = "com.example.todolist.ACTION_SYNC_FAILED"; // 用于同步启动失败或过程中的通用失败
    public static final String EXTRA_SYNC_TYPE = "sync_type"; // "todo" 或 "task_group"
    public static final String EXTRA_SUCCESS_COUNT = "sync_success_count";
    public static final String EXTRA_FAILURE_COUNT = "sync_failure_count";
    public static final String EXTRA_SYNC_ERROR_MESSAGE = "sync_error_message";
    public static final String EXTRA_REASON = "reason"; // 用于 ACTION_SYNC_FAILED


    private static Todo toTodo(ParseObject o) {
        try {
            if (o == null) return null;

            Todo todo = new Todo();
            todo.uuid = o.getString("uuid");

            // 确保UUID不为空
            if (todo.uuid == null) {
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
            todo.userId = o.has("user") ? o.getParseUser("user").getObjectId() : "";
            if (todo.userId == null) {
                Log.e(TAG, "错误：从云端拉取的任务 " + o.getString("uuid") + " 的所有者(ParseUser)的 objectId 为空！");
                return null; // userId 必须有效才能正确关联本地数据
            }
            Log.d(TAG, "调试：从云端任务 " + o.getString("uuid") + " 成功提取 userId: " + todo.userId);

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
        o.put("uuid", t.uuid);
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

            Log.d(TAG, "设置Todo ACL: uuid=" + t.uuid + ", title=" + t.title);
        } else {
            Log.e(TAG, "无法设置ACL：当前用户为空");
        }

        return o;
    }

    private static TaskGroup toTaskGroup(ParseObject o) {
        try {
            if (o == null) return null;

            TaskGroup taskGroup = new TaskGroup();
            taskGroup.uuid = o.getString("uuid");

            // 确保必要字段不为null
            if (taskGroup.uuid == null) {
                Log.e(TAG, "TaskGroup 解析失败: 缺少uuid字段");
                return null;
            }

            taskGroup.title = o.getString("title") != null ? o.getString("title") : "";
            taskGroup.category = o.getString("category") != null ? o.getString("category") : "其他";

            // 获取estimatedDays，默认为1
            taskGroup.estimatedDays = o.has("estimatedDays") ? o.getInt("estimatedDays") : 1;
            taskGroup.createdAt = o.getCreatedAt().getTime();
            if (o.getUpdatedAt().getTime() > taskGroup.createdAt) {
                taskGroup.updatedAt = o.getUpdatedAt().getTime();
            } else {
                taskGroup.updatedAt = taskGroup.createdAt; // 如果没有更新时间，使用创建时间
            }

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
                taskGroup.userId = o.getString("ownerId");
            } else if (o.has("user")) {
                ParseUser user = o.getParseUser("user");
                if (user != null) {
                    taskGroup.userId = user.getObjectId();
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
        o.put("uuid", taskGroup.uuid);
        o.put("title", taskGroup.title != null ? taskGroup.title : "");
        o.put("category", taskGroup.category != null ? taskGroup.category : "其他");
        o.put("estimatedDays", taskGroup.estimatedDays);
        o.put("subTaskIds", taskGroup.subTaskIds != null ? taskGroup.subTaskIds : new ArrayList<String>());
        o.put("deleted", taskGroup.deleted);

        // 存储ownerId，保证相同用户ID
        if (taskGroup.userId != null) {
            o.put("ownerId", taskGroup.userId);
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

            Log.d(TAG, "设置TaskGroup ACL: uuid=" + taskGroup.uuid + ", title=" + taskGroup.title);
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
            // 检查Parse用户是否登录
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d(TAG, "用户未登录，跳过云同步");
                return;
            }

            // 获取TaskDao
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

            // 创建查询但延迟执行
            ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
//            query.whereEqualTo("user", ParseUser.getCurrentUser());

            final TaskDao finalTaskDao = taskDao;
            new Thread(() -> {
                try {
                    // 获取云端所有任务
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
                    String currentUserId = user.getObjectId();
                    try {
                        localTasks = finalTaskDao.getAllTasksForUser();
                        if (localTasks == null) {
                            localTasks = new ArrayList<>();
                        }
                        Log.d(TAG, "本地有 " + localTasks.size() + " 个任务");
                    } catch (Exception e) {
                        Log.e(TAG, "获取本地任务失败", e);
                        return;
                    }

                    // 建立本地映射
                    Map<String, Todo> localMap = new HashMap<>();
                    for (Todo t : localTasks) {
                        // 确保ID不为null
                        if (t != null && t.uuid != null) {
                            localMap.put(t.uuid, t);
                        }
                    }

                    int updatedCount = 0;
                    int skippedCount = 0;

                    // 遍历云端任务, 仅执行"云 -> 本地"更新，且只在云端更新更晚时才覆盖本地
                    for (ParseObject obj : cloudDocs) {
                        try {
                            Todo cloudTodo = toTodo(obj);
                            if (cloudTodo == null || cloudTodo.uuid == null) {
                                Log.w(TAG, "云端任务解析失败或ID为空，跳过");
                                continue;
                            }

                            Todo localTodo = localMap.get(cloudTodo.uuid);

                            // 如果本地为空，或者云端更新时间更晚，则更新本地
                            if (localTodo == null) {
                                Log.d(TAG, "本地不存在任务 " + cloudTodo.uuid + "，从云端导入");
                                finalTaskDao.insertTodo(cloudTodo);
                                updatedCount++;
                            } else if (cloudTodo.updatedAt > localTodo.updatedAt) {
                                Log.d(TAG, "云端任务 " + cloudTodo.uuid + " 更新时间(" + cloudTodo.updatedAt +
                                       ")晚于本地(" + localTodo.updatedAt + ")，更新本地");
                                finalTaskDao.insertTodo(cloudTodo);
                                updatedCount++;
                            } else {
                                Log.d(TAG, "本地任务 " + localTodo.uuid + " 更新时间(" + localTodo.updatedAt +
                                       ")不早于云端(" + cloudTodo.updatedAt + ")，保留本地");
                                skippedCount++;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "处理单个Todo失败，继续下一个: " + e.getMessage());
                            // 单个失败不影响整体
                            continue;
                        }
                    }

                    Log.d(TAG, "同步结果：更新 " + updatedCount + " 个任务，跳过 " + skippedCount + " 个任务");

                    // 通知数据更新
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
                    String currentUserId = user.getObjectId();
                    // 获取所有本地任务
                    List<Todo> localTasks = taskDao.getAllTasksForUser();
                    if (localTasks == null || localTasks.isEmpty()) {
                        Log.d(TAG, "本地无任务，跳过上传");
                        return;
                    }

                    Log.d(TAG, "准备上传 " + localTasks.size() + " 个任务到云端");
                    int successCount = 0;
                    int failureCount = 0;

                    // 批量上传到Parse
                    for (Todo localTodo : localTasks) {
                        if (localTodo == null || localTodo.uuid == null) {
                            Log.w(TAG, "Todo 同步：本地任务为空或其ID (uuid) 为空，跳过推送。");
                            continue;
                        }

                        ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
                        query.whereEqualTo("uuid", localTodo.uuid); // 使用自定义的唯一ID (uuid) 进行查询
                        query.whereEqualTo("user", user);       // 确保是当前用户的对象

                        try {
                            // 尝试获取云端是否已存在该 uuid 的对象
                            ParseObject cloudParseObject = query.getFirst(); // getFirst会抛出OBJECT_NOT_FOUND异常

                            // 如果执行到这里，说明云端存在该对象，进行更新逻辑
                            Log.d(TAG, "Todo 同步：云端已存在 uuid: " + localTodo.uuid + " 的任务，准备比较时间戳并可能更新。");

                            long localUpdatedAt = localTodo.updatedAt;
                            long cloudClientUpdatedAt = cloudParseObject.has("clientUpdatedAt") ? cloudParseObject.getLong("clientUpdatedAt") : 0;
                            // Date cloudServerUpdatedAt = cloudParseObject.getUpdatedAt(); // 这是服务器的更新时间

                            // 冲突解决：如果本地更新时间 > 云端更新时间，才推送更新
                            // (或者根据您的策略，例如总是以本地为准，或者更复杂的合并)
                            if (localUpdatedAt > cloudClientUpdatedAt) {
                                Log.d(TAG, "Todo 同步：本地版本较新 (本地: " + localUpdatedAt + ", 云端: " + cloudClientUpdatedAt + ")，更新云端对象: " + localTodo.uuid);
                                // 将 localTodo 的属性更新到 cloudParseObject 上
                                // 注意：不能直接用 toParse(localTodo) 返回的新实例，必须在 cloudParseObject 上修改
                                cloudParseObject.put("title", localTodo.title != null ? localTodo.title : "");
                                cloudParseObject.put("time", localTodo.time);
                                cloudParseObject.put("place", localTodo.place != null ? localTodo.place : "");
                                cloudParseObject.put("category", localTodo.category != null ? localTodo.category : "其他");
                                cloudParseObject.put("completed", localTodo.completed);
                                cloudParseObject.put("clientUpdatedAt", localTodo.updatedAt); // 更新云端的 clientUpdatedAt
                                cloudParseObject.put("deleted", localTodo.deleted);
                                cloudParseObject.put("belongsToTaskGroup", localTodo.belongsToTaskGroup);
                                cloudParseObject.put("priority", localTodo.priority != null ? localTodo.priority : "中");
                                cloudParseObject.put("pomodoroEnabled", localTodo.pomodoroEnabled != null ? localTodo.pomodoroEnabled : false);
                                cloudParseObject.put("points", localTodo.points);
                                cloudParseObject.put("pomodoroMinutes", localTodo.pomodoroMinutes);
                                cloudParseObject.put("pomodoroCompletedCount", localTodo.pomodoroCompletedCount);
                                // user 和 ACL 通常在创建时设定，更新时一般不需要改，除非有特殊需求

                                cloudParseObject.saveInBackground(e -> {
                                    if (e == null) {
                                        Log.d(TAG, "Todo 同步：成功更新云端对象: " + localTodo.uuid);
                                    } else {
                                        Log.e(TAG, "Todo 同步：更新云端对象失败: " + localTodo.uuid + ", 错误: " + e.getMessage(), e);
                                    }
                                });
                            } else {
                                Log.d(TAG, "Todo 同步：本地版本不比云端新 (本地: " + localUpdatedAt + ", 云端: " + cloudClientUpdatedAt + ")，跳过推送: " + localTodo.uuid);
                            }

                        } catch (ParseException e) {
                            if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                                // 云端不存在该 uuid 的对象，创建新对象
                                Log.d(TAG, "Todo 同步：云端不存在 uuid: " + localTodo.uuid + " 的任务，创建新对象。");
                                ParseObject newCloudParseObject = toParse(localTodo); // toParse 应该包含设置 user 和 ACL
                                if (newCloudParseObject != null) { // 确保 toParse 成功
                                    newCloudParseObject.saveInBackground(saveException -> {
                                        if (saveException == null) {
                                            Log.d(TAG, "Todo 同步：成功上传新对象到云端: " + localTodo.uuid + "，新 objectId: " + newCloudParseObject.getObjectId());
                                        } else {
                                            Log.e(TAG, "Todo 同步：上传新对象失败: " + localTodo.uuid + ", 错误: " + saveException.getMessage(), saveException);
                                        }
                                    });
                                } else {
                                    Log.e(TAG, "Todo 同步：toParse(localTodo) 返回 null，无法创建新对象 for uuid: " + localTodo.uuid);
                                }
                            } else {
                                // 其他查询错误
                                Log.e(TAG, "Todo 同步：查询云端对象失败 for uuid: " + localTodo.uuid + ", 错误: " + e.getMessage(), e);
                            }
                        }
                    }
                    // ... (Broadcast logic) ...
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

    // 推送本地TaskGroup到云端
    public static void pushTaskGroupsToCloud(Context applicationContext) {
        if (!isNetworkAvailable(applicationContext)) {
            Log.w(TAG, "TaskGroup 推送：网络不可用，跳过上传。");
            sendSyncFailedBroadcast(applicationContext, "task_group", "network_unavailable");
            return;
        }

        ParseUser user = ParseUser.getCurrentUser();
        if (user == null) {
            Log.e(TAG, "TaskGroup 推送：用户未登录，无法上传到云端。");
            sendSyncFailedBroadcast(applicationContext, "task_group", "user_not_logged_in");
            return;
        }

        TaskGroupDao taskGroupDao = AppDatabase.getInstance(applicationContext).taskGroupDao();

        new Thread(() -> {
            final List<ParseObject> objectsToProcessInCloud = new ArrayList<>();
            // 用于跟踪哪些本地对象是成功处理的（无论是上传、更新还是跳过）
            final List<String> processedLocalGroupIds = new ArrayList<>();
            final List<String> failedLocalGroupIds = new ArrayList<>(); // 记录处理失败的本地group ID

            try {
                String currentUserId = user.getObjectId();
                List<TaskGroup> localTaskGroups = taskGroupDao.getAllTaskGroupsForUser();
                if (localTaskGroups == null || localTaskGroups.isEmpty()) {
                    Log.d(TAG, "TaskGroup 推送：本地无当前用户的待办集，跳过上传。");
                    sendSyncCompletedBroadcast(applicationContext, "task_group", 0, 0, null);
                    return;
                }

                Log.d(TAG, "TaskGroup 推送：准备处理 " + localTaskGroups.size() + " 个本地待办集。");

                // 使用 CountDownLatch 等待所有异步查询完成
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(localTaskGroups.size());

                for (TaskGroup localTaskGroup : localTaskGroups) {
                    if (localTaskGroup == null || localTaskGroup.uuid == null) {
                        Log.w(TAG, "TaskGroup 推送：本地待办集为空或其ID (uuid) 为空，标记为失败。");
                        synchronized (failedLocalGroupIds) {
                            failedLocalGroupIds.add("null_or_empty_id");
                        }
                        latch.countDown();
                        continue;
                    }

                    ParseQuery<ParseObject> query = ParseQuery.getQuery("TaskGroup");
                    query.whereEqualTo("uuid", localTaskGroup.uuid);
                    query.whereEqualTo("user", user);

                    final TaskGroup currentLocalGroup = localTaskGroup; // effectively final for lambda

                    query.getFirstInBackground((cloudTaskGroup, e) -> {
                        try {
                            if (e == null && cloudTaskGroup != null) { // 对象已存在于云端
                                Log.d(TAG, "TaskGroup 推送：云端已存在 uuid: " + currentLocalGroup.uuid + " 的待办集。");
                                long localUpdatedAt = currentLocalGroup.updatedAt;
                                long cloudClientUpdatedAt = cloudTaskGroup.has("clientUpdatedAt") ? cloudTaskGroup.getLong("clientUpdatedAt") : 0;

                                if (localUpdatedAt > cloudClientUpdatedAt) {
                                    Log.d(TAG, "TaskGroup 推送：本地版本较新 (本地 updatedAt: " + localUpdatedAt + ", 云端 clientUpdatedAt: " + cloudClientUpdatedAt + ")，准备更新云端对象: " + currentLocalGroup.uuid);
                                    // 更新 cloudTaskGroup 的属性
//                                    updateParseObjectFromLocalTaskGroup(cloudTaskGroup, currentLocalGroup); // 确保此方法也更新 clientUpdatedAt
                                    cloudTaskGroup.put("clientUpdatedAt", localUpdatedAt); // 显式更新云端的 clientUpdatedAt
                                    synchronized (objectsToProcessInCloud) {
                                        objectsToProcessInCloud.add(cloudTaskGroup);
                                    }
                                    synchronized (processedLocalGroupIds) {
                                        processedLocalGroupIds.add(currentLocalGroup.uuid);
                                    }
                                } else {
                                    Log.d(TAG, "TaskGroup 推送：本地版本不比云端新或相同，跳过推送: " + currentLocalGroup.uuid);
                                    synchronized (processedLocalGroupIds) { // 也算作已处理
                                        processedLocalGroupIds.add(currentLocalGroup.uuid);
                                    }
                                }
                            } else if (e != null && e.getCode() == ParseException.OBJECT_NOT_FOUND) { // 对象在云端不存在
                                Log.d(TAG, "TaskGroup 推送：云端不存在 uuid: " + currentLocalGroup.uuid + " 的待办集，准备创建新对象。");
                                ParseObject newCloudTaskGroup = toParseTaskGroup(currentLocalGroup); // toParseTaskGroup 内部应设置 user, ACL, 和 clientUpdatedAt
                                if (newCloudTaskGroup != null) {
                                    // clientUpdatedAt 应该在 toParseTaskGroup 中根据 localTaskGroup.updatedAt 设置
                                    synchronized (objectsToProcessInCloud) {
                                        objectsToProcessInCloud.add(newCloudTaskGroup);
                                    }
                                    synchronized (processedLocalGroupIds) {
                                        processedLocalGroupIds.add(currentLocalGroup.uuid);
                                    }
                                } else {
                                    Log.e(TAG, "TaskGroup 推送：toParseTaskGroup(localTaskGroup) 返回 null for uuid: " + currentLocalGroup.uuid);
                                    synchronized (failedLocalGroupIds) {
                                        failedLocalGroupIds.add(currentLocalGroup.uuid);
                                    }
                                }
                            } else { // 其他查询错误
                                Log.e(TAG, "TaskGroup 推送：查询云端对象失败 for uuid: " + currentLocalGroup.uuid + ", 错误: " + (e != null ? e.getMessage() : "未知查询错误"), e);
                                synchronized (failedLocalGroupIds) {
                                    failedLocalGroupIds.add(currentLocalGroup.uuid);
                                }
                            }
                        } finally {
                            latch.countDown(); // 确保 latch 总是被递减
                        }
                    });
                }

                // 等待所有 getFirstInBackground 操作完成
                try {
                    latch.await(60, java.util.concurrent.TimeUnit.SECONDS); // 设置一个超时时间，防止无限等待
                    Log.d(TAG, "TaskGroup 推送：所有查询操作已完成或超时。");
                } catch (InterruptedException interruptedException) {
                    Log.e(TAG, "TaskGroup 推送：等待查询操作完成时被中断。", interruptedException);
                    Thread.currentThread().interrupt(); // 重新设置中断状态
                    sendSyncCompletedBroadcast(applicationContext, "task_group", 0, localTaskGroups.size(), "同步被中断");
                    return;
                }

                // 现在 objectsToProcessInCloud 包含了所有需要创建或更新的 ParseObject
                if (!objectsToProcessInCloud.isEmpty()) {
                    Log.d(TAG, "TaskGroup 推送：开始批量保存/更新 " + objectsToProcessInCloud.size() + " 个待办集到云端。");
                    ParseObject.saveAllInBackground(objectsToProcessInCloud, e -> {
                        if (e == null) {
                            Log.d(TAG, "TaskGroup 推送：批量保存/更新成功 " + objectsToProcessInCloud.size() + " 个待办集。");
                            sendSyncCompletedBroadcast(applicationContext, "task_group", objectsToProcessInCloud.size(), failedLocalGroupIds.size(), null);
                        } else {
                            Log.e(TAG, "TaskGroup 推送：批量保存/更新到云端失败: " + e.getMessage(), e);
                            // 失败时，objectsToProcessInCloud 中的对象可能部分成功部分失败，或者全部失败
                            // ParseException for saveAll can be a list. For simplicity, count all as failed on any error.
                            sendSyncCompletedBroadcast(applicationContext, "task_group", 0, processedLocalGroupIds.size() + failedLocalGroupIds.size(), "批量上传待办集失败: " + e.getMessage());
                        }
                    });
                } else if (!failedLocalGroupIds.isEmpty() || !processedLocalGroupIds.isEmpty()) {
                    // 没有对象需要保存，但之前有查询失败或成功跳过的
                    Log.d(TAG, "TaskGroup 推送：没有新的待办集需要上传或更新到云端。已处理: " + processedLocalGroupIds.size() + ", 查询/创建失败: " + failedLocalGroupIds.size());
                    sendSyncCompletedBroadcast(applicationContext, "task_group", processedLocalGroupIds.size() - failedLocalGroupIds.size(), failedLocalGroupIds.size(), null);
                } else {
                    Log.d(TAG, "TaskGroup 推送：没有本地待办集需要处理。");
                    sendSyncCompletedBroadcast(applicationContext, "task_group", 0, 0, null);
                }

            } catch (Exception e) { // 捕获整个线程中的其他未知异常
                Log.e(TAG, "TaskGroup 推送：上传过程发生未知异常: " + e.getMessage(), e);
                // successCount 和 failureCount 在这里不准确，发送一个通用失败消息
//                sendSyncCompletedBroadcast(applicationContext, "task_group", 0, (localTaskGroups != null ? localTaskGroups.size() : 0) , "同步过程中发生严重错误: " + e.getMessage());
            }
        }).start();
    }

    private static void sendSyncCompletedBroadcast(Context context, String syncType, int successCount, int failureCount, String errorMessage) {
        try {
            Intent intent = new Intent(ACTION_SYNC_COMPLETED);
            intent.setPackage(context.getPackageName()); // 确保广播是应用内广播，更安全
            intent.putExtra(EXTRA_SYNC_TYPE, syncType);
            intent.putExtra(EXTRA_SUCCESS_COUNT, successCount);
            intent.putExtra(EXTRA_FAILURE_COUNT, failureCount);
            if (errorMessage != null) {
                intent.putExtra(EXTRA_SYNC_ERROR_MESSAGE, errorMessage);
            }
            context.sendBroadcast(intent);
            Log.d(TAG, "发送同步完成广播: 类型=" + syncType + ", 成功=" + successCount + ", 失败=" + failureCount + (errorMessage != null ? ", 错误=" + errorMessage : ""));
        } catch (Exception e) {
            Log.e(TAG, "发送同步完成广播失败: " + e.getMessage(), e);
        }
    }

    private static void sendSyncFailedBroadcast(Context context, String syncType, String reason) {
        try {
            Intent intent = new Intent("com.example.todolist.ACTION_SYNC_FAILED"); // 可以定义一个新的Action表示同步启动失败
            intent.setPackage(context.getPackageName());
            intent.putExtra(EXTRA_SYNC_TYPE, syncType);
            intent.putExtra("reason", reason); // 例如 "network_unavailable", "user_not_logged_in"
            context.sendBroadcast(intent);
            Log.w(TAG, "发送同步失败广播: 类型=" + syncType + ", 原因=" + reason);
        } catch (Exception e) {
            Log.e(TAG, "发送同步失败广播失败: " + e.getMessage(), e);
        }
    }

    // 从云端拉取TaskGroup到本地
    public static void pullTaskGroupsToLocal(Context applicationContext) {
        try {
            // 检查用户是否登录
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d(TAG, "用户未登录，跳过TaskGroup同步");
                return;
            }

            // 安全获取TaskGroupDao
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

            // 创建查询并设置策略
            ParseQuery<ParseObject> query = ParseQuery.getQuery("TaskGroup");
//            query.whereEqualTo("user", ParseUser.getCurrentUser());

            final TaskGroupDao finalTaskGroupDao = taskGroupDao;
            new Thread(() -> {
                try {
                    // 获取云端所有任务组
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

                    // 获取本地所有任务组
                    List<TaskGroup> localTaskGroups;
                    try {
                        String currentUserId = user.getObjectId();
                        localTaskGroups = finalTaskGroupDao.getAllTaskGroupsForUser();
                        if (localTaskGroups == null) {
                            localTaskGroups = new ArrayList<>();
                        }
                        Log.d(TAG, "本地有 " + localTaskGroups.size() + " 个TaskGroup");
                    } catch (Exception e) {
                        Log.e(TAG, "获取本地TaskGroup失败", e);
                        return;
                    }

                    // 建立本地映射
                    Map<String, TaskGroup> localMap = new HashMap<>();
                    for (TaskGroup t : localTaskGroups) {
                        if (t != null && t.uuid != null) {
                            localMap.put(t.uuid, t);
                        }
                    }

                    int updatedCount = 0;
                    int skippedCount = 0;

                    // 遍历云端任务组，只在以下情况更新本地：
                    // 1. 本地不存在该TaskGroup
                    // 2. 本地存在，但云端创建时间更晚（简化判断，一般云端同一TaskGroup不会有多个版本）
                    for (ParseObject obj : cloudDocs) {
                        try {
                            TaskGroup cloudTaskGroup = toTaskGroup(obj);
                            if (cloudTaskGroup == null || cloudTaskGroup.uuid == null) {
                                Log.w(TAG, "云端TaskGroup解析失败或ID为空，跳过");
                                continue;
                            }

                            TaskGroup localTaskGroup = localMap.get(cloudTaskGroup.uuid);

                            // 如果本地不存在，或者云端创建时间更晚，则更新本地
                            if (localTaskGroup == null) {
                                Log.d(TAG, "本地不存在TaskGroup " + cloudTaskGroup.uuid + "，从云端导入");
                                finalTaskGroupDao.insertTaskGroup(cloudTaskGroup);
                                updatedCount++;
                            } else if (cloudTaskGroup.updatedAt > localTaskGroup.updatedAt) {
                                // 通常情况下不会出现，但如有两个人同时创建同UUID的TaskGroup，会以晚更新的为准
                                Log.d(TAG, "云端TaskGroup " + cloudTaskGroup.uuid + " 更新时间晚于本地，更新");
                                finalTaskGroupDao.insertTaskGroup(cloudTaskGroup);
                                updatedCount++;
                            } else {
                                Log.d(TAG, "保留本地TaskGroup " + localTaskGroup.uuid);
                                skippedCount++;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "处理单个TaskGroup失败: " + e.getMessage());
                            // 继续处理下一个，不中断整个同步
                            continue;
                        }
                    }

                    Log.d(TAG, "TaskGroup同步结果：更新 " + updatedCount + " 个，跳过 " + skippedCount + " 个");

                    // 通知数据更新
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
