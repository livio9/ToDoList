package com.example.todolist.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 待办集数据模型
 * 表示一个大任务，包含多个子任务
 */
@Entity(tableName = "taskgroups")
@TypeConverters(Converters.class)
public class TaskGroup implements Serializable {
    @PrimaryKey
    @NonNull
    public String uuid;            // 唯一ID
    public String title;         // 待办集标题
    public String category;      // 分类
    public int estimatedDays;    // 预计完成天数
    public long createdAt;       // 创建时间
    public long updatedAt;       // 更新时间
    public List<String> subTaskIds; // 子任务ID列表
    public boolean deleted;      // 是否已删除
    public String objectId = null; // 新增：Parse云端objectId
//    public String ownerId;       // 创建者objectId

    @NonNull
    public String userId; // 新增：用于关联 ParseUser的objectId

    // 进度统计字段 - 不存储在数据库中
    @Ignore
    public int completedCount;   // 已完成子任务数量
    @Ignore
    public int totalCount;       // 总子任务数量

    // 无参数构造函数，Room使用这个构造函数创建对象
    public TaskGroup() {
        this.uuid = "";
        subTaskIds = new ArrayList<>();
        this.deleted = false;
        this.userId = "";
//        this.ownerId = com.parse.ParseUser.getCurrentUser() != null ? com.parse.ParseUser.getCurrentUser().getObjectId() : null;
        this.completedCount = 0;
        this.totalCount = 0;
    }

    // 使用@Ignore标记，告诉Room不要使用这个构造函数
    @Ignore
    public TaskGroup(@NonNull String uuid, String title, String category, int estimatedDays, @NonNull String userId) {
        this.uuid = uuid;
        this.title = title;
        this.category = category;
        this.estimatedDays = estimatedDays;
        this.createdAt = System.currentTimeMillis();
        this.subTaskIds = new ArrayList<>();
        this.deleted = false;
        this.userId = userId;
//        this.ownerId = com.parse.ParseUser.getCurrentUser() != null ? com.parse.ParseUser.getCurrentUser().getObjectId() : null;
        this.completedCount = 0;
        this.totalCount = 0;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    public void addSubTask(String taskId) {
        if (subTaskIds == null) {
            subTaskIds = new ArrayList<>();
        }
        if (!subTaskIds.contains(taskId)) {
            subTaskIds.add(taskId);
            touch(); // 修改了子任务列表，更新时间戳
        }
    }

    public void removeSubTask(String taskId) {
        if (subTaskIds != null) {
            if (subTaskIds.remove(taskId)) {
                touch(); // 修改了子任务列表，更新时间戳
            }
        }
    }

} 