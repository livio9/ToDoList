package com.example.todolist.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;
import java.util.List;

@Dao
public interface TaskDao {
    // 修改：获取特定用户的所有任务
    @Query("SELECT * FROM todos WHERE userId = :userId")
    List<Todo> getAllTasksForUser(String userId);
    // 获取所有任务
    @Query("SELECT * FROM todos")
    List<Todo> getAllTasks();

    // 修改：获取特定用户所有可见（未删除且不属于任务组）的任务
    @Query("SELECT * FROM todos WHERE deleted = 0 AND belongsToTaskGroup = 0 AND userId = :userId")
    List<Todo> getVisibleTasksForUser(String userId);

    // 修改：根据ID和用户ID查询单个任务
    @Query("SELECT * FROM todos WHERE id = :taskId AND userId = :userId")
    Todo getTodoByIdForUser(String taskId, String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTodo(Todo todo);

    @Update
    void updateTodo(Todo todo);

    @Delete
    void deleteTodo(Todo todo);

    // 修改：删除特定用户的所有任务（用于登出时清理）
    @Query("DELETE FROM todos WHERE userId = :userId")
    void deleteAllTasksForUser(String userId);

    // 保持一个通用的 deleteAll (如果确实需要在某些场景下清除所有数据)
    @Query("DELETE FROM todos")
    void deleteAll();

    // 修改：软删除特定用户的任务
    @Query("UPDATE todos SET deleted = 1, updatedAt = :timestamp WHERE id = :taskId AND userId = :userId")
    void logicalDeleteTodoForUser(String taskId, String userId, long timestamp);
    
    // 重载方法，使用当前时间戳
    @Query("UPDATE todos SET deleted = 1, updatedAt = :timestamp WHERE id = :taskId AND userId = :userId")
    default void logicalDeleteTodoForUser(String taskId, String userId) {
        logicalDeleteTodoForUser(taskId, userId, System.currentTimeMillis());
    }
    @Query("SELECT * FROM todos")
    List<Todo> getAllUnfiltered(); // 用于同步等内部操作，不直接展示给UI
}
