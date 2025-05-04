package com.example.todolist;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParseUser;
import com.example.todolist.auth.SessionManager;
import com.example.todolist.utils.NetworkStateMonitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TodoList extends Application {
    private static final String TAG = "TodoList";
    private boolean parseInitialized = false;
    private SessionManager sessionManager;
    private NetworkStateMonitor networkMonitor;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化会话管理器
        sessionManager = SessionManager.getInstance(this);
        
        // 初始化网络监听器
        networkMonitor = NetworkStateMonitor.getInstance(this);
        networkMonitor.startMonitoring();
        
        // 设置全局异常处理器
        setupUncaughtExceptionHandler();
        
        // 初始化Parse SDK，使用线程和多层异常捕获
        Thread initThread = new Thread(() -> {
            try {
                if (isNetworkAvailable()) {
                    // 初始化Parse
                    initParse();
                    
                    // 尝试恢复会话
                    tryRestoreSession();
                } else {
                    Log.w(TAG, "网络不可用，延迟初始化Parse");
                    // 注册网络可用监听
                    networkMonitor.addListener(new NetworkStateMonitor.NetworkStateListener() {
                        @Override
                        public void onNetworkAvailable() {
                            // 网络恢复后初始化
                            Log.d(TAG, "网络已恢复，开始初始化Parse");
                            if (!parseInitialized) {
                                initParse();
                                tryRestoreSession();
                            }
                            // 移除监听器
                            networkMonitor.removeListener(this);
                        }
                        
                        @Override
                        public void onNetworkLost() {
                            // 网络断开不处理
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Parse初始化线程异常", e);
            }
        });
        
        try {
            // 在后台线程初始化，不阻塞UI
            initThread.start();
        } catch (Exception e) {
            Log.e(TAG, "启动Parse初始化线程失败", e);
        }
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        // 停止网络监听
        if (networkMonitor != null) {
            networkMonitor.stopMonitoring();
        }
    }
    
    /**
     * 设置全局未捕获异常处理器
     */
    private void setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "应用发生未捕获的异常", throwable);
            
            try {
                // 记录崩溃信息到本地
                logCrashToLocal(throwable);
                
                // 保存崩溃信息到SharePreferences
                getSharedPreferences("crash_info", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("had_crash", true)
                    .putLong("crash_time", System.currentTimeMillis())
                    .apply();
                
                // 简单处理：直接退出应用
                // 下次启动时会检查并处理崩溃
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            } catch (Exception e) {
                Log.e(TAG, "异常处理器处理异常时又出现异常", e);
            }
        });
    }
    
    /**
     * 记录崩溃信息到本地文件
     */
    private void logCrashToLocal(Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTraceString = sw.toString();
            
            // 记录到日志
            Log.e(TAG, "崩溃详情: " + stackTraceString);
            
            // 也可以在这里实现将崩溃信息保存到文件或数据库的逻辑
        } catch (Exception e) {
            Log.e(TAG, "记录崩溃信息失败", e);
        }
    }
    
    /**
     * 初始化Parse SDK，带有异常处理
     */
    private void initParse() {
        if (parseInitialized) {
            return;
        }
        
        try {
            Parse.initialize(new Parse.Configuration.Builder(this)
                    .applicationId("myTODOListId")
                    .server("http://121.43.161.183:1337/parse")
                    .enableLocalDataStore() // 启用本地数据存储，提高离线体验
                    .build()
            );
            
            // 保存安装信息，增加异常捕获
            try {
                ParseInstallation.getCurrentInstallation().saveInBackground();
                Log.d(TAG, "Parse安装信息保存成功");
            } catch (Exception e) {
                Log.e(TAG, "Parse安装信息保存失败", e);
            }
            
            parseInitialized = true;
            Log.d(TAG, "Parse SDK初始化成功");
            
            // 检查当前用户信息
            checkCurrentUserEmail();
        } catch (Exception e) {
            Log.e(TAG, "Parse SDK初始化失败", e);
        }
    }
    
    /**
     * 尝试恢复用户会话
     */
    private void tryRestoreSession() {
        try {
            // 首先检查当前是否有用户登录
            if (ParseUser.getCurrentUser() != null) {
                Log.d(TAG, "已有用户登录，无需恢复会话");
                return;
            }
            
            Log.d(TAG, "尝试恢复用户会话...");
            boolean restored = sessionManager.restoreUserSession();
            
            if (restored) {
                Log.d(TAG, "会话恢复成功");
            } else {
                Log.d(TAG, "会话令牌恢复失败，尝试自动登录");
                
                // 使用保存的邮箱密码尝试自动登录
                sessionManager.autoLogin((success, exception) -> {
                    if (success) {
                        Log.d(TAG, "自动登录成功");
                    } else {
                        Log.d(TAG, "自动登录失败，需要手动登录");
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "恢复会话过程出现异常", e);
        }
    }
    
    /**
     * 检查网络连接是否可用
     */
    private boolean isNetworkAvailable() {
        return networkMonitor.isNetworkAvailable();
    }
    
    /**
     * 检查并修复当前用户的邮箱信息
     */
    private void checkCurrentUserEmail() {
        try {
            // 获取当前用户
            ParseUser currentUser = ParseUser.getCurrentUser();
            if (currentUser != null) {
                Log.d(TAG, "当前已登录用户: " + currentUser.getUsername());
                
                // 检查邮箱字段
                String email = currentUser.getEmail();
                Log.d(TAG, "当前用户邮箱: " + (email != null ? email : "null"));
                
                // 如果标准邮箱字段为空，尝试从其他字段恢复
                if (TextUtils.isEmpty(email)) {
                    // 尝试从userEmail字段获取
                    String userEmail = currentUser.getString("userEmail");
                    if (!TextUtils.isEmpty(userEmail)) {
                        currentUser.setEmail(userEmail);
                        Log.d(TAG, "从userEmail恢复邮箱: " + userEmail);
                    } else if (currentUser.getUsername().contains("@")) {
                        // 如果用户名看起来像邮箱，也用它来恢复
                        currentUser.setEmail(currentUser.getUsername());
                        currentUser.put("userEmail", currentUser.getUsername());
                        Log.d(TAG, "从用户名恢复邮箱: " + currentUser.getUsername());
                    }
                    
                    // 保存更新
                    try {
                        currentUser.save();
                        Log.d(TAG, "用户邮箱信息更新成功");
                    } catch (ParseException e) {
                        Log.e(TAG, "保存用户邮箱失败: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查用户邮箱时出错", e);
        }
    }
    
    /**
     * 获取网络状态监听器
     */
    public NetworkStateMonitor getNetworkMonitor() {
        return networkMonitor;
    }
} 