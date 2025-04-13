package com.example.todolist.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import java.io.Serializable;

@Entity(tableName = "todos")
public class Todo implements Serializable {
    @PrimaryKey
    @NonNull
    public String id;

    public String title;
    public long time;          // 任务时间（毫秒时间戳）
    public String place;
    public String category;
    public boolean completed;

    public long updatedAt;     // 上次修改时间戳
    public boolean deleted;    // 是否软删除

    // 默认构造函数（Room 和 Firestore 映射需要）
    public Todo() { }

    // 构造函数：新增任务时，默认更新时间为当前时间，且删除标记为 false
    public Todo(@NonNull String id, String title, long time, String place, String category, boolean completed) {
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
