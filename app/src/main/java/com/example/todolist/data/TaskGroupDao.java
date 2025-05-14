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

    // Get all non-deleted task groups for a specific user
//    @Query("SELECT * FROM taskgroups WHERE deleted = 0 AND userId = :userId ORDER BY createdAt DESC")
    @Query("SELECT * FROM taskgroups WHERE deleted = 0 ORDER BY createdAt DESC")
    List<TaskGroup> getAllTaskGroupsForUser();


    // Get a specific non-deleted task group by ID, ensuring it belongs to the user
//    @Query("SELECT * FROM taskgroups WHERE id = :groupId AND deleted = 0 AND userId = :userId")
    @Query("SELECT * FROM taskgroups WHERE id = :groupId AND deleted = 0 ORDER BY createdAt DESC")
    TaskGroup getTaskGroupByIdForUser(String groupId);

    // Get sub-tasks by their IDs, ensuring they belong to the specified user
    // (Assuming sub-tasks (Todos) also have a userId field and are filtered by it)
    @Query("SELECT * FROM todos WHERE id IN (:taskIds) AND deleted = 0 AND userId = :userId ORDER BY time ASC")
    List<Todo> getSubTasksByIdsForUser(List<String> taskIds, String userId);

    // Delete all task groups for a specific user (used on logout)
    @Query("DELETE FROM taskgroups WHERE userId = :userId")
    void deleteAllTaskGroupsForUser(String userId);

    // Delete a specific task group (can be used if taskGroup object is available)
    @Delete
    void deleteTaskGroup(TaskGroup taskGroup); // Added this for convenience

    // Delete all task groups from the table (used for complete local wipe)
    @Query("DELETE FROM taskgroups")
    int deleteAllTaskGroupsUnfiltered();

    // Get all task groups unfiltered (for sync or admin purposes)
    @Query("SELECT * FROM taskgroups ORDER BY createdAt DESC")
    List<TaskGroup> getAllTaskGroupsUnfiltered();
}