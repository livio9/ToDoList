package com.example.todolist.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;
import java.io.Serializable;

@Entity(tableName = "todos")
public class Todo implements Serializable {
    @PrimaryKey
    @NonNull
    public String uuid;

    public String title;
    public long time;          // 任务时间（毫秒时间戳）
    public String place;
    public String category;
    public boolean completed;

    public long clientUpdatedAt;     // 上次修改时间戳
    public boolean deleted;    // 是否软删除
    public boolean belongsToTaskGroup = false; // 是否属于代办集，默认为false

    // 默认构造函数（Room 和 Firestore 映射需要）
    public Todo() { }

    // 构造函数：新增任务时，默认更新时间为当前时间，且删除标记为 false
    @Ignore
    public Todo(@NonNull String uuid, String title, long time, String place, String category, boolean completed) {
        this.uuid = uuid;
        this.title = title;
        this.time = time;
        this.place = place;
        this.category = category;
        this.completed = completed;
        this.clientUpdatedAt = System.currentTimeMillis();
        this.deleted = false;
        this.belongsToTaskGroup = false;
    }
}
