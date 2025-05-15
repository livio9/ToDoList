package com.example.todolist.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TodoDao {
    @Query("SELECT * FROM todos")
    List<Todo> getAllTodos();

    @Query("SELECT * FROM todos WHERE deleted = 0")
    List<Todo> getNonDeletedTodos();

    @Query("SELECT * FROM todos WHERE uuid = :uuid")
    Todo getTodoByUuid(String uuid);

    @Query("SELECT * FROM todos WHERE objectId = :objectId")
    Todo getTodoByObjectId(String objectId);

    @Query("SELECT * FROM todos WHERE userId = :userId")
    List<Todo> getTodosByUserId(String userId);

    @Query("SELECT * FROM todos WHERE category = :category")
    List<Todo> getTodosByCategory(String category);

    @Query("SELECT * FROM todos WHERE completed = :completed")
    List<Todo> getTodosByCompletionStatus(boolean completed);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Todo todo);

    @Update
    void update(Todo todo);

    @Delete
    void delete(Todo todo);

    @Query("DELETE FROM todos WHERE uuid = :uuid")
    void deleteByUuid(String uuid);

    @Query("UPDATE todos SET deleted = 1 WHERE uuid = :uuid")
    void markAsDeleted(String uuid);
} 