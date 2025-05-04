package com.example.todolist.sync;

import android.content.Context;
import android.content.Intent;
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
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.FindCallback;
import com.parse.ParseException;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.*;

public class SyncWorker extends Worker {
    private static Todo toTodo(ParseObject o) {
        Todo todo = new Todo();
        todo.id = o.getString("uuid");
        todo.title = o.getString("title");
        todo.time = o.getLong("time");
        todo.place = o.getString("place");
        todo.category = o.getString("category");
        todo.completed = o.getBoolean("completed");
        todo.updatedAt = o.getLong("clientUpdatedAt");
        todo.deleted = o.getBoolean("deleted");
        todo.belongsToTaskGroup = o.getBoolean("belongsToTaskGroup");
        
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
        
        return todo;
    }
    
    private static ParseObject toParse(Todo t) {
        ParseObject o = new ParseObject("Todo");
        o.put("uuid", t.id);
        o.put("title", t.title);
        o.put("time", t.time);
        o.put("place", t.place);
        o.put("category", t.category);
        o.put("completed", t.completed);
        o.put("clientUpdatedAt", t.updatedAt);
        o.put("deleted", t.deleted);
        o.put("belongsToTaskGroup", t.belongsToTaskGroup);
        o.put("priority", t.priority);
        o.put("pomodoroEnabled", t.pomodoroEnabled);
        o.put("points", t.points);
        o.put("user", ParseUser.getCurrentUser());
        return o;
    }
    
    private static TaskGroup toTaskGroup(ParseObject o) {
        try {
            if (o == null) return null;
            
            TaskGroup taskGroup = new TaskGroup();
            taskGroup.id = o.getString("uuid");
            
            // 确保必要字段不为null
            if (taskGroup.id == null) {
                Log.e("SyncWorker", "TaskGroup 解析失败: 缺少uuid字段");
                return null;
            }
            
            taskGroup.title = o.getString("title") != null ? o.getString("title") : "";
            taskGroup.category = o.getString("category") != null ? o.getString("category") : "其他";
            
            // 获取estimatedDays，默认为1
            taskGroup.estimatedDays = o.has("estimatedDays") ? o.getInt("estimatedDays") : 1;
            
            // 获取createdAt，默认为当前时间
            taskGroup.createdAt = o.has("createdAt") ? o.getLong("createdAt") : System.currentTimeMillis();
            
            // 获取子任务ID列表
            List<String> subTaskIds = o.getList("subTaskIds");
            if (subTaskIds != null) {
                taskGroup.subTaskIds = subTaskIds;
            } else {
                taskGroup.subTaskIds = new ArrayList<>();
            }
            
            return taskGroup;
        } catch (Exception e) {
            Log.e("SyncWorker", "解析TaskGroup异常: " + e.getMessage());
            return null;
        }
    }
    
    private static ParseObject toParseTaskGroup(TaskGroup taskGroup) {
        ParseObject o = new ParseObject("TaskGroup");
        o.put("uuid", taskGroup.id);
        o.put("title", taskGroup.title);
        o.put("category", taskGroup.category);
        o.put("estimatedDays", taskGroup.estimatedDays);
        o.put("createdAt", taskGroup.createdAt);
        o.put("subTaskIds", taskGroup.subTaskIds);
        o.put("user", ParseUser.getCurrentUser());
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
                Log.d("SyncWorker", "用户未登录，跳过云同步");
                return;
            }
            
            // 获取TaskDao
            TaskDao taskDao;
            try {
                taskDao = AppDatabase.getInstance(applicationContext).taskDao();
                if (taskDao == null) {
                    Log.e("SyncWorker", "数据库访问失败，无法同步");
                    return;
                }
            } catch (Exception e) {
                Log.e("SyncWorker", "获取TaskDao失败", e);
                return;
            }
            
            // 创建查询但延迟执行
            ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
            query.whereEqualTo("user", ParseUser.getCurrentUser());
            
            // 设置查询超时，避免长时间等待
            query.setMaxCacheAge(60 * 60 * 24); // 1天的缓存
            query.setCachePolicy(ParseQuery.CachePolicy.NETWORK_ELSE_CACHE); // 网络优先，本地缓存备用

            new Thread(() -> {
                try {
                    // 获取云端所有任务
                    List<ParseObject> cloudDocs;
                    try {
                        cloudDocs = query.find();
                        if (cloudDocs == null) {
                            Log.w("SyncWorker", "云端返回null，可能是网络问题");
                            return;
                        }
                    } catch (ParseException e) {
                        Log.e("SyncWorker", "查询云端数据失败: " + e.getMessage(), e);
                        return;
                    }
                    
                    // 获取本地所有任务
                    List<Todo> localTasks;
                    try {
                        localTasks = taskDao.getAll();
                        if (localTasks == null) {
                            localTasks = new ArrayList<>();
                        }
                    } catch (Exception e) {
                        Log.e("SyncWorker", "获取本地任务失败", e);
                        return;
                    }

                    // 建立本地映射
                    Map<String, Todo> localMap = new HashMap<>();
                    for (Todo t : localTasks) {
                        // 确保ID不为null
                        if (t != null && t.id != null) {
                            localMap.put(t.id, t);
                        }
                    }

                    // 遍历云端任务, 仅执行"云 -> 本地"更新
                    for (ParseObject obj : cloudDocs) {
                        try {
                            Todo cloudTodo = toTodo(obj);
                            if (cloudTodo == null || cloudTodo.id == null) continue;
                            
                            Todo localTodo = localMap.get(cloudTodo.id);
                            // 如果本地为空, 或云端的更新更晚, 则覆盖本地
                            if (localTodo == null || cloudTodo.updatedAt > localTodo.updatedAt) {
                                taskDao.insertTodo(cloudTodo);
                            }
                        } catch (Exception e) {
                            Log.e("SyncWorker", "处理单个Todo失败，继续下一个: " + e.getMessage());
                            // 单个失败不影响整体
                            continue;
                        }
                    }
                    
                    // 通知数据更新
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e("SyncWorker", "发送广播失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e("SyncWorker", "同步过程出现未捕获异常: " + e.getMessage(), e);
                }
            }).start();
        } catch (Exception e) {
            Log.e("SyncWorker", "启动同步失败: " + e.getMessage(), e);
        }
    }

    public static void pushLocalToCloud(Context applicationContext) {
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                return;
            }

            TaskDao taskDao = AppDatabase.getInstance(applicationContext).taskDao();

            new Thread(() -> {
                try {
                    // 获取所有本地任务
                    List<Todo> localTasks = taskDao.getAll();
                    
                    // 批量上传到Parse
                    for (Todo todo : localTasks) {
                        toParse(todo).save();
                    }
                    
                    // 通知数据已更新
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        if (ParseUser.getCurrentUser() == null) return Result.success();
        ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
        TaskDao taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        query.whereEqualTo("user", ParseUser.getCurrentUser());
        try {
            // 获取本地和云端任务列表
            List<Todo> localTasks = taskDao.getAll();
            List<ParseObject> cloudDocs = query.find();

            // 构建映射: id -> Todo
            Map<String, Todo> localMap = new HashMap<>();
            for (Todo t : localTasks) {
                localMap.put(t.id, t);
            }
            Map<String, Todo> cloudMap = new HashMap<>();
            for (ParseObject obj : cloudDocs) {
                Todo cloudTodo = toTodo(obj);
                if (cloudTodo != null) {
                    cloudMap.put(cloudTodo.id, cloudTodo);
                }
            }
            // 构建所有任务ID的集合
            Set<String> allIds = new HashSet<>();
            allIds.addAll(localMap.keySet());
            allIds.addAll(cloudMap.keySet());

            // 合并逻辑：对于每个 id，选用更新时间较新的版本
            for (String id : allIds) {
                Todo localTodo = localMap.get(id);
                Todo cloudTodo = cloudMap.get(id);
                Todo mergedTodo = null;
                if (localTodo == null && cloudTodo != null) {
                    // 本地不存在，云端有：下载云端任务到本地
                    mergedTodo = cloudTodo;
                } else if (cloudTodo == null && localTodo != null) {
                    // 云端不存在，上传本地任务
                    mergedTodo = localTodo;
                } else if (localTodo != null && cloudTodo != null) {
                    // 双方都有：比较更新时间
                    if (localTodo.updatedAt >= cloudTodo.updatedAt) {
                        mergedTodo = localTodo;
                    } else {
                        mergedTodo = cloudTodo;
                    }
                }
                if (mergedTodo != null) {
                    // 更新本地数据库
                    taskDao.insertTodo(mergedTodo);
                    // 更新云端
                    toParse(mergedTodo).saveInBackground();
                }
            }
            return Result.success();
        } catch (ParseException e) {
            e.printStackTrace();
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
    }

    public static void cancelPeriodicSync(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("SyncWork");
    }

    // 推送本地TaskGroup到云端
    public static void pushTaskGroupsToCloud(Context applicationContext) {
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                return;
            }

            TaskGroupDao taskGroupDao = AppDatabase.getInstance(applicationContext).taskGroupDao();

            new Thread(() -> {
                try {
                    // 获取所有本地任务组
                    List<TaskGroup> localTaskGroups = taskGroupDao.getAllTaskGroups();
                    
                    // 批量上传到Parse
                    for (TaskGroup taskGroup : localTaskGroups) {
                        toParseTaskGroup(taskGroup).save();
                    }
                    
                    // 通知数据已更新
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 从云端拉取TaskGroup到本地
    public static void pullTaskGroupsToLocal(Context applicationContext) {
        try {
            // 检查用户是否登录
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d("SyncWorker", "用户未登录，跳过TaskGroup同步");
                return;
            }
            
            // 安全获取TaskGroupDao
            TaskGroupDao taskGroupDao;
            try {
                taskGroupDao = AppDatabase.getInstance(applicationContext).taskGroupDao();
                if (taskGroupDao == null) {
                    Log.e("SyncWorker", "数据库访问失败，无法同步TaskGroup");
                    return;
                }
            } catch (Exception e) {
                Log.e("SyncWorker", "获取TaskGroupDao失败", e);
                return;
            }
            
            // 创建查询并设置策略
            ParseQuery<ParseObject> query = ParseQuery.getQuery("TaskGroup");
            query.whereEqualTo("user", ParseUser.getCurrentUser());
            query.setMaxCacheAge(60 * 60 * 24); // 1天的缓存
            query.setCachePolicy(ParseQuery.CachePolicy.NETWORK_ELSE_CACHE);

            new Thread(() -> {
                try {
                    // 获取云端所有任务组
                    List<ParseObject> cloudDocs;
                    try {
                        cloudDocs = query.find();
                        if (cloudDocs == null) {
                            Log.w("SyncWorker", "云端TaskGroup查询返回null，可能是网络问题");
                            return;
                        }
                        
                        if (cloudDocs.isEmpty()) {
                            Log.d("SyncWorker", "云端无TaskGroup数据");
                        }
                    } catch (ParseException e) {
                        Log.e("SyncWorker", "查询云端TaskGroup失败: " + e.getMessage(), e);
                        return;
                    }
                    
                    // 获取本地所有任务组
                    List<TaskGroup> localTaskGroups;
                    try {
                        localTaskGroups = taskGroupDao.getAllTaskGroups();
                        if (localTaskGroups == null) {
                            localTaskGroups = new ArrayList<>();
                        }
                    } catch (Exception e) {
                        Log.e("SyncWorker", "获取本地TaskGroup失败", e);
                        return;
                    }

                    // 建立本地映射
                    Map<String, TaskGroup> localMap = new HashMap<>();
                    for (TaskGroup t : localTaskGroups) {
                        if (t != null && t.id != null) {
                            localMap.put(t.id, t);
                        }
                    }

                    // 遍历云端任务组, 仅执行"云 -> 本地"更新
                    for (ParseObject obj : cloudDocs) {
                        try {
                            TaskGroup cloudTaskGroup = toTaskGroup(obj);
                            if (cloudTaskGroup == null) continue;
                            
                            // 直接保存到本地
                            taskGroupDao.insertTaskGroup(cloudTaskGroup);
                        } catch (Exception e) {
                            Log.e("SyncWorker", "处理单个TaskGroup失败: " + e.getMessage());
                            // 继续处理下一个，不中断整个同步
                            continue;
                        }
                    }
                    
                    // 通知数据更新
                    try {
                        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                        applicationContext.sendBroadcast(intent);
                    } catch (Exception e) {
                        Log.e("SyncWorker", "发送广播失败: " + e.getMessage());
                    }
                } catch (Exception e) {
                    Log.e("SyncWorker", "TaskGroup同步失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            Log.e("SyncWorker", "启动TaskGroup同步失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
