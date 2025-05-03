package com.example.todolist.sync;

import android.content.Context;
import android.content.Intent;

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
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.FindCallback;
import com.parse.ParseException;

import com.google.android.gms.tasks.Tasks;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.*;

public class SyncWorker extends Worker {
    private static Todo toTodo(ParseObject o) {
        return new Todo(
                o.getString("uuid"),
                o.getString("title"),
                o.getLong("time"),
                o.getString("place"),
                o.getString("category"),
                o.getBoolean("completed")
        );
    }
    private static ParseObject toParse(Todo t) {
        ParseObject o = new ParseObject("Todo");
        o.put("uuid", t.uuid);
        o.put("title", t.title);
        o.put("time", t.time);
        o.put("place", t.place);
        o.put("category", t.category);
        o.put("completed", t.completed);
        o.put("clientUpdatedAt", t.clientUpdatedAt);
        o.put("deleted", t.deleted);
        o.put("belongsToTaskGroup", t.belongsToTaskGroup);
        o.put("user", ParseUser.getCurrentUser());
        return o;
    }
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void pullCloudToLocal(Context applicationContext) {
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                return;
            }
            ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
            TaskDao taskDao = AppDatabase.getInstance(applicationContext).taskDao();
            query.whereEqualTo("user", ParseUser.getCurrentUser());

            new Thread(() -> {
                try {
                    // 获取云端所有任务
                    List<ParseObject> cloudDocs = query.find();
                    // 获取本地所有任务
                    List<Todo> localTasks = taskDao.getAll();

                    // 建立本地映射
                    Map<String, Todo> localMap = new HashMap<>();
                    for (Todo t : localTasks) {
                        localMap.put(t.uuid, t);
                    }

                    // 遍历云端任务, 仅执行"云 -> 本地"更新
                    for (ParseObject obj : cloudDocs) {
                        Todo cloudTodo = toTodo(obj);
                        if (cloudTodo == null) continue;
                        Todo localTodo = localMap.get(cloudTodo.uuid);
                        // 如果本地为空, 或云端的更新更晚, 则覆盖本地
                        if (localTodo == null || cloudTodo.clientUpdatedAt > localTodo.clientUpdatedAt) {
                            taskDao.insertTodo(cloudTodo);
                        }
                    }
                    
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
                    
                    // 批量上传到Firestore
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

            // 构建映射: uuid -> Todo
            Map<String, Todo> localMap = new HashMap<>();
            for (Todo t : localTasks) {
                localMap.put(t.uuid, t);
            }
            Map<String, Todo> cloudMap = new HashMap<>();
            for (ParseObject obj : cloudDocs) {
                Todo cloudTodo = toTodo(obj);
                if (cloudTodo != null) {
                    cloudMap.put(cloudTodo.uuid, cloudTodo);
                }
            }
            // 构建所有任务ID的集合
            Set<String> allIds = new HashSet<>();
            allIds.addAll(localMap.keySet());
            allIds.addAll(cloudMap.keySet());

            // 合并逻辑：对于每个 uuid ，选用更新时间较新的版本（以后可以添加更复杂的冲突策略）
            for (String uuid : allIds) {
                Todo localTodo = localMap.get(uuid);
                Todo cloudTodo = cloudMap.get(uuid);
                Todo mergedTodo = null;
                if (localTodo == null && cloudTodo != null) {
                    // 本地不存在，云端有：下载云端任务到本地
                    mergedTodo = cloudTodo;
                } else if (cloudTodo == null && localTodo != null) {
                    // 云端不存在，上传本地任务
                    mergedTodo = localTodo;
                } else if (localTodo != null && cloudTodo != null) {
                    // 双方都有：比较更新时间
                    if (localTodo.clientUpdatedAt >= cloudTodo.clientUpdatedAt) {
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
}
