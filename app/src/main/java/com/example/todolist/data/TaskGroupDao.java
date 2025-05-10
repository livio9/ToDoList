package com.example.todolist.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TaskGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTaskGroup(TaskGroup taskGroup);
    
    @Query("SELECT * FROM taskgroups WHERE deleted = 0 ORDER BY createdAt DESC")
    List<TaskGroup> getAllTaskGroups();
    
    @Query("SELECT * FROM taskgroups WHERE id = :groupId AND deleted = 0")
    TaskGroup getTaskGroupById(String groupId);
    
    @Query("SELECT * FROM todos WHERE id IN (:taskIds) AND deleted = 0 ORDER BY time ASC")
    List<Todo> getSubTasksByIds(List<String> taskIds);
} 