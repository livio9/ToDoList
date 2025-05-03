# TodoList应用详细说明文档

## 项目概述

本应用是一个功能完善的待办事项管理应用，具有现代化UI设计、本地存储和云同步功能。应用采用Material Design设计风格，支持多种任务类别、任务分组、数据统计和AI辅助任务分解等高级功能。

## 技术栈

- **前端**: Java + Android SDK + Material Components
- **本地存储**: Room（SQLite抽象层）
- **云同步**: Firebase Firestore
- **后台任务**: WorkManager
- **AI功能**: 接入OpenRouter API

## 应用架构

应用采用模块化设计，主要分为以下几个模块：

```
com.example.todolist/
├── ui/             # UI界面组件
├── data/           # 数据模型和数据访问
├── sync/           # 数据同步服务
├── auth/           # 用户认证
└── ai/             # AI助手功能
```

## 颜色系统

应用使用了统一的配色方案，定义在`colors.xml`中：

```xml
<!-- 应用主题颜色 - 现代蓝紫色调 -->
<color name="primary">#5C6BC0</color>      <!-- 主要颜色：明亮靛蓝色 -->
<color name="primary_dark">#3F51B5</color> <!-- 深一点的主色调 -->
<color name="primary_light">#E8EAF6</color> <!-- 浅一点的主色调 -->
<color name="accent">#FF4081</color>       <!-- 强调色：亮粉色 -->
<color name="accent_light">#FF80AB</color> <!-- 浅一点的强调色 -->
<color name="accent_dark">#F50057</color>  <!-- 深一点的强调色 -->

<!-- 类别颜色 - 更加柔和的色调 -->
<color name="category_work">#26A69A</color>     <!-- 工作：柔和绿色 -->
<color name="category_personal">#7986CB</color> <!-- 个人：柔和紫色 -->
<color name="category_study">#4FC3F7</color>    <!-- 学习：柔和蓝色 -->
<color name="category_health">#81C784</color>   <!-- 健康：浅绿色 -->
<color name="category_other">#FFB74D</color>    <!-- 其他：柔和橙色 -->
```

## 详细页面说明

### 1. 主页面 (MainActivity)

**文件**: `app/src/main/java/com/example/todolist/ui/MainActivity.java`
**布局**: `app/src/main/res/layout/activity_main.xml`

**主要功能**:
- 应用的入口点，处理初始化和导航
- 管理底部导航栏和Fragment切换
- 初始化数据库连接和Firebase服务
- 触发数据同步

**主要UI组件**:
- `BottomNavigationView`（底部导航栏）
- `FragmentContainer`（Fragment容器）
- `Toolbar`（顶部工具栏）

**核心代码**:
```java
private void setupBottomNavigation() {
    bottomNavigation = findViewById(R.id.bottomNavigation);
    bottomNavigation.setOnItemSelectedListener(item -> {
        int itemId = item.getItemId();
        
        if (itemId == R.id.navigation_tasks) {
            switchFragment(tasksFragment);
            setTitle("待办事项");
            return true;
        } else if (itemId == R.id.navigation_task_groups) {
            switchFragment(taskGroupsFragment);
            setTitle("待办集");
            return true;
        } else if (itemId == R.id.navigation_statistics) {
            switchFragment(statisticsFragment);
            setTitle("数据统计");
            return true;
        } else if (itemId == R.id.navigation_profile) {
            switchFragment(profileFragment);
            setTitle("个人中心");
            return true;
        }
        
        return false;
    });
}
```

### 2. 任务列表页面 (TasksFragment)

**文件**: `app/src/main/java/com/example/todolist/ui/TasksFragment.java`
**布局**: `app/src/main/res/layout/fragment_tasks.xml`

**主要功能**:
- 显示所有非任务组内的待办事项
- 提供任务过滤功能（按时间、类别、状态）
- 添加、编辑和删除任务
- 任务空视图提示

**主要UI组件**:
- `RecyclerView`（任务列表）
- `FloatingActionButton`（添加按钮）
- `Chip` 组（过滤器）
- 自定义空视图

**布局结构**:
```xml
<CoordinatorLayout>
    <LinearLayout>
        <HorizontalScrollView> <!-- 过滤器区域 -->
            <ChipGroup/> <!-- 任务过滤选项 -->
        </HorizontalScrollView>
        
        <NestedScrollView>
            <RecyclerView/> <!-- 任务列表 -->
            <LinearLayout/> <!-- 空视图 -->
        </NestedScrollView>
    </LinearLayout>
    
    <FloatingActionButton/> <!-- 添加按钮 -->
</CoordinatorLayout>
```

**过滤功能实现**:
```java
private void applyFiltersAndRefresh() {
    String timeFilter = spinnerTime.getText().toString();
    String categoryFilter = spinnerCategory.getText().toString();
    String statusFilter = spinnerStatus.getText().toString();
    
    List<Todo> filteredList = new ArrayList<>(allTasks);
    Calendar now = Calendar.getInstance();
    
    // 应用时间过滤
    if (!timeFilter.equals("全部时间")) {
        if (timeFilter.equals("今天")) {
            // 过滤今天的任务
            Calendar todayStart = Calendar.getInstance();
            todayStart.set(Calendar.HOUR_OF_DAY, 0);
            todayStart.set(Calendar.MINUTE, 0);
            todayStart.set(Calendar.SECOND, 0);
            
            Calendar todayEnd = Calendar.getInstance();
            todayEnd.set(Calendar.HOUR_OF_DAY, 23);
            todayEnd.set(Calendar.MINUTE, 59);
            todayEnd.set(Calendar.SECOND, 59);
            
            filteredList = filteredList.stream()
                    .filter(todo -> todo.time >= todayStart.getTimeInMillis() && 
                                   todo.time <= todayEnd.getTimeInMillis())
                    .collect(Collectors.toList());
        } else if (timeFilter.equals("本周内")) {
            // 过滤本周任务
            // ...
        } else if (timeFilter.equals("已过期")) {
            // 过滤已过期任务
            // ...
        }
    }
    
    // 应用类别和状态过滤
    // ...
    
    // 更新UI显示
    adapter.updateData(filteredList);
    updateEmptyViewVisibility(filteredList);
}
```

### 3. 任务项UI (item_todo.xml)

**文件**: `app/src/main/res/layout/item_todo.xml`

**主要功能**:
- 显示单个任务的卡片视图
- 展示任务标题、时间、地点和类别
- 提供勾选完成功能

**布局结构**:
```xml
<CardView>
    <LinearLayout> <!-- 垂直方向主布局 -->
        <LinearLayout> <!-- 任务标题和勾选区 -->
            <CheckBox/>
            <TextView/> <!-- 标题 -->
            <ImageView/> <!-- 选项按钮 -->
        </LinearLayout>
        
        <LinearLayout> <!-- 任务详情区 -->
            <LinearLayout> <!-- 左侧时间和地点信息 -->
                <LinearLayout> <!-- 时间行 -->
                    <ImageView/>
                    <TextView/>
                </LinearLayout>
                
                <LinearLayout> <!-- 地点行 -->
                    <ImageView/>
                    <TextView/>
                </LinearLayout>
            </LinearLayout>
            
            <TextView/> <!-- 右侧类别标签 -->
        </LinearLayout>
    </LinearLayout>
</CardView>
```

**样式特点**:
- 使用阴影和圆角增强立体感
- 图标和文字配色统一
- 根据任务状态变更文字样式（已完成添加删除线和灰色）
- 类别使用彩色标签区分

### 4. 任务适配器 (TaskAdapter)

**文件**: `app/src/main/java/com/example/todolist/ui/TaskAdapter.java`

**主要功能**:
- 将任务数据绑定到RecyclerView
- 处理任务完成状态变更
- 管理任务项点击和长按事件

**主要组件**:
- `ViewHolder`内部类: 保存任务项视图引用
- `onBindViewHolder`: 绑定数据到视图
- 各种事件监听器

**关键代码**:
```java
@Override
public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    Todo todo = taskList.get(position);
    
    // 设置任务标题
    holder.textTitle.setText(todo.title);
    
    // 根据完成状态设置样式
    if (todo.completed) {
        holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_completed));
    } else {
        // 判断是否过期
        // ...
    }
    
    // 设置时间地点信息
    // ...
    
    // 设置类别标签
    if (todo.category != null && !todo.category.isEmpty()) {
        holder.textCategory.setVisibility(View.VISIBLE);
        handleCategoryChip(holder.textCategory, todo.category);
    } else {
        holder.textCategory.setVisibility(View.GONE);
    }
    
    // 设置完成状态勾选框
    holder.checkCompleted.setChecked(todo.completed);
    
    // 处理点击事件
    // ...
}
```

### 5. 添加/编辑任务页面 (AddEditTaskActivity)

**文件**: `app/src/main/java/com/example/todolist/ui/AddEditTaskActivity.java`
**布局**: `app/src/main/res/layout/activity_add_edit_task.xml`

**主要功能**:
- 添加新任务或编辑现有任务
- 设置任务标题、时间、地点和类别
- 提供AI辅助分解任务功能

**主要UI组件**:
- `TextInputLayout`和`EditText`（输入字段）
- 日期和时间选择器
- 类别选择下拉菜单
- 保存和AI分解按钮

**布局结构**:
```xml
<CoordinatorLayout>
    <AppBarLayout>
        <Toolbar/>
    </AppBarLayout>
    
    <ScrollView>
        <LinearLayout>
            <!-- 标题输入框 -->
            <TextInputLayout>
                <EditText/>
            </TextInputLayout>
            
            <!-- 日期时间选择 -->
            <LinearLayout>
                <TextView/> <!-- 日期显示 -->
                <TextView/> <!-- 时间显示 -->
            </LinearLayout>
            
            <!-- 地点输入框 -->
            <TextInputLayout>
                <EditText/>
            </TextInputLayout>
            
            <!-- 类别选择 -->
            <TextInputLayout>
                <AutoCompleteTextView/>
            </TextInputLayout>
            
            <!-- 按钮区域 -->
            <Button/> <!-- 保存按钮 -->
            <Button/> <!-- AI分解按钮 -->
        </LinearLayout>
    </ScrollView>
</CoordinatorLayout>
```

### 6. 任务组页面 (TaskGroupsFragment)

**文件**: `app/src/main/java/com/example/todolist/ui/TaskGroupsFragment.java`
**布局**: `app/src/main/res/layout/fragment_task_groups.xml`

**主要功能**:
- 显示和管理任务组（相关任务的集合）
- 创建新的任务组
- 查看任务组详情

**主要UI组件**:
- `RecyclerView`（任务组列表）
- `FloatingActionButton`（添加按钮）
- 空视图提示

### 7. 统计页面 (StatisticsFragment)

**文件**: `app/src/main/java/com/example/todolist/ui/StatisticsFragment.java`
**布局**: `app/src/main/res/layout/fragment_statistics.xml`

**主要功能**:
- 显示任务完成情况的统计数据
- 按类别和时间段显示统计图表
- 呈现任务完成率和效率分析

**主要UI组件**:
- 统计卡片（显示完成率）
- 饼图（显示任务类别分布）
- 条形图（显示时间分布）

### 8. 个人中心页面 (ProfileFragment)

**文件**: `app/src/main/java/com/example/todolist/ui/ProfileFragment.java`
**布局**: `app/src/main/res/layout/fragment_profile.xml`

**主要功能**:
- 用户信息管理
- 应用设置
- 数据同步选项
- 登出功能

**主要UI组件**:
- 用户信息卡片
- 设置选项列表
- 同步状态显示
- 登出按钮

### 9. 登录页面 (LoginActivity)

**文件**: `app/src/main/java/com/example/todolist/auth/LoginActivity.java`
**布局**: `app/src/main/res/layout/activity_login.xml`

**主要功能**:
- 用户登录和注册
- 身份验证
- 重置密码选项

**主要UI组件**:
- 用户名/邮箱输入框
- 密码输入框
- 登录和注册按钮
- 社交媒体登录选项

## 数据模型

### Todo 类

**文件**: `app/src/main/java/com/example/todolist/data/Todo.java`

**主要属性**:
```java
@Entity(tableName = "todos")
public class Todo implements Serializable {
    @PrimaryKey
    @NonNull
    public String id;           // 唯一标识符

    public String title;        // 任务标题
    public long time;           // 任务时间戳
    public String place;        // 任务地点
    public String category;     // 任务类别
    public boolean completed;   // 完成状态

    public long updatedAt;      // 最后更新时间
    public boolean deleted;     // 软删除标记
    public boolean belongsToTaskGroup = false; // 是否属于任务组
}
```

### TaskGroup 类

**文件**: `app/src/main/java/com/example/todolist/data/TaskGroup.java`

**主要属性**:
```java
@Entity(tableName = "task_groups")
public class TaskGroup {
    @PrimaryKey
    @NonNull
    public String id;           // 唯一标识符

    public String title;        // 任务组标题
    public String description;  // 任务组描述
    public String category;     // 任务组类别
    public long createdAt;      // 创建时间
    public long updatedAt;      // 更新时间
    public boolean deleted;     // 软删除标记
}
```

## 云同步功能

**文件**: `app/src/main/java/com/example/todolist/sync/SyncWorker.java`

**主要功能**:
- 定期与Firebase同步本地数据
- 处理数据冲突
- 后台同步服务管理

**关键代码**:
```java
@Override
public Result doWork() {
    // 获取Firebase用户
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
        return Result.success();
    }
    
    // 获取本地和云端数据
    List<Todo> localTasks = taskDao.getAll();
    QuerySnapshot snapshot = Tasks.await(cloudTasksRef.get());
    List<DocumentSnapshot> cloudDocs = snapshot.getDocuments();
    
    // 合并数据
    for (String id : allIds) {
        Todo localTodo = localMap.get(id);
        Todo cloudTodo = cloudMap.get(id);
        
        // 比较更新时间，选择较新的版本
        if (localTodo != null && cloudTodo != null) {
            if (localTodo.updatedAt >= cloudTodo.updatedAt) {
                // 本地更新更晚，上传到云端
                cloudTasksRef.document(localTodo.id).set(localTodo);
            } else {
                // 云端更新更晚，更新本地
                taskDao.insertTodo(cloudTodo);
            }
        } else if (localTodo != null) {
            // 仅本地有，上传到云端
            cloudTasksRef.document(localTodo.id).set(localTodo);
        } else if (cloudTodo != null) {
            // 仅云端有，下载到本地
            taskDao.insertTodo(cloudTodo);
        }
    }
    
    return Result.success();
}
```

## AI功能

**文件**: `app/src/main/java/com/example/todolist/ai/TaskDecomposer.java`

**主要功能**:
- 通过AI将大任务分解为小任务
- 使用外部API生成任务分解建议
- 处理AI返回的JSON数据

**核心代码**:
```java
public static DecompositionResult decomposeTask(String mainTaskTitle) throws IOException, JSONException {
    // 构建提示模板
    String prompt = String.format(PROMPT_TEMPLATE, mainTaskTitle);
    
    // 调用AI API
    JSONObject requestBody = new JSONObject();
    requestBody.put("model", MODEL);
    // 设置API参数
    // ...
    
    // 发送请求
    Response response = CLIENT.newCall(request).execute();
    
    // 解析响应
    JSONObject taskJson = new JSONObject(jsonContent);
    DecompositionResult result = new DecompositionResult(
            taskJson.getString("mainTask"),
            taskJson.getString("category"),
            taskJson.getInt("estimatedDays")
    );
    
    // 解析子任务
    JSONArray subTasksJson = taskJson.getJSONArray("subTasks");
    for (int i = 0; i < subTasksJson.length(); i++) {
        JSONObject subTaskJson = subTasksJson.getJSONObject(i);
        SubTask subTask = new SubTask(
                subTaskJson.getString("title"),
                subTaskJson.getString("description"),
                subTaskJson.getDouble("estimatedHours")
        );
        result.addSubTask(subTask);
    }
    
    return result;
}
```

## 视觉设计元素

### 图标资源
- `ic_category_work.xml`: 工作类别图标
- `ic_category_personal.xml`: 个人类别图标
- `ic_category_study.xml`: 学习类别图标
- `ic_category_health.xml`: 健康类别图标
- `ic_category_other.xml`: 其他类别图标
- `ic_task_completed.xml`: 任务完成图标
- `ic_empty_task.xml`: 空任务提示图标

### 背景资源
- `task_card_background.xml`: 任务卡片背景
- `bg_category_chip.xml`: 类别标签背景
- `bg_rounded_hint.xml`: 提示信息背景
- `bg_button_primary.xml`: 主要按钮背景
- `bg_button_accent.xml`: 强调按钮背景
- `bg_statistics_card.xml`: 统计卡片背景
- `empty_view_background.xml`: 空视图背景

### 样式资源
应用定义了一系列统一的样式，包括：
- 按钮样式
- 文本输入框样式
- 卡片样式
- 标签样式
- 底部导航栏样式
- 工具栏样式
- 悬浮按钮样式

## 总结

本应用是一个功能完整的待办事项管理工具，结合了现代UI设计、本地数据存储、云同步和AI辅助功能。应用采用了模块化架构设计，将UI、数据处理、同步和AI功能分离，使代码更加清晰和易于维护。

通过精美的UI设计、统一的配色方案和丰富的视觉元素，应用提供了流畅的用户体验。Room数据库确保了本地数据的高效存储和访问，而Firebase集成则提供了跨设备的数据同步能力。AI辅助功能则为用户提供了智能的任务管理体验。 