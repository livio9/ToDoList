package com.example.todolist.data;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Todo.class, TaskGroup.class}, version = 3, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    private static final String TAG = "AppDatabase";
    public abstract TaskDao taskDao();
    public abstract TaskGroupDao taskGroupDao();

    // 从版本2到版本3的迁移
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 添加新的belongsToTaskGroup列到todos表
            database.execSQL("ALTER TABLE todos ADD COLUMN belongsToTaskGroup INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 获取单例数据库实例
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    try {
                        Log.d(TAG, "创建数据库实例");
                        // 建立本地数据库 "todo_db"
                        instance = Room.databaseBuilder(context.getApplicationContext(),
                                        AppDatabase.class, "todo_db")
                                .addMigrations(MIGRATION_2_3) // 添加2到3的迁移策略
                                .build();
                        Log.d(TAG, "数据库创建成功");
                    } catch (Exception e) {
                        Log.e(TAG, "数据库创建失败", e);
                        
                        try {
                            // 如果普通数据库创建失败，尝试使用内存数据库
                            Log.d(TAG, "尝试创建内存数据库");
                            instance = Room.inMemoryDatabaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class
                            ).build();
                            Log.d(TAG, "内存数据库创建成功");
                        } catch (Exception e2) {
                            Log.e(TAG, "内存数据库创建也失败", e2);
                            // 两种数据库都失败，返回null
                            instance = null;
                        }
                    }
                }
            }
        }
        return instance;
    }
}
