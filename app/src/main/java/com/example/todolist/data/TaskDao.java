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
    @Query("SELECT * FROM todos WHERE deleted = 0 AND belongsToTaskGroup = 0 ORDER BY time ASC")
    List<Todo> getNonGroupTodos();

    @Query("SELECT * FROM todos WHERE deleted = 0 ORDER BY time ASC")
    List<Todo> getAll();

    // 查询未被软删除且不属于代办集的任务
    @Query("SELECT * FROM todos WHERE deleted = 0 AND belongsToTaskGroup = 0")
    List<Todo> getVisibleTodos();
    
    // 根据ID查询单个任务
    @Query("SELECT * FROM todos WHERE id = :id")
    Todo getTodoById(String id);

    @Query("SELECT * FROM todos WHERE id IN (:ids) AND deleted = 0")
    List<Todo> getTodosByIds(List<String> ids);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTodo(Todo todo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Todo> todos);

    @Update
    void updateTodo(Todo todo);

    @Delete
    void delete(Todo todo);

    // 获取指定类别的任务
    @Query("SELECT * FROM todos WHERE category = :category AND deleted = 0 AND belongsToTaskGroup = 0 ORDER BY time ASC")
    List<Todo> getTodosByCategory(String category);
    
    // 获取已完成的任务
    @Query("SELECT * FROM todos WHERE completed = 1 AND deleted = 0 ORDER BY time DESC")
    List<Todo> getCompletedTodos();
    
    // 获取未完成的任务
    @Query("SELECT * FROM todos WHERE completed = 0 AND deleted = 0 ORDER BY time ASC")
    List<Todo> getIncompleteTodos();
    
    // 获取指定类别且未完成的任务
    @Query("SELECT * FROM todos WHERE category = :category AND completed = 0 AND deleted = 0 ORDER BY time ASC")
    List<Todo> getIncompleteTodosByCategory(String category);

    // 获取某个时间范围内的任务
    @Query("SELECT * FROM todos WHERE time BETWEEN :startTime AND :endTime AND deleted = 0 ORDER BY time ASC")
    List<Todo> getTodosBetweenTime(long startTime, long endTime);
    
    // 标记一个任务为已删除
    @Query("UPDATE todos SET deleted = 1, updatedAt = :timestamp WHERE id = :todoId")
    void markAsDeleted(String todoId, long timestamp);
    
    // 查找包含关键词的任务
    @Query("SELECT * FROM todos WHERE (title LIKE '%' || :keyword || '%' OR place LIKE '%' || :keyword || '%') AND deleted = 0 ORDER BY time ASC")
    List<Todo> searchTodos(String keyword);
    
    // 根据优先级获取任务
    @Query("SELECT * FROM todos WHERE priority = :priority AND deleted = 0 ORDER BY time ASC")
    List<Todo> getTodosByPriority(String priority);
    
    // 获取已启用番茄钟的任务
    @Query("SELECT * FROM todos WHERE pomodoroEnabled = 1 AND completed = 0 AND deleted = 0 ORDER BY time ASC")
    List<Todo> getPomodoroEnabledTodos();

    // 清除所有任务（用于清理数据库）
    @Query("DELETE FROM todos")
    void deleteAllTodos();
}
