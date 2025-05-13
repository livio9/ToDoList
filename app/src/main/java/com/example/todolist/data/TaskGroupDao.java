package com.example.todolist.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskGroupDao {
    @Query("SELECT * FROM taskgroups WHERE deleted = 0 ORDER BY createdAt DESC")
    List<TaskGroup> getAllTaskGroups();

    @Query("SELECT * FROM taskgroups WHERE uuid = :uuid")
    TaskGroup getTaskGroupById(String uuid);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTaskGroup(TaskGroup taskGroup);

    @Update
    void updateTaskGroup(TaskGroup taskGroup);

    @Delete
    void delete(TaskGroup taskGroup);

    @Query("UPDATE taskgroups SET deleted = 1 WHERE uuid = :uuid")
    void markAsDeleted(String uuid);
    
    // 根据分类获取待办集
    @Query("SELECT * FROM taskgroups WHERE category = :category AND deleted = 0 ORDER BY createdAt DESC")
    List<TaskGroup> getTaskGroupsByCategory(String category);
    
    // 获取最近创建的待办集
    @Query("SELECT * FROM taskgroups WHERE deleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    List<TaskGroup> getRecentTaskGroups(int limit);
    
    // 根据完成度过滤待办集（需要配合程序逻辑计算完成度）
    @Query("SELECT * FROM taskgroups WHERE deleted = 0")
    List<TaskGroup> getAllTaskGroupsForFilteringByCompletion();
    
    // 清除所有待办集（用于清理数据库）
    @Query("DELETE FROM taskgroups")
    void deleteAllTaskGroups();
} 