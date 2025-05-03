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
    public List<String> subTaskIds; // 子任务ID列表

    // 无参数构造函数，Room使用这个构造函数创建对象
    public TaskGroup() {
        this.uuid = "";
        subTaskIds = new ArrayList<>();
    }

    // 使用@Ignore标记，告诉Room不要使用这个构造函数
    @Ignore
    public TaskGroup(@NonNull String uuid, String title, String category, int estimatedDays) {
        this.uuid = uuid;
        this.title = title;
        this.category = category;
        this.estimatedDays = estimatedDays;
        this.createdAt = System.currentTimeMillis();
        this.subTaskIds = new ArrayList<>();
    }

    public void addSubTask(String taskId) {
        if (!subTaskIds.contains(taskId)) {
            subTaskIds.add(taskId);
        }
    }
} 