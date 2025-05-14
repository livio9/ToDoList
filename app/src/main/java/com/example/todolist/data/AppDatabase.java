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

@Database(entities = {Todo.class, TaskGroup.class}, version = 10, exportSchema = false)
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
    
    // 从版本3到版本4的迁移
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 添加新的points列到todos表
            database.execSQL("ALTER TABLE todos ADD COLUMN points INTEGER NOT NULL DEFAULT 0");
        }
    };
    
    // 从版本4到版本5的迁移
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 添加pomodoroEnabled和priority字段
            try {
                // 有些版本的SQLite可能不支持ALTER TABLE ADD COLUMN IF NOT EXISTS
                // 因此使用try-catch来处理可能已存在的列
                
                try {
                    database.execSQL("ALTER TABLE todos ADD COLUMN pomodoroEnabled INTEGER DEFAULT NULL");
                } catch (Exception e) {
                    Log.w(TAG, "添加pomodoroEnabled列失败，可能已存在: " + e.getMessage());
                }
                
                try {
                    database.execSQL("ALTER TABLE todos ADD COLUMN priority TEXT DEFAULT '中'");
                } catch (Exception e) {
                    Log.w(TAG, "添加priority列失败，可能已存在: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "迁移4到5失败", e);
            }
        }
    };

    // 从版本5到版本6的迁移
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                // 添加新的专注时间统计字段
                try {
                    database.execSQL("ALTER TABLE todos ADD COLUMN pomodoroMinutes INTEGER NOT NULL DEFAULT 0");
                } catch (Exception e) {
                    Log.w(TAG, "添加pomodoroMinutes列失败，可能已存在: " + e.getMessage());
                }
                
                try {
                    database.execSQL("ALTER TABLE todos ADD COLUMN pomodoroCompletedCount INTEGER NOT NULL DEFAULT 0");
                } catch (Exception e) {
                    Log.w(TAG, "添加pomodoroCompletedCount列失败，可能已存在: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "迁移5到6失败", e);
            }
        }
    };
    
    // 从版本6到版本7的迁移
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                // 添加TaskGroup的deleted字段
                try {
                    database.execSQL("ALTER TABLE taskgroups ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
                } catch (Exception e) {
                    Log.w(TAG, "添加TaskGroup的deleted列失败，可能已存在: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "迁移6到7失败", e);
            }
        }
    };

    // 从版本7到版本8的迁移
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE taskgroups ADD COLUMN ownerId TEXT");
            } catch (Exception e) {
                Log.w(TAG, "添加ownerId列失败，可能已存在: " + e.getMessage());
            }
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.d(TAG, "Migrating database from version 8 to 9");
            try {
                // 为 todos 表添加 userId 列
                database.execSQL("ALTER TABLE todos ADD COLUMN userId TEXT NOT NULL DEFAULT ''");
                Log.d(TAG, "Added userId column to todos table");
            } catch (Exception e) {
                Log.w(TAG, "Adding userId column to todos failed, it might already exist: " + e.getMessage());
            }
            try {
                // 为 taskgroups 表添加 userId 列
                database.execSQL("ALTER TABLE taskgroups ADD COLUMN userId TEXT NOT NULL DEFAULT ''");
                Log.d(TAG, "Added userId column to taskgroups table");
            } catch (Exception e) {
                Log.w(TAG, "Adding userId column to taskgroups failed, it might already exist: " + e.getMessage());
            }
        }
    };
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.d(TAG, "Migrating database from version 9 to 10: Adding updatedAt to taskgroups");
            try {
                // 为 taskgroups 表添加 updatedAt 列，并设置默认值为 createdAt 的值，或者当前时间
                // 使用System.currentTimeMillis()作为默认值可能更简单，如果旧数据没有createdAt，或createdAt不准确
                database.execSQL("ALTER TABLE taskgroups ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
                // 可选：将现有记录的 updatedAt 更新为 createdAt 的值（如果 createdAt 存在且有意义）
                // database.execSQL("UPDATE taskgroups SET updatedAt = createdAt WHERE updatedAt = 0");
                // 或者更新为当前时间
                database.execSQL("UPDATE taskgroups SET updatedAt = " + System.currentTimeMillis() + " WHERE updatedAt = 0");
                Log.d(TAG, "Added updatedAt column to taskgroups table and set default values.");
            } catch (Exception e) {
                Log.w(TAG, "Adding updatedAt column to taskgroups failed, it might already exist or other error: " + e.getMessage());
            }
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
                                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10) // 添加所有迁移策略
                                .fallbackToDestructiveMigration() // 当迁移失败时允许重建数据库
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
                            ).fallbackToDestructiveMigration()
                             .build();
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
