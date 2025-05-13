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

    public static ParseObject toParse(Todo t) {
        ParseObject o = new ParseObject("Todo"); // 类名 "Todo"
        o.put("uuid", t.id);
        o.put("title", t.title);
        o.put("time", t.time);
        o.put("place", t.place != null ? t.place : "");
        o.put("category", t.category != null ? t.category : "其他");
        o.put("completed", t.completed);
        o.put("clientUpdatedAt", t.updatedAt); // 使用本地的 updatedAt 作为云端追踪更新的依据
        o.put("deleted", t.deleted);
        o.put("belongsToTaskGroup", t.belongsToTaskGroup);
        o.put("priority", t.priority != null ? t.priority : "中");
        o.put("pomodoroEnabled", t.pomodoroEnabled != null ? t.pomodoroEnabled : false);
        o.put("pomodoroMinutes", t.pomodoroMinutes);
        o.put("pomodoroCompletedCount", t.pomodoroCompletedCount);
        o.put("points", t.points);

        ParseUser currentUser = ParseUser.getCurrentUser();
        if (currentUser != null) {
            o.put("user", currentUser); // 关联任务的创建者

            // --- 新增：设置初始 ACL for Todo ---
            ParseACL todoACL = new ParseACL(currentUser);
            todoACL.setPublicReadAccess(false);
            todoACL.setPublicWriteAccess(false);
            o.setACL(todoACL);
            // --- 结束新增 ACL ---
            // 理想情况: 如果 belongsToTaskGroup = true, 查询父 TaskGroup 的 ACL 并应用到这个 Todo。
            // 为了最小改动，暂时先这样。后续可以在 AddEditTaskActivity 创建 Todo 时，如果知道父 TaskGroup ID，
            // 获取父 TaskGroup 的 ACL 并设置给新 Todo 的 ParseObject。
        } else {
            Log.w("SyncWorker_toParse", "CurrentUser es null al crear ParseObject para Todo uuid: " + t.id);
        }
        return o;
    }

    private static TaskGroup toTaskGroup(ParseObject o) {
        final String TAG_INNER = "SyncWorker_toTaskGroup"; // 为内部方法日志添加特定标签

        if (o == null) {
            Log.w(TAG_INNER, "ParseObject a tratar es null. Retornando null.");
            return null;
        }

        Log.d(TAG_INNER, "Procesando ParseObject con objectId: " + o.getObjectId());

        try {
            TaskGroup taskGroup = new TaskGroup();
            taskGroup.uuid = o.getString("uuid");

            if (taskGroup.uuid == null) {
                Log.e(TAG_INNER, "TaskGroup 解析失败: El campo 'uuid' es null para objectId: " + o.getObjectId());
                return null;
            }
            Log.d(TAG_INNER, "uuid: " + taskGroup.uuid);

            taskGroup.title = o.getString("title");
            if (taskGroup.title == null) {
                Log.w(TAG_INNER, "El campo 'title' es null para uuid: " + taskGroup.uuid + ". Usando string vacío.");
                taskGroup.title = "";
            }
            Log.d(TAG_INNER, "title: " + taskGroup.title);

            taskGroup.category = o.getString("category");
            if (taskGroup.category == null) {
                Log.w(TAG_INNER, "El campo 'category' es null para uuid: " + taskGroup.uuid + ". Usando 'Otros'.");
                taskGroup.category = "其他"; // 默认类别
            }
            Log.d(TAG_INNER, "category: " + taskGroup.category);

            taskGroup.estimatedDays = o.has("estimatedDays") ? o.getInt("estimatedDays") : 1;
            Log.d(TAG_INNER, "estimatedDays: " + taskGroup.estimatedDays);

            // --- 关键：处理 createdAt ---
            if (o.has("createdAt")) {
                Object createdAtObj = o.get("createdAt"); // 先获取为 Object 类型
                if (createdAtObj instanceof Date) {
                    taskGroup.createdAt = ((Date) createdAtObj).getTime();
                    Log.d(TAG_INNER, "createdAt (de Date): " + taskGroup.createdAt + " para uuid: " + taskGroup.uuid);
                } else if (createdAtObj instanceof Number) {
                    // 如果后端仍然是 Number (尽管日志显示期望 Date)，这里尝试兼容
                    taskGroup.createdAt = ((Number) createdAtObj).longValue();
                    Log.w(TAG_INNER, "createdAt se leyó como Number (valor: " + taskGroup.createdAt + ") aunque se esperaba Date para uuid: " + taskGroup.uuid + ". Esto podría indicar un problema de schema persistente o datos mixtos.");
                } else if (createdAtObj != null) {
                    // 如果存在但类型未知
                    Log.e(TAG_INNER, "createdAt tiene un tipo inesperado: " + createdAtObj.getClass().getName() + " para uuid: " + taskGroup.uuid + ". Usando hora actual.");
                    taskGroup.createdAt = System.currentTimeMillis();
                } else {
                    // createdAtObj is null (o.get("createdAt") devolvió null)
                    Log.w(TAG_INNER, "El campo 'createdAt' es null (o.get devolvió null) para uuid: " + taskGroup.uuid + ". Usando hora actual.");
                    taskGroup.createdAt = System.currentTimeMillis();
                }
            } else {
                Log.w(TAG_INNER, "El campo 'createdAt' no existe para uuid: " + taskGroup.uuid + ". Usando hora actual.");
                taskGroup.createdAt = System.currentTimeMillis();
            }
            // --- 结束 createdAt 处理 ---

            List<String> subTaskIds = o.getList("subTaskIds");
            if (subTaskIds != null) {
                taskGroup.subTaskIds = subTaskIds;
                Log.d(TAG_INNER, "subTaskIds count: " + subTaskIds.size() + " para uuid: " + taskGroup.uuid);
            } else {
                taskGroup.subTaskIds = new ArrayList<>();
                Log.w(TAG_INNER, "subTaskIds es null para uuid: " + taskGroup.uuid + ". Usando lista vacía.");
            }

            // 检查 deleted 字段 (新增)
            if (o.has("deleted")) {
                taskGroup.deleted = o.getBoolean("deleted");
            } else {
                taskGroup.deleted = false; // 默认为未删除
                Log.w(TAG_INNER, "El campo 'deleted' no existe para uuid: " + taskGroup.uuid + ". Usando 'false' por defecto.");
            }
            Log.d(TAG_INNER, "deleted: " + taskGroup.deleted + " para uuid: " + taskGroup.uuid);


            Log.d(TAG_INNER, "ParseObject con objectId: " + o.getObjectId() + " convertido a TaskGroup exitosamente.");
            return taskGroup;

        } catch (Exception e) {
            Log.e(TAG_INNER, "Excepción al parsear ParseObject a TaskGroup para objectId: " + (o != null ? o.getObjectId() : "null") + ". Error: " + e.getMessage(), e);
            return null;
        }
    }

    private static ParseObject toParseTaskGroup(TaskGroup taskGroup) {
        ParseObject o = new ParseObject("TaskGroup"); // 类名 "TaskGroup"
        o.put("uuid", taskGroup.uuid);
        o.put("title", taskGroup.title);
        o.put("category", taskGroup.category);
        o.put("estimatedDays", taskGroup.estimatedDays);
        o.put("createdAt", new Date(taskGroup.createdAt)); // 确保是 Date 对象
        o.put("subTaskIds", taskGroup.subTaskIds != null ? taskGroup.subTaskIds : new ArrayList<String>());
        o.put("deleted", taskGroup.deleted); // 确保上传 deleted 状态

        ParseUser currentUser = ParseUser.getCurrentUser();
        if (currentUser != null) {
            o.put("user", currentUser); // 关联创建者

            // --- 新增：设置初始 ACL ---
            ParseACL taskGroupACL = new ParseACL(currentUser); // 创建者拥有完全权限
            taskGroupACL.setPublicReadAccess(false);  // 禁止公共读取
            taskGroupACL.setPublicWriteAccess(false); // 禁止公共写入
            o.setACL(taskGroupACL);
            // --- 结束新增 ACL ---
        } else {
            Log.w("SyncWorker_toParseTaskGroup", "CurrentUser es null al crear ParseObject para TaskGroup uuid: " + taskGroup.uuid);
            // 可以考虑是否允许匿名创建，或者抛出错误
        }
        return o;
    }

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void pullCloudToLocal(Context applicationContext) {
        final String TAG_PULL_TODO = "SyncWorker_pullTodos";
        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d(TAG_PULL_TODO, "Usuario no logueado, saltando sincronización de Todo.");
                return;
            }

            TaskDao taskDao;
            try {
                taskDao = AppDatabase.getInstance(applicationContext).taskDao();
                if (taskDao == null) {
                    Log.e(TAG_PULL_TODO, "Acceso a la base de datos fallido, no se puede sincronizar Todo.");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG_PULL_TODO, "Error al obtener TaskDao.", e);
                return;
            }

            Log.d(TAG_PULL_TODO, "Iniciando pull de Todos desde Parse para el usuario: " + user.getUsername());

            ParseQuery<ParseObject> query = ParseQuery.getQuery("Todo");
            // --- 修改点：移除 .whereEqualTo("user", user) ---
            // query.whereEqualTo("user", user); // <--- 删除或注释掉此行


            final TaskDao finalTaskDao = taskDao;
            new Thread(() -> {
                try {
                    List<ParseObject> cloudDocs = query.find();

                    if (cloudDocs == null) {
                        Log.w(TAG_PULL_TODO, "La consulta de Todo a Parse devolvió null.");
                        return;
                    }
                    Log.d(TAG_PULL_TODO, "Se encontraron " + cloudDocs.size() + " Todos en Parse (accesibles por el usuario actual).");

                    // (可选，但推荐) 为了同步删除操作
                    List<Todo> localTodos = finalTaskDao.getAll(); // 假设getAll获取所有，包括已删除的
                    Map<String, Todo> localTodoMap = new HashMap<>();
                    for(Todo lt : localTodos) {
                        if(lt.id != null) localTodoMap.put(lt.id, lt);
                    }
                    Set<String> cloudTodoIds = new HashSet<>();


                    int successCount = 0;
                    int failureCount = 0;

                    for (ParseObject obj : cloudDocs) {
                        String uuid = obj.getString("uuid");
                        if (uuid != null) cloudTodoIds.add(uuid);

                        Log.d(TAG_PULL_TODO, "Procesando ParseObject de Todo con objectId: " + obj.getObjectId() + ", uuid: " + uuid);
                        Todo cloudTodo = toTodo(obj); // toTodo 也需要确保能正确解析来自云端的数据

                        if (cloudTodo != null) {
                            // 简单的覆盖策略，可以根据 clientUpdatedAt 优化
                            Todo localTodo = localTodoMap.get(cloudTodo.id);
                            if (localTodo == null || cloudTodo.updatedAt >= localTodo.updatedAt) {
                                Log.d(TAG_PULL_TODO, "Insertando/Actualizando Todo localmente (uuid: " + cloudTodo.id + ", title: " + cloudTodo.title +", deleted: " + cloudTodo.deleted + ")");
                                finalTaskDao.insertTodo(cloudTodo); // 保存到本地数据库
                                successCount++;
                            } else {
                                Log.d(TAG_PULL_TODO, "Todo local (uuid: " + cloudTodo.id + ") es más reciente. No actualizando desde la nube.");
                                // Potentially push local changes back if there's a conflict and local is newer.
                                // For now, we just skip updating local from cloud.
                            }
                        } else {
                            Log.e(TAG_PULL_TODO, "Falló la conversión de ParseObject a Todo para objectId: " + obj.getObjectId() + ". Saltando este objeto.");
                            failureCount++;
                        }
                    }
                    Log.d(TAG_PULL_TODO, "Sincronización de Todo (pull) completada. Éxitos: " + successCount + ", Fallos: " + failureCount);

                    // (可选，但推荐) 处理在云端被删除但在本地仍然存在的Todo
                    for(Map.Entry<String, Todo> entry : localTodoMap.entrySet()){
                        if(!cloudTodoIds.contains(entry.getKey())){
                            Log.d(TAG_PULL_TODO, "Todo local (uuid: " + entry.getKey() + ") no encontrado en la nube, marcando como eliminado localmente.");
                            Todo todoToDeleteLocally = entry.getValue();
                            if (!todoToDeleteLocally.deleted) { // 只处理尚未在本地删除的
                                todoToDeleteLocally.deleted = true;
                                todoToDeleteLocally.updatedAt = System.currentTimeMillis();
                                finalTaskDao.insertTodo(todoToDeleteLocally);
                            }
                        }
                    }

                    Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                    applicationContext.sendBroadcast(intent);
                    Log.d(TAG_PULL_TODO, "Broadcast ACTION_DATA_UPDATED enviado después de pull Todos.");

                } catch (ParseException e) {
                    Log.e(TAG_PULL_TODO, "Error de Parse al obtener Todos: " + e.getCode() + " - " + e.getMessage(), e);
                } catch (Exception e) {
                    Log.e(TAG_PULL_TODO, "Excepción general durante la sincronización de Todo: " + e.getMessage(), e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG_PULL_TODO, "Error al iniciar el hilo de sincronización de Todo.", e);
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
        final String TAG_PULL = "SyncWorker_pullGroups"; // 特定标签

        try {
            ParseUser user = ParseUser.getCurrentUser();
            if (user == null) {
                Log.d(TAG_PULL, "Usuario no logueado, saltando sincronización de TaskGroup.");
                return;
            }

            TaskGroupDao taskGroupDao;
            try {
                taskGroupDao = AppDatabase.getInstance(applicationContext).taskGroupDao();
                if (taskGroupDao == null) {
                    Log.e(TAG_PULL, "Acceso a la base de datos fallido, no se puede sincronizar TaskGroup.");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG_PULL, "Error al obtener TaskGroupDao.", e);
                return;
            }

            Log.d(TAG_PULL, "Iniciando pull de TaskGroups desde Parse para el usuario: " + user.getUsername());

            ParseQuery<ParseObject> query = ParseQuery.getQuery("TaskGroup");
//            query.whereEqualTo("user", user); // 只获取当前用户的数据



            final TaskGroupDao finalTaskGroupDao = taskGroupDao; // DAO 必须是 final 才能在内部类中使用
            new Thread(() -> {
                try {
                    List<ParseObject> cloudDocs = query.find(); // 从云端获取

                    if (cloudDocs == null) {
                        Log.w(TAG_PULL, "La consulta de TaskGroup a Parse devolvió null. Posiblemente problema de red o servidor.");
                        return;
                    }

                    Log.d(TAG_PULL, "Se encontraron " + cloudDocs.size() + " TaskGroups en Parse.");

                    List<TaskGroup> localGroups = finalTaskGroupDao.getAllTaskGroups(); // 确保这个方法只拿未删除的
                    Map<String, TaskGroup> localGroupMap = new HashMap<>();
                    for (TaskGroup lg : localGroups) {
                        if (lg.uuid != null) localGroupMap.put(lg.uuid, lg);
                    }
                    Set<String> cloudGroupIds = new HashSet<>();

                    int successCount = 0;
                    int failureCount = 0;

                    for (ParseObject obj : cloudDocs) {
                        String uuid = obj.getString("uuid"); // 获取 uuid
                        if (uuid != null) cloudGroupIds.add(uuid);

                        Log.d(TAG_PULL, "Procesando ParseObject de TaskGroup con objectId: " + obj.getObjectId() + ", uuid: " + uuid);
                        TaskGroup cloudTaskGroup = toTaskGroup(obj); // 使用更新后的 toTaskGroup


                        if (cloudTaskGroup != null) {
                            // 检查本地的更新时间与云端的比较 (简单策略：云端覆盖本地，如果云端更新)
                            // 注意：toTaskGroup 中 createdAt 已转换为 long。ParseObject 的 updatedAt 是 Parse 自动管理的。
                            // 我们在 toParseTaskGroup 中没有放 clientUpdatedAt，可以考虑加上 clientUpdatedAt 字段用于更精确的同步冲突解决。
                            // 为简单起见，这里直接替换。
                            Log.d(TAG_PULL, "Insertando/Actualizando TaskGroup localmente (uuid: " + cloudTaskGroup.uuid + ", title: " + cloudTaskGroup.title + ", deleted: " + cloudTaskGroup.deleted +")");
                            finalTaskGroupDao.insertTaskGroup(cloudTaskGroup);
                            successCount++;
                        } else {
                            Log.e(TAG_PULL, "Falló la conversión de ParseObject a TaskGroup para objectId: " + obj.getObjectId() + ". Saltando este objeto.");
                            failureCount++;
                        }
                    }
                    Log.d(TAG_PULL, "Sincronización de TaskGroup completada. Éxitos: " + successCount + ", Fallos: " + failureCount);

                    for(Map.Entry<String, TaskGroup> entry : localGroupMap.entrySet()){
                        if(!cloudGroupIds.contains(entry.getKey())){
                            // 这个TaskGroup在本地存在，但在云端查询结果中没有 (可能被删除，或不再有权限)
                            // 为了简化，我们假设这是因为被删除了
                            Log.d(TAG_PULL, "TaskGroup local (uuid: " + entry.getKey() + ") no encontrado en la nube, marcando como eliminado localmente.");
                            TaskGroup groupToDeleteLocally = entry.getValue();
                            groupToDeleteLocally.deleted = true;
                            finalTaskGroupDao.insertTaskGroup(groupToDeleteLocally);
                        }
                    }

                    // 通知数据更新
                    Intent intent = new Intent("com.example.todolist.ACTION_DATA_UPDATED");
                    applicationContext.sendBroadcast(intent);
                    Log.d(TAG_PULL, "Broadcast ACTION_DATA_UPDATED enviado.");

                } catch (ParseException e) {
                    Log.e(TAG_PULL, "Error de Parse al obtener TaskGroups: " + e.getCode() + " - " + e.getMessage(), e);
                } catch (Exception e) {
                    Log.e(TAG_PULL, "Excepción general durante la sincronización de TaskGroup: " + e.getMessage(), e);
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG_PULL, "Error al iniciar el hilo de sincronización de TaskGroup.", e);
        }
    }
}
