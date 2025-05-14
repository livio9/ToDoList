package com.example.todolist.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.parse.ParseException;
import com.parse.ParseUser;

/**
 * 会话管理器 - 负责用户会话的持久化和恢复
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    
    // SharedPreferences 常量
    private static final String PREF_NAME = "user_session";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PASSWORD = "user_password";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_REMEMBER_ME = "remember_me";
    
    private static SessionManager instance;
    private final SharedPreferences preferences;
    private final SharedPreferences.Editor editor;
    private final Context context;
    
    private SessionManager(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }
    
    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 保存用户会话信息
     */
    public void saveUserSession(String email, String password, String sessionToken, boolean rememberMe) {
        try {
            editor.putString(KEY_USER_EMAIL, email);
            
            // 只有在"记住我"选项开启时才保存密码
            if (rememberMe) {
                editor.putString(KEY_USER_PASSWORD, password);
            } else {
                editor.remove(KEY_USER_PASSWORD);
            }
            
            editor.putString(KEY_SESSION_TOKEN, sessionToken);
            editor.putBoolean(KEY_REMEMBER_ME, rememberMe);
            editor.apply();
            
            Log.d(TAG, "保存用户会话成功: " + email);
        } catch (Exception e) {
            Log.e(TAG, "保存用户会话失败", e);
        }
    }
    
    /**
     * 尝试使用会话令牌恢复用户登录
     * @return 是否成功恢复会话
     */
    public boolean restoreUserSession() {
        try {
            String sessionToken = preferences.getString(KEY_SESSION_TOKEN, null);
            
            if (!TextUtils.isEmpty(sessionToken)) {
                try {
                    // 尝试使用会话令牌恢复登录
                    ParseUser.becomeInBackground(sessionToken, (user, e) -> {
                        if (e == null && user != null) {
                            Log.d(TAG, "会话恢复成功: " + user.getUsername());
                        } else {
                            // 会话恢复失败，可能是会话已过期
                            Log.e(TAG, "会话恢复失败", e);
                            clearUserSession();
                        }
                    });
                    
                    return ParseUser.getCurrentUser() != null;
                } catch (Exception e) {
                    Log.e(TAG, "会话恢复过程异常", e);
                    clearUserSession();
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "尝试恢复会话失败", e);
        }
        
        return false;
    }
    
    /**
     * 尝试使用保存的邮箱和密码自动登录
     * @param callback 登录结果回调
     */
    public void autoLogin(AutoLoginCallback callback) {
        try {
            // 如果已经登录，直接返回成功
            if (ParseUser.getCurrentUser() != null) {
                callback.onResult(true, null);
                return;
            }
            
            String email = preferences.getString(KEY_USER_EMAIL, null);
            String password = preferences.getString(KEY_USER_PASSWORD, null);
            
            if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(password)) {
                Log.d(TAG, "尝试自动登录: " + email);
                
                ParseUser.logInInBackground(email, password, (user, e) -> {
                    if (user != null) {
                        // 登录成功，更新会话令牌
                        saveUserSession(email, password, user.getSessionToken(), true);
                        callback.onResult(true, null);
                    } else {
                        // 登录失败
                        Log.e(TAG, "自动登录失败", e);
                        callback.onResult(false, e);
                    }
                });
            } else {
                // 没有保存的凭据
                callback.onResult(false, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "自动登录过程异常", e);
            // 将一般Exception转换为ParseException
            ParseException parseException = (e instanceof ParseException) ? 
                (ParseException) e : 
                new ParseException(ParseException.OTHER_CAUSE, e.getMessage());
            callback.onResult(false, parseException);
        }
    }
    
    /**
     * 更新会话令牌（通常在手动登录成功后调用）
     */
    public void updateSessionToken(String sessionToken) {
        try {
            if (!TextUtils.isEmpty(sessionToken)) {
                editor.putString(KEY_SESSION_TOKEN, sessionToken);
                editor.apply();
                Log.d(TAG, "更新会话令牌成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "更新会话令牌失败", e);
        }
    }
    
    /**
     * 清除用户会话
     */
    public void clearUserSession() {
        try {
            String email = preferences.getString(KEY_USER_EMAIL, null); // 保留邮箱

            editor.remove(KEY_USER_PASSWORD); // 明确移除密码
            editor.remove(KEY_SESSION_TOKEN); // 移除会话令牌
            editor.remove(KEY_REMEMBER_ME); // 移除记住我状态
            // 如果有其他与会话相关的键，也在这里移除
            editor.apply(); // 先应用一次移除操作

            Log.d(TAG, "用户会话信息已清除 (密码、令牌、记住我状态)。邮箱保留（如果之前存在）。");
        } catch (Exception e) {
            Log.e(TAG, "清除用户会话失败", e);
        }
    }
    
    /**
     * 获取保存的用户邮箱
     */
    public String getSavedEmail() {
        return preferences.getString(KEY_USER_EMAIL, "");
    }
    
    /**
     * 自动登录回调接口
     */
    public interface AutoLoginCallback {
        void onResult(boolean success, ParseException exception);
    }
} 