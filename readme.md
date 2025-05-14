# 移动应用软件开发中期报告 - 待办事项App

## 1. 项目背景

### （1）项目概述

随着智能手机的普及，管理个人事务变得越来越重要。本项目旨在开发一款**待办事项App**，通过Android平台为用户提供高效的任务管理工具。应用支持任务的增、删、改、查操作，提供任务筛选、排序、分类管理等功能，帮助用户合理安排时间，提高工作效率。

在功能设计上，待办事项App的目标是：

- 提供一个简洁且易于操作的用户界面，让用户能够快速查看和管理自己的待办事项。
- 让用户能够创建、编辑和删除任务，并为每个任务设置标题、时间、地点、类别等信息。
- 通过任务状态（已完成/未完成）进行管理，用户可以快速查看已完成和未完成的任务。
- 支持按时间、类别和状态对任务进行筛选，帮助用户更好地组织任务列表。
- 任务数据能够通过Firebase云端同步，确保数据在不同设备间的一致性。

应用利用**Room Database**进行本地存储，确保即使在无网络情况下，用户也能查看和操作任务数据；并通过**Firebase Firestore**实现云端数据同步，保证数据的持久性和安全性。同步操作通过**WorkManager**定时执行，确保任务数据及时更新。

### （2） 项目目标

本项目的主要目标是开发一个符合以下要求的待办事项App：

- **简洁直观的用户界面**：
  - 用户能够方便地添加、编辑、删除任务。
  - 提供任务的时间、地点、类别等信息录入和修改功能。
- **任务管理功能**：
  - 支持任务按时间、类别、地点等标签进行筛选。
  - 支持任务按已完成/未完成状态进行标记，帮助用户查看任务进展。
  - 支持按时间进行排序，帮助用户管理任务的优先级。
- **本地数据库和云端同步**：
  - 本地数据库通过**Room Database**存储用户的任务数据。无论用户处于在线还是离线状态，都能够正常使用App。
  - 所有任务数据同步至云端的**Firebase Firestore**，确保数据在多个设备间保持一致。通过同步机制，用户的数据在网络连接恢复时会自动更新。
- **高效的同步机制**：
  - **WorkManager**用于管理周期性任务同步，确保数据在网络可用时及时同步至云端。同步频率和策略通过应用需求进行调整。

------

### （3）关键功能

#### 用户账户管理

- **注册与登录**：
  - 用户通过**Firebase Auth**进行账户注册与登录，提供安全的身份验证。
  - 如果用户已经登录，应用直接进入主界面（**MainActivity**），无需重新登录。
- **账户注销**：
  - 提供用户注销功能，用户可以随时退出账户，返回登录页面。

#### 任务管理

- **任务创建、编辑、删除**：
  - 用户可以添加新的待办事项，编辑现有任务信息（标题、时间、地点、类别）。
  - 任务的时间信息使用时间戳进行存储，方便后期排序和筛选。
  - 使用**Room Database**将任务数据存储到本地，并通过**Firebase Firestore**进行同步。
- **任务状态管理**：
  - 用户可以通过点击复选框标记任务为已完成，未完成的任务则显示正常样式，已完成的任务会带有删除线。
  - 任务的完成状态可以同步到云端，确保数据一致性。

#### 数据同步

- **本地与云端同步**：
  - 使用**WorkManager**定期同步任务数据，确保本地任务与云端数据保持一致。
  - 在网络恢复时，应用自动将本地数据同步到Firebase Firestore。

------

### （4）开发计划

中期：2025.04.14（第十三周周一），完成项目基本的结构与功能。

期末：2025.05.16（第十三周周五）前：展示完整设计后的UI界面，介绍核心亮点，Demo演示与验收

---



## 2. 功能需求分析

### 系统角色

系统的主要角色包括**普通用户**和**管理员**。普通用户是系统的主要操作对象，系统为每个普通用户提供个人账户管理功能，包括注册、登录、查看和编辑待办事项，以及将任务数据同步至云端。登录后，用户可以访问所有待办事项管理功能，如创建、编辑、删除任务，并通过任务筛选和排序功能高效管理任务。用户的数据存储在云端和本地，系统通过严格的隐私保护措施确保数据安全。管理员则拥有管理权限，负责系统的整体维护和管理工作，包括监控系统的运行状况和提供用户支持服务。管理员的职责不涉及直接访问用户的私人数据，从而确保用户隐私的保护。

系统的后端将使用**Firebase**提供认证和云端数据存储服务，而前端将通过**Room数据库**本地存储数据，以保证用户即使在无网络时也能正常使用应用。

------

### 用户需求

##### 任务管理

用户可以轻松创建、查看、修改、删除待办事项。每个任务都可以附带标题、时间、地点、类别等信息。任务的状态（已完成/未完成）能够快速切换，帮助用户管理每日的工作和生活。每个任务的完成情况能够实时更新，确保用户能有效地跟踪进度。

##### 筛选和排序

用户希望能够根据不同的筛选条件来查看任务列表，例如：按**时间**筛选（今天、下周或所有任务），按**类别**筛选（工作、个人等），以及按**任务状态**筛选（已完成、未完成）。此外，任务还可以按时间顺序进行升序或降序排列。

##### 数据同步

用户的数据可以通过Firebase同步到云端，确保在多设备间的任务一致性。通过**WorkManager**，系统可以定期或手动触发任务同步，确保本地任务数据和云端数据保持一致。

##### 用户界面设计

用户界面将简洁且功能性强，旨在提供流畅的用户体验：

- 登录界面允许用户通过电子邮件登录系统，且注册流程简便。
- 主界面展示用户的待办事项列表，并且提供任务筛选和排序功能。
- 添加/编辑任务界面使用户能够方便地设置任务信息，包括标题、时间、地点、类别和状态。

##### 异常处理与通知

为了保证系统的可靠性，任务同步的异常处理机制将非常重要。在同步过程中若发生错误，系统应提供重试机制，并且提示用户同步状态。对于任务的提醒功能，当任务接近到期时，系统会通过通知提醒用户，避免重要任务被遗忘。

------

### 非功能需求

##### 安全性

系统会使用**Firebase Auth**进行用户认证，确保任务数据只能被授权的用户访问。所有存储的数据将加密上传到云端，确保用户隐私不被泄露。此外，如果出现异常访问行为，系统应及时提醒用户。

##### 性能要求

系统的响应时间将保持在可接受的范围内。具体来说，任务的添加、修改、删除等操作响应时间不应超过2秒。同步数据时，系统应保证同步延迟不超过5秒。同时，系统需要支持离线模式，确保用户即使在没有网络的情况下，也能操作和管理任务，并在恢复网络连接后完成数据同步。

------

### 未来扩展功能

尽管当前版本实现了任务管理和数据同步等基础功能，未来可以考虑增加更多智能化和交互功能：

- **任务共享**：允许用户与他人共享任务，方便协作管理。
- **语音输入**：通过语音识别来创建任务，提高操作的便捷性。
- **智能推荐**：根据用户的历史任务数据，智能推荐新的任务或提醒用户未完成的任务。
- **统计信息：**将用户的任务完成情况以可视化的方式展现给用户（例如图，表）。

---





## 3.  技术栈

### 前端 UI 应用技术

- **基础技术**：Java + Android SDK
- **布局设计**：使用 XML 定义 UI 界面，在 Java Activity 里通过 `findViewById()` 绑定 UI 组件
- **重要组件说明**：
  - `RecyclerView` + `Adapter` ：用于列表显示任务，配合 `TaskAdapter` 自定义样式
  - `FloatingActionButton` ：点击后打开新增任务界面
  - `EditText` + `Button`：用于输入任务内容并保存

**优点**：

- 基于 Android 原生技术，性能高效、系统稳定
- XML 开发 UI 直观，适合新手上手
- 给予组件构造和风格自由度

**缺点**：

- 开发效率低，很多组件需要手动绑定
- XML + Java 分离，带来维护成本
- 在处理复杂界面和动画时，性能不优

### 后端

- **Firebase (Auth, Firestore)**：
  - **Firebase Auth**用于实现用户认证功能，用户可以通过电子邮件进行注册、登录，保障系统的安全性。
  - **Firebase Firestore**用于存储任务数据，所有待办事项都将同步到云端，以确保数据在不同设备间的一致性。
  - 通过Firebase的实时数据更新功能，确保用户任务数据的同步。

### 本地数据存储技术

Room 是 Android 为了简化本地数据存取而开发的数据库库，它是对 SQLite 的封装。目的是使开发者无需写原生 SQL 语句也能方便地存取数据。

- 基本组成:

  - **Entity 实体** (`Todo.java`)：使用 `@Entity(tableName = "todo_table")`，代表数据库中的一个表。包含 ID，内容，是否完成等字段：

    ```java
    @Entity(tableName = "todo_table")
    public class Todo {
        @PrimaryKey(autoGenerate = true)
        private int id;
        private String task;
        private boolean completed;
        // getter/setter 看法以便 Room 调用
    }
    ```

  - **DAO (数据操作接口)** `TaskDao.java`：这是一个接口，定义了查询、添加、更新、删除的操作。当 Room 系统生成代码时，会根据这里的方法生成对应的 SQL 调用：

    ```java
    @Dao
    public interface TaskDao {
        @Insert
        void insert(Todo todo);
    
        @Update
        void update(Todo todo);
    
        @Delete
        void delete(Todo todo);
    
        @Query("SELECT * FROM todo_table ORDER BY id DESC")
        List<Todo> getAllTasks();
    }
    ```

  - **AppDatabase (数据库实现)** `AppDatabase.java`：继承自 `RoomDatabase`，给 Room 指定 DAO 接口：

    ```java
    @Database(entities = {Todo.class}, version = 1)
    public abstract class AppDatabase extends RoomDatabase {
        public abstract TaskDao taskDao();
    }
    ```

- **Room 操作流程：**

  1. 开发者定义 Entity (表结构)
  2. 定义 DAO (操作方法)
  3. 通过 AppDatabase 创建库对象
  4. 在 Activity 中调用 DAO 实现操作

Room 是保证数据存储和读取的主要技术支撑，适合本地数据压缩、无网操作等场景。

### 背景任务 & 云同步

WorkManager 是 Android Jetpack 提供的一个管理延时和远程执行任务的 API，适合如同步云数据、最后一次推送的运行。

**在本项目中：**

- `SyncWorker.java` 执行同步操作：

  ```java
  public class SyncWorker extends Worker {
      public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
          super(context, params);
      }
  
      @NonNull
      @Override
      public Result doWork() {
          // 此处编写了本地数据和云端同步逻辑
          // 这里可以调用 API 进行数据推送，如果成功，返回 success
          return Result.success();
      }
  }
  ```

- 在任意一个 Activity 里使用 WorkManager 调度：

  ```java
  WorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class).build();
  WorkManager.getInstance(context).enqueue(syncRequest);
  ```

**优点**：

- 可以在未打开 App 时也执行指定任务
- 可配合网络条件、电量条件进行规则设置
- 合适无网同步、延迟推送等场景

**缺点**：

- 必须配合网络 API 调用，需要自己编写 POST/PUT/用户校验等逻辑

总结：Room 用于本地数据存储，WorkManager 用于将本地数据背景同步到云端，同时两者配合使用可以实现非常简洁效率的本地数据 + 云同步的组合功能。

## 4. 项目设计

#### （1）用户身份登录模块（LoginActivity）

- **LoginActivity.java**
  - **主要功能**
    - 实现简单的用户登录界面（使用 Firebase Authentication 进行身份验证）。
    - 主要职责：
    - 输入邮箱、密码（后续可开通手机号码等方式）
    - 登录后跳转至 MainActivity
    - 登录成功后执行云端同步（ pullCloudToLocal() )
  - **实现亮点**
    1. 自动登录判断逻辑

```java
FirebaseUser currentUser = auth.getCurrentUser();
if (currentUser != null) {
    startActivity(new Intent(LoginActivity.this, MainActivity.class));
    finish();
    return;
}
```

- 功能：
  - 如果用户之前已经登录（auth.getCurrentUser() != null）
  - 直接跳过登录界面，进入 MainActivity
  - 这种逻辑可以让用户“一次登录，永久记住”，非常友好

    2.  登录逻辑实现（点击登录按钮）

```java
auth.signInWithEmailAndPassword(email, password)  
                    .addOnCompleteListener(task -> {  
                        if (task.isSuccessful()) {  
                            // 登录成功，进入主界面  
                            Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();  
//                            SyncWorker.triggerSyncNow(getApplicationContext());  
                            SyncWorker.pullCloudToLocal(getApplicationContext());  
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {  
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));  
                                finish();  
                            }, 1000); // 延迟1秒进入主界面  
                        } else {  
                            // 登录失败  
                            Toast.makeText(LoginActivity.this, "登录失败："  
                                            + (task.getException() != null ? task.getException().getMessage() : ""),  
                                    Toast.LENGTH_SHORT).show();  
                            Exception e = task.getException();  
                            e.printStackTrace();  
                            Log.e("FirebaseAuth", "登录失败", e);  
                            Toast.makeText(LoginActivity.this, "登录失败：" + (e != null ? e.getMessage() : "未知错误"), Toast.LENGTH_LONG).show();  
                        }  
                    });
```

登录成功流程：

- 弹出 Toast 提示 “登录成功”

- 调用 SyncWorker.pullCloudToLocal() 手动拉取云端数据

- 使用 Handler.postDelayed() 延迟 1 秒进入主界面

  3. 注册逻辑实现（点击注册按钮）

    - 注册成功后：
      - 不需要重新登录，Firebase 会自动设置当前用户
      - 直接跳转主界面即可

```
btnRegister.setOnClickListener(v -> {
    ...
    auth.createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // 注册成功，进入主界面
                ...
            } else {
                // 注册失败
                ...
            }
        });
});
```

<img src="screen_shots\登录界面.jpg" style="zoom:25%;" />

#### （2） 任务展示与交互模块（MainActivity + TaskAdapter）

##### **MainActivity.java**

`MainActivity.java` 是整个应用的入口页面，主要负责展示用户的待办事项。它包括以下主要功能：

1. **加载本地数据库中的任务数据**：

   - 通过访问本地数据库（`Room`），从 `TaskDao` 获取所有待办事项（`Todo`）。
   - 使用 `taskDao.getAll()` 方法获取所有任务数据并显示。

2. **初始化 RecyclerView 和 TaskAdapter**：

   - `RecyclerView` 用于展示待办事项列表。`RecyclerView` 的适配器 `TaskAdapter` 会负责将每个任务显示为一个卡片（`CardView`）。

   - 通过 `taskList = db.taskDao().getAllTasks()` 获取任务列表，并将其传递给 `TaskAdapter` 进行显示。

   - 代码示例：

     ```java
     taskList = db.taskDao().getAllTasks();
     adapter = new TaskAdapter(taskList, this);
     recyclerView.setAdapter(adapter);
     ```

3. **设置点击事件监听**：

   - `RecyclerView` 中的每个项都能响应点击事件，点击某个任务会进入编辑界面（`AddEditTaskActivity`）。

   - 代码示例：

     ```java
     adapter.setOnItemClickListener(todo -> {
         Intent editIntent = new Intent(MainActivity.this, AddEditTaskActivity.class);
         editIntent.putExtra("todo", todo);
         startActivity(editIntent);
     });
     ```

4. **设置长按事件监听**：

   - 长按某个任务会弹出删除确认框，用户可以选择删除该任务。

   - 删除操作会删除本地和云端的对应任务。

   - 代码示例：

     ```java
     adapter.setOnItemLongClickListener(todo -> {
         new AlertDialog.Builder(MainActivity.this)
             .setTitle("删除确认")
             .setMessage("确定删除该待办事项吗？")
             .setPositiveButton("删除", (dialog, which) -> {
                 new Thread(() -> taskDao.deleteTodo(todo)).start();
                 if (auth.getCurrentUser() != null) {
                     firestore.collection("users")
                         .document(auth.getCurrentUser().getUid())
                         .collection("tasks").document(todo.id)
                         .delete();
                 }
                 allTasks.remove(todo);
                 applyFiltersAndRefresh();
                 Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
             })
             .setNegativeButton("取消", null)
             .show();
     });
     ```

5. **初始化筛选下拉菜单**：

   - 提供时间、类别和任务状态的筛选功能，用户可以根据筛选条件过滤任务。

   - 代码示例：

     ```java
     String[] timeOptions = {"全部时间", "今天", "本周内", "已过期"};
     ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, timeOptions);
     spinnerTime.setAdapter(timeAdapter);
     ```

6. **添加任务的浮动按钮**：

   - 在主界面底部展示一个浮动按钮，用户点击后可以进入添加任务的页面（`AddEditTaskActivity`）。

   - 代码示例：

     ```java
     FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
     fabAdd.setOnClickListener(v -> {
         Intent addIntent = new Intent(MainActivity.this, AddEditTaskActivity.class);
         startActivity(addIntent);
     });
     ```

7. **定时同步任务**：

   - 通过 `SyncWorker.schedulePeriodicSync()` 定时同步任务数据到云端，确保本地和云端数据的一致性。

   - 代码示例：

     ```java
     if (auth.getCurrentUser() != null) {
         SyncWorker.schedulePeriodicSync(getApplicationContext());
         SyncWorker.triggerSyncNow(getApplicationContext());
     }
     ```

##### **TaskAdapter.java** 

`TaskAdapter.java` 是 `RecyclerView` 的适配器，负责将任务数据显示为卡片形式，并处理任务状态的展示和交互。其主要功能包括：

1. **显示任务内容与状态**：

   - 每个任务通过 `TextView` 显示任务的标题、时间、地点和类别。

   - 根据任务的完成状态，任务标题会显示删除线，并且设置为灰色，未完成任务则显示正常的黑色标题。

   - 代码示例：

     ```java
     if (todo.completed) {
         holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
         holder.textTitle.setTextColor(0xFF888888);  // 灰色
     } else {
         holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
         holder.textTitle.setTextColor(0xFF000000);  // 黑色
     }
     ```

2. **任务复选框的交互**：

   - 每个任务项会有一个复选框，用户勾选时，任务的完成状态会更新。更新后，本地数据库和云端都将同步更新任务状态。

   - 代码示例：

     ```java
     holder.checkDone.setOnClickListener(v -> {
         boolean newStatus = holder.checkDone.isChecked();
         todo.completed = newStatus;
         new Thread(() -> {
             AppDatabase.getInstance(context).taskDao().updateTodo(todo);
         }).start();
         if (auth.getCurrentUser() != null) {
             FirebaseFirestore.getInstance().collection("users")
                 .document(auth.getCurrentUser().getUid())
                 .collection("tasks").document(todo.id)
                 .update("completed", newStatus);
         }
     });
     ```

3. **列表项点击事件**：

   - 用户点击任务时，任务的详细信息会传递给 `AddEditTaskActivity` 进行编辑。

   - 代码示例：

     ```java
     holder.itemView.setOnClickListener(v -> {
         if (itemClickListener != null) {
             itemClickListener.onItemClick(todo);
         }
     });
     ```

4. **列表项长按事件**：

   - 用户长按任务时，会弹出删除确认对话框，确认后删除任务并同步更新本地和云端数据。

   - 代码示例：

     ```java
     holder.itemView.setOnLongClickListener(v -> {
         if (itemLongClickListener != null) {
             itemLongClickListener.onItemLongClick(todo);
         }
         return true;
     });
     ```

​                               <img src="screen_shots\主页面.jpg" style="zoom:25%;" />               <img src="screen_shots\列表.jpg" style="zoom:25%;" />    



#### （3）任务新增与编辑模块（AddEditTaskActivity）

`AddEditTaskActivity.java` 主要用于任务的新增和编辑，判断是否为编辑模式还是新增模式。根据任务的 ID，进入相应的模式，界面包含标题、地点、时间、类别等输入项，用户可以修改任务信息并保存。

#####  **编辑模式与新增模式**

- **编辑模式**：加载已有任务信息进行编辑。
- **新增模式**：创建一个新的任务。

```java
if (currentTodo != null) {
    // 编辑模式：填充现有数据
    editTitle.setText(currentTodo.title);
    // ... 其他字段
} else {
    // 新增模式：初始化默认值
    currentTodo = new Todo();
}
```

###### **保存任务**:根据是编辑还是新增，保存任务到本地数据库并同步到云端。

```java
buttonSave.setOnClickListener(v -> {
    String title = editTitle.getText().toString().trim();
    // ... 获取其他数据
    if (currentTodo == null) {
        // 新增任务
        currentTodo = new Todo(UUID.randomUUID().toString(), title, time, place, category, completed);
        taskDao.insertTodo(currentTodo);
    } else {
        // 编辑任务
        currentTodo.title = title;
        taskDao.insertTodo(currentTodo); // 使用 REPLACE 更新
    }
    // 同步到云端
    firestore.collection("users").document(auth.getCurrentUser().getUid()).collection("tasks").document(currentTodo.id).set(currentTodo);
});
```

###### **删除任务**:在编辑模式下，用户可以删除当前任务，操作会同步到本地数据库和云端。

```java
buttonDelete.setOnClickListener(v -> {
    taskDao.deleteTodo(currentTodo);  // 删除本地任务
    firestore.collection("users").document(auth.getCurrentUser().getUid()).collection("tasks").document(currentTodo.id).delete();  // 删除云端任务
    finish();
});
```

###### 日期和时间选择:用户可以选择日期和时间，选择后更新任务的日期字段。

```java
textDateTime.setOnClickListener(v -> {
    // 日期和时间选择器逻辑
    selectedCalendar.set(Calendar.YEAR, year1);
    selectedCalendar.set(Calendar.MONTH, month1);
    updateDateTimeText();  // 更新显示的日期时间
});
```

###### 界面布局（XML）:界面使用 `ConstraintLayout` 布局，包含 `EditText`、`Spinner`、`CheckBox` 等控件。

```xml
<EditText android:id="@+id/editTitle" android:hint="标题"/>
<TextView android:id="@+id/textDateTime" android:hint="选择日期和时间"/>
<Spinner android:id="@+id/spinnerCategoryInput"/>
<CheckBox android:id="@+id/checkCompleted" android:text="已完成"/>
<Button android:id="@+id/buttonSave" android:text="保存"/>
<Button android:id="@+id/buttonDelete" android:text="删除"/>
```



<img src="screen_shots\添加.jpg" style="zoom:25%;" />



#### （4）**AppDatabase.java**：Room 数据库主入口

`AppDatabase.java` 是整个数据库的入口类，使用 `Room` 进行本地数据存储。它继承自 `RoomDatabase`，并提供了对 `TaskDao` 的访问。

- 提供获取 `TaskDao` 的方法。
- 确保数据库实例的唯一性。

```java
@Database(entities = {Todo.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    // 获取 TaskDao 实例
    public abstract TaskDao taskDao();
    // 获取数据库实例
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "todo_db").build();
                }
            }
        }
        return instance;
    }
}
```

##### **TaskDao.java**：数据操作接口

`TaskDao.java` 定义了对 `Todo` 实体进行操作的接口，包含了增、删、改、查的方法。

- **职责**：
  - 定义查询任务、插入新任务、更新任务和删除任务的方法。
  - `@Query` 注解用于查询任务。
  - `@Insert`、`@Update`、`@Delete` 注解用于执行插入、更新和删除操作。

```java
@Dao
public interface TaskDao {
    @Query("SELECT * FROM todos")// 获取所有任务
    List<Todo> getAll();
    @Insert(onConflict = OnConflictStrategy.REPLACE)    // 插入任务
    void insertTodo(Todo todo);
    @Update
    void updateTodo(Todo todo);    // 更新任务
    @Delete
    void deleteTodo(Todo todo);// 删除任务
    @Query("DELETE FROM todos")
    void deleteAll();// 删除所有任务
}
```

##### **Todo.java**：实体类

`Todo.java` 是实体类，表示待办事项的结构。它使用 `@Entity` 注解标识，并定义了任务的各个字段（`id`、`title`、`time`、`place`、`category`、`completed`）。

- **职责**：
  - 定义任务的字段及其数据类型。
  - 使用 `@PrimaryKey` 注解标识主键（任务 ID），`@NonNull` 确保 ID 不为 `null`。

```java
@Entity(tableName = "todos")
public class Todo {
    @PrimaryKey @NonNull
    public String id;           // 任务 ID
    public String title;        // 任务标题
    public long time;           // 任务时间（毫秒时间戳）
    public String place;        // 任务地点
    public String category;     // 任务类别
    public boolean completed;   // 任务是否完成

    public Todo() {}    // 默认构造函数（Room 和 Firestore 映射需要）

    public Todo(@NonNull String id, String title, long time, String place, String category, boolean completed) {					    // 构造函数
        this.id = id;
        this.title = title;
        this.time = time;
        this.place = place;
        this.category = category;
        this.completed = completed;
        this.updatedAt = System.currentTimeMillis();
        this.deleted = false;
    }
}
```



#### （5）数据同步模块（SyncWorker）

`SyncWorker` 是一个继承自 **WorkManager** 的 `Worker` 类，主要负责后台任务数据的同步。它的职责包括从本地数据库读取任务数据，并将其上传到云端，确保数据的同步。

##### 1. **主要职责**

- **从本地数据库读取任务数据**：使用 `TaskDao` 获取本地任务数据。
- **上传任务数据到云端**：将本地数据上传至 Firebase Firestore。
- **同步云端数据到本地**：从 Firebase 获取任务数据并更新到本地数据库。
- **后台异步执行**：使用 **WorkManager** 实现异步执行任务。

##### 2. **工作流程框架**

```java
public class SyncWorker extends Worker {
    @NonNull
    @Override
    public Result doWork() {
        // 获取用户信息和数据库实例
        // 上传本地任务数据到云端
        uploadLocalTasksToCloud();
        // 从云端获取任务数据
        downloadTasksFromCloud();
        return Result.success();  // 返回成功
    }
}
```

##### 3. **周期性同步**

使用 **WorkManager** 调度周期性任务同步，确保任务数据定时同步到云端。安排周期性同步任务（每15分钟执行一次）

```java
public static void schedulePeriodicSync(Context context) {
    // 设置同步任务的条件，例如网络连接
    PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build();
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("SyncWork", ExistingPeriodicWorkPolicy.REPLACE, request);
}
```

##### 4. **手动触发同步**

允许用户手动触发同步操作：

```java
public static void triggerSyncNow(Context context) {
    // 设置同步条件
    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(constraints)
            .build();
    WorkManager.getInstance(context).enqueue(request);
}
```

##### 5. **取消定时同步**

取消已经安排的定时同步任务：

```java
public static void cancelPeriodicSync(Context context) {
    WorkManager.getInstance(context).cancelUniqueWork("SyncWork");
}
```





通过这种模块化设计，应用能将数据展示、业务逻辑、用户输入、存储处理分离开来，便于维护与拓展。

------



## 5. 遇到的问题与解决方案

#### （1）数据同步冲突

- **问题描述**：在实现数据同步时，发现云端与本地数据的同步出现冲突。尤其是当用户在不同设备或不同网络环境下进行操作时，数据可能会产生不一致，特别是在用户完成任务后，云端数据未及时更新，导致不同设备上的任务状态不同步。
- **解决方案**：为了避免数据冲突，优化`SyncWorker`的同步机制，增加手动触发和周期性同步的功能。每次任务更新后，数据会通过后台同步机制及时更新到云端，确保任务数据在不同设备上的一致性。引入了版本控制，以便在同步时自动处理冲突，优先保存最新的任务状态。

#### （2）必须使用VPN

- **问题描述**：Firebase是Google提供的服务，需要翻墙才能注册登录，非常不方便。国内无法直接访问Google服务的情况下，用户无法正常注册或登录。
- **解决方案**：考虑使用其他认证方案，如使用本地认证机制或第三方认证服务（微信、QQ登录、电话号码等）。通过这些方式，可以绕过Firebase的限制，让用户无论在国内外都能顺利注册和登录。

#### （3）用户登录信息存储安全性不足

- **问题描述**：目前，用户的登录信息（如token）存储在本地的SharedPreferences中，这种存储方式的安全性较低，容易受到攻击或泄露。
- **解决方案**：为了提升安全性，可以将用户的登录信息存储在**EncryptedSharedPreferences**中，该方法使用加密技术存储敏感信息，增加数据的安全性。



------



## 6. 项目进度与计划

#### (1) 进度

- 完成了需求分析、系统设计、数据库设计、界面设计和初步开发。
- 已实现待办事项的增删改查功能，任务筛选和同步功能也已基本实现。

#### (2)后续计划与优化方向

| 方向         | 详情                                                     |
| ------------ | -------------------------------------------------------- |
| 架构升级     | 建议使用 MVVM 架构，将 UI 逻辑与数据实现分离             |
| UI 环境新化  | 考虑使用 Jetpack Compose 以提升开发效率                  |
| 网络同步提升 | 加入 Retrofit + RESTful API 调用，并处理网络错误         |
| 登录安全性   | 使用 EncryptedSharedPreferences 或数据加密技术保护 Token |
| 多端同步     | 配合 Firebase / Supabase 实现实时同步和提醒              |
| 互动动画     | 增加更多具有交互感的互动、动画，体验更流畅               |

---


