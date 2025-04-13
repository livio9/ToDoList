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

    // 查询未被软删除的任务
    @Query("SELECT * FROM todos WHERE deleted = 0")
    List<Todo> getVisibleTodos();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTodo(Todo todo);

    @Update
    void updateTodo(Todo todo);

    @Delete
    void deleteTodo(Todo todo);

    @Query("DELETE FROM todos")
    void deleteAll();
}
