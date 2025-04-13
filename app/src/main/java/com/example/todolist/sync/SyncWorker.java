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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.android.gms.tasks.Tasks;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.*;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void pullCloudToLocal(Context applicationContext) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        String uid = user.getUid();
        TaskDao taskDao = AppDatabase.getInstance(applicationContext).taskDao();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference cloudTasksRef = db.collection("users").document(uid).collection("tasks");

        new Thread(() -> {
            try {
                // 获取云端所有任务
                QuerySnapshot snapshot = com.google.android.gms.tasks.Tasks.await(cloudTasksRef.get());
                List<DocumentSnapshot> cloudDocs = snapshot.getDocuments();
                // 获取本地所有任务
                List<Todo> localTasks = taskDao.getAll();

                // 建立本地映射
                Map<String, Todo> localMap = new HashMap<>();
                for (Todo t : localTasks) {
                    localMap.put(t.id, t);
                }

                // 遍历云端任务, 仅执行"云 -> 本地"更新
                for (DocumentSnapshot doc : cloudDocs) {
                    Todo cloudTodo = doc.toObject(Todo.class);
                    if (cloudTodo == null) continue;
                    Todo localTodo = localMap.get(cloudTodo.id);
                    // 如果本地为空, 或云端的更新更晚, 则覆盖本地
                    if (localTodo == null || cloudTodo.updatedAt > localTodo.updatedAt) {
                        taskDao.insertTodo(cloudTodo);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
        applicationContext.sendBroadcast(intent);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.success();
        }
        String uid = user.getUid();
        TaskDao taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference cloudTasksRef = db.collection("users").document(uid).collection("tasks");

        try {
            // 获取本地和云端任务列表
            List<Todo> localTasks = taskDao.getAll();
            QuerySnapshot snapshot = Tasks.await(cloudTasksRef.get());
            List<DocumentSnapshot> cloudDocs = snapshot.getDocuments();

            // 构建映射: id -> Todo
            Map<String, Todo> localMap = new HashMap<>();
            for (Todo t : localTasks) {
                localMap.put(t.id, t);
            }
            Map<String, Todo> cloudMap = new HashMap<>();
            for (DocumentSnapshot doc : cloudDocs) {
                Todo cloudTodo = doc.toObject(Todo.class);
                if (cloudTodo != null) {
                    cloudMap.put(cloudTodo.id, cloudTodo);
                }
            }
            // 构建所有任务ID的集合
            Set<String> allIds = new HashSet<>();
            allIds.addAll(localMap.keySet());
            allIds.addAll(cloudMap.keySet());

            // 合并逻辑：对于每个 id ，选用更新时间较新的版本（以后可以添加更复杂的冲突策略）
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
                    cloudTasksRef.document(mergedTodo.id).set(mergedTodo);
                }
            }
            return Result.success();
        } catch (ExecutionException | InterruptedException e) {
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
