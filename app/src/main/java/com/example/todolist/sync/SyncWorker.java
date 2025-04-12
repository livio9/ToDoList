package com.example.todolist.sync;

import android.content.Context;
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
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // 未登录用户，不执行任何操作
            return Result.success();
        }
        String uid = user.getUid();
        TaskDao taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference cloudTasksRef = db.collection("users").document(uid).collection("tasks");
        try {
            // 1. 上传本地所有任务到云端
            List<Todo> localTasks = taskDao.getAll();
            List<com.google.android.gms.tasks.Task<?>> uploadTasks = new ArrayList<>();
            for (Todo todo : localTasks) {
                uploadTasks.add(cloudTasksRef.document(todo.id).set(todo));
            }
            // 等待所有上传任务完成
            Tasks.await(Tasks.whenAll(uploadTasks));
            // 2. 从云端获取该用户的所有任务列表
            QuerySnapshot snapshot = Tasks.await(cloudTasksRef.get());
            List<DocumentSnapshot> documents = snapshot.getDocuments();
            for (DocumentSnapshot doc : documents) {
                Todo cloudTodo = doc.toObject(Todo.class);
                if (cloudTodo != null) {
                    // 将云端任务合并到本地数据库（本地没有则插入，有则更新）
                    taskDao.insertTodo(cloudTodo);
                }
            }
            return Result.success();  // 同步成功
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            // 同步过程中出现异常，建议重试
            return Result.retry();
        }
    }

    // 静态方法：安排周期性同步任务（例如每15分钟执行一次）
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // 仅在网络连接时执行
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("SyncWork", ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    // 静态方法：立即执行一次同步
    public static void triggerSyncNow(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    // 静态方法：取消定时同步任务
    public static void cancelPeriodicSync(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("SyncWork");
    }
}
