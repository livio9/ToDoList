package com.example.todolist.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Todo.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    public abstract TaskDao taskDao();

    // 获取单例数据库实例
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    // 建立本地数据库 "todo_db"
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "todo_db")
                            .build();
                }
            }
        }
        return instance;
    }
}
