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
    @Query("SELECT * FROM todos")
    List<Todo> getAll();

    // 查询未被软删除且不属于代办集的任务
    @Query("SELECT * FROM todos WHERE deleted = 0 AND belongsToTaskGroup = 0")
    List<Todo> getVisibleTodos();
    
    // 根据ID查询单个任务
    @Query("SELECT * FROM todos WHERE id = :taskId")
    Todo getTodoById(String taskId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTodo(Todo todo);

    @Update
    void updateTodo(Todo todo);

    @Delete
    void deleteTodo(Todo todo);

    @Query("DELETE FROM todos")
    void deleteAll();
    
    // 逻辑删除任务（软删除）
    @Query("UPDATE todos SET deleted = 1, updatedAt = :timestamp WHERE id = :taskId")
    void logicalDeleteTodo(String taskId, long timestamp);
    
    // 重载方法，使用当前时间戳
    @Query("UPDATE todos SET deleted = 1, updatedAt = :timestamp WHERE id = :taskId")
    default void logicalDeleteTodo(String taskId) {
        logicalDeleteTodo(taskId, System.currentTimeMillis());
    }
}
