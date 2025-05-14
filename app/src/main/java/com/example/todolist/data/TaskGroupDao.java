package com.example.todolist.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TaskGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTaskGroup(TaskGroup taskGroup); // TaskGroup object should now contain userId

    // 获取特定用户的所有未删除任务组
    @Query("SELECT * FROM taskgroups WHERE deleted = 0 ORDER BY createdAt DESC")
    List<TaskGroup> getAllTaskGroupsForUser();

    // 根据uuid和用户ID查询单个未删除任务组
    @Query("SELECT * FROM taskgroups WHERE uuid = :groupId AND deleted = 0")
    TaskGroup getTaskGroupByIdForUser(String groupId);

    // 根据子任务uuid列表和用户ID获取子任务
    @Query("SELECT * FROM todos WHERE uuid IN (:taskIds) AND deleted = 0 ORDER BY time ASC")
    List<Todo> getSubTasksByIdsForUser(List<String> taskIds);

    // 删除特定用户的所有任务组（用于登出时清理）
    @Query("DELETE FROM taskgroups")
    void deleteAllTaskGroupsForUser();

    // 删除单个任务组
    @Delete
    void deleteTaskGroup(TaskGroup taskGroup);

    // 删除所有任务组（用于本地清空）
    @Query("DELETE FROM taskgroups WHERE deleted = 0")
    int deleteAllTaskGroupsUnfiltered();

    // 获取所有任务组（不区分用户，内部同步用）
    @Query("SELECT * FROM taskgroups ORDER BY createdAt DESC")
    List<TaskGroup> getAllTaskGroupsUnfiltered();

    // 获取所有任务组（包括已软删除）
    @Query("SELECT * FROM taskgroups")
    List<TaskGroup> getAllTaskGroupsIncludingDeleted();
}