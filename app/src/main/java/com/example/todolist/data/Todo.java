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
    public String priority;    // 任务优先级：高、中、低
    public Boolean pomodoroEnabled; // 是否启用番茄时钟
    public int pomodoroMinutes = 0; // 该任务的总专注分钟数
    public int pomodoroCompletedCount = 0; // 该任务已完成的番茄钟次数

    public long updatedAt;     // 上次修改时间戳
    public boolean deleted;    // 是否软删除
    public boolean belongsToTaskGroup = false; // 是否属于代办集，默认为false
    public int points = 0;     // 任务积分，完成任务时奖励

    @NonNull
    public String userId; // 新增：用于关联 ParseUser的objectId

    // 默认构造函数（Room 和 Firestore 映射需要）
    public Todo() { this.userId = ""; }

    // 构造函数：新增任务时，默认更新时间为当前时间，且删除标记为 false
    @Ignore
    public Todo(@NonNull String uuid, String title, long time, String place, String category, boolean completed, @NonNull String userId) {
        this.uuid = uuid;
        this.title = title;
        this.time = time;
        this.place = place;
        this.category = category;
        this.completed = completed;
        this.updatedAt = System.currentTimeMillis();
        this.deleted = false;
        this.belongsToTaskGroup = false;
        this.priority = "中";  // 默认优先级为中
        this.pomodoroEnabled = false; // 默认不启用番茄时钟
        this.userId = userId; // 设置 userId
        this.points = calculatePoints(); // 根据优先级等计算积分
    }
    
    // 计算任务完成后可获得的积分
    public int calculatePoints() {
        // 基础分值
        int basePoints = 10;
        
        // 根据优先级调整分值
        if (priority != null) {
            switch (priority) {
                case "高":
                    basePoints += 10;
                    break;
                case "中":
                    basePoints += 5;
                    break;
                case "低":
                    basePoints += 2;
                    break;
            }
        }
        
        // 使用番茄钟的额外奖励
        if (pomodoroEnabled != null && pomodoroEnabled) {
            basePoints += 5;
        }
        
        return basePoints;
    }
}
