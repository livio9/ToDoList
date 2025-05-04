package com.example.todolist.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.todolist.sync.SyncWorker;
import com.example.todolist.ui.MainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.parse.ParseUser;
import com.parse.LogInCallback;
import com.parse.SignUpCallback;
import com.parse.ParseException;
import com.example.todolist.R;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private TextInputLayout inputLayoutEmail;
    private TextInputLayout inputLayoutPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnRegister;
    private TextView txtForgotPassword;
    private MaterialCheckBox checkboxRememberMe;
    private SessionManager sessionManager;
    private static final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        // 初始化会话管理器
        sessionManager = SessionManager.getInstance(this);
        
        // 检查是否是崩溃后重启
        checkForPreviousCrash();
        
        // 初始化控件
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        inputLayoutEmail = findViewById(R.id.inputLayoutEmail);
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword);
        btnLogin = findViewById(R.id.buttonLogin);
        btnRegister = findViewById(R.id.buttonRegister);
        txtForgotPassword = findViewById(R.id.textForgotPassword);
        checkboxRememberMe = findViewById(R.id.checkboxRememberMe);
        
        // 设置点击事件
        btnLogin.setOnClickListener(v -> {
            if (validateInputs()) {
                login();
            }
        });
        
        btnRegister.setOnClickListener(v -> {
            if (validateInputs()) {
                register();
            }
        });
        
        txtForgotPassword.setOnClickListener(v -> showResetPasswordDialog());
        
        // 输入框焦点变化监听，用于隐藏错误提示
        editEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                inputLayoutEmail.setError(null);
            }
        });
        
        editPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                inputLayoutPassword.setError(null);
            }
        });
        
        // 从会话管理器获取保存的邮箱地址
        String savedEmail = sessionManager.getSavedEmail();
        if (!TextUtils.isEmpty(savedEmail)) {
            editEmail.setText(savedEmail);
            editPassword.requestFocus(); // 自动聚焦到密码框
        }
        
        // 检查是否已登录
        if (ParseUser.getCurrentUser() != null) {
            // 已经登录，直接进入主界面
            startMainActivity();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 检查是否已自动登录
        if (ParseUser.getCurrentUser() != null) {
            startMainActivity();
        }
    }
    
    /**
     * 检查上次应用是否异常退出
     */
    private void checkForPreviousCrash() {
        try {
            SharedPreferences prefs = getSharedPreferences("crash_info", MODE_PRIVATE);
            boolean hadCrash = prefs.getBoolean("had_crash", false);
            
            if (hadCrash) {
                // 清除崩溃标记
                prefs.edit().putBoolean("had_crash", false).apply();
                
                // 获取崩溃时间
                long crashTime = prefs.getLong("crash_time", 0);
                long timeSinceCrash = System.currentTimeMillis() - crashTime;
                
                // 如果崩溃发生在短时间内，显示提示
                if (timeSinceCrash < 60000) { // 一分钟内
                    Toast.makeText(this, 
                        "应用上次运行时出现问题，已修复。如再次出现，请联系开发者。", 
                        Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查崩溃信息失败", e);
        }
    }
    
    // 验证输入
    private boolean validateInputs() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        boolean isValid = true;
        
        // 清除之前的错误
        inputLayoutEmail.setError(null);
        inputLayoutPassword.setError(null);
        
        // 验证邮箱
        if (TextUtils.isEmpty(email)) {
            inputLayoutEmail.setError("请输入邮箱");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputLayoutEmail.setError("请输入有效的邮箱地址");
            isValid = false;
        }
        
        // 验证密码
        if (TextUtils.isEmpty(password)) {
            inputLayoutPassword.setError("请输入密码");
            isValid = false;
        } else if (password.length() < 6) {
            inputLayoutPassword.setError("密码长度至少为6位");
            isValid = false;
        }
        
        return isValid;
    }
    
    // 登录方法
    private void login() {
        try {
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString().trim();
            boolean rememberMe = checkboxRememberMe.isChecked();
            
            // 显示加载进度
            btnLogin.setEnabled(false);
            btnLogin.setText("登录中...");
            
            try {
                ParseUser.logInInBackground(email, password, new LogInCallback() {
                    @Override
                    public void done(ParseUser user, ParseException e) {
                        try {
                            if (user != null) {
                                // 登录成功，确保邮箱信息完整
                                try {
                                    if (TextUtils.isEmpty(user.getEmail())) {
                                        user.setEmail(email);
                                        user.put("userEmail", email);
                                        user.saveInBackground();
                                    }
                                } catch (Exception ex) {
                                    Log.e(TAG, "更新用户邮箱失败", ex);
                                }
                                
                                // 保存会话信息
                                try {
                                    sessionManager.saveUserSession(
                                        email, 
                                        password, 
                                        user.getSessionToken(), 
                                        rememberMe
                                    );
                                } catch (Exception ex) {
                                    Log.e(TAG, "保存会话信息失败", ex);
                                }
                                
                                Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                
                                // 直接进入主界面
                                startMainActivity();
                            } else {
                                // 登录失败
                                btnLogin.setEnabled(true);
                                btnLogin.setText("登录");
                                
                                String errorMsg = "未知错误";
                                if (e != null) {
                                    switch (e.getCode()) {
                                        case ParseException.OBJECT_NOT_FOUND:
                                            errorMsg = "用户名或密码错误";
                                            break;
                                        case ParseException.CONNECTION_FAILED:
                                            errorMsg = "网络连接失败，请检查网络";
                                            break;
                                        default:
                                            errorMsg = e.getMessage();
                                            break;
                                    }
                                }
                                
                                Toast.makeText(LoginActivity.this, "登录失败：" + errorMsg, Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "登录回调处理失败", ex);
                            btnLogin.setEnabled(true);
                            btnLogin.setText("登录");
                            Toast.makeText(LoginActivity.this, "登录出错: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "登录操作失败", e);
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");
                Toast.makeText(LoginActivity.this, "登录失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "登录方法执行失败", e);
            try {
                btnLogin.setEnabled(true);
                btnLogin.setText("登录");
            } catch (Exception ex) {
                Log.e(TAG, "重置按钮状态失败", ex);
            }
            Toast.makeText(LoginActivity.this, "登录功能出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // 注册方法
    private void register() {
        try {
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString().trim();
            boolean rememberMe = checkboxRememberMe.isChecked();
            
            // 显示加载进度
            btnRegister.setEnabled(false);
            btnRegister.setText("注册中...");
            
            // 创建Parse用户
            ParseUser user = new ParseUser();
            user.setUsername(email);
            user.setPassword(password);
            user.setEmail(email);
            user.put("userEmail", email);
            
            // 后台执行注册
            user.signUpInBackground(new SignUpCallback() {
                @Override
                public void done(ParseException e) {
                    try {
                        if (e == null) {
                            // 注册成功，再次确保邮箱保存成功
                            try {
                                final ParseUser currentUser = ParseUser.getCurrentUser();
                                if (currentUser != null && TextUtils.isEmpty(currentUser.getEmail())) {
                                    currentUser.setEmail(email);
                                    currentUser.put("userEmail", email); 
                                    currentUser.saveInBackground();
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "保存用户邮箱失败", ex);
                            }
                            
                            // 保存会话信息
                            try {
                                ParseUser loggedInUser = ParseUser.getCurrentUser();
                                if (loggedInUser != null) {
                                    sessionManager.saveUserSession(
                                        email, 
                                        password, 
                                        loggedInUser.getSessionToken(), 
                                        rememberMe
                                    );
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "注册后保存会话信息失败", ex);
                            }
                            
                            Toast.makeText(LoginActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                            
                            // 直接进入主界面
                            startMainActivity();
                        } else {
                            btnRegister.setEnabled(true);
                            btnRegister.setText("注册新账号");
                            
                            String errorMsg;
                            switch (e.getCode()) {
                                case ParseException.USERNAME_TAKEN:
                                    errorMsg = "账号已存在";
                                    break;
                                case ParseException.EMAIL_TAKEN:
                                    errorMsg = "邮箱已被使用";
                                    break;
                                case ParseException.CONNECTION_FAILED:
                                    errorMsg = "网络连接失败，请检查网络";
                                    break;
                                default:
                                    errorMsg = e.getMessage();
                                    break;
                            }
                            
                            Toast.makeText(LoginActivity.this, "注册失败：" + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "注册回调处理失败", ex);
                        btnRegister.setEnabled(true);
                        btnRegister.setText("注册新账号");
                        Toast.makeText(LoginActivity.this, "注册处理出错: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "注册方法执行失败", e);
            btnRegister.setEnabled(true);
            btnRegister.setText("注册新账号");
            Toast.makeText(this, "注册功能出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // 显示密码重置对话框
    private void showResetPasswordDialog() {
        try {
            // 创建对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("重置密码");
            
            // 设置内容
            final TextInputEditText input = new TextInputEditText(this);
            input.setHint("请输入您的邮箱");
            input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            
            // 如果邮箱输入框有内容，则预填充
            String currentEmail = editEmail.getText().toString().trim();
            if (!TextUtils.isEmpty(currentEmail)) {
                input.setText(currentEmail);
            }
            
            builder.setView(input);
            
            // 设置按钮
            builder.setPositiveButton("重置", (dialog, which) -> {
                String email = input.getText().toString().trim();
                if (!TextUtils.isEmpty(email)) {
                    requestPasswordReset(email);
                } else {
                    Toast.makeText(LoginActivity.this, "请输入邮箱", Toast.LENGTH_SHORT).show();
                }
            });
            
            builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
            
            // 显示对话框
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "显示密码重置对话框失败", e);
            Toast.makeText(this, "操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 请求密码重置
    private void requestPasswordReset(String email) {
        try {
            ParseUser.requestPasswordResetInBackground(email, e -> {
                if (e == null) {
                    Toast.makeText(LoginActivity.this, "密码重置邮件已发送", Toast.LENGTH_LONG).show();
                } else {
                    String errorMsg;
                    switch (e.getCode()) {
                        case ParseException.EMAIL_NOT_FOUND:
                            errorMsg = "未找到该邮箱";
                            break;
                        case ParseException.CONNECTION_FAILED:
                            errorMsg = "网络连接失败";
                            break;
                        default:
                            errorMsg = e.getMessage();
                            break;
                    }
                    
                    Toast.makeText(LoginActivity.this, "密码重置失败: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "请求密码重置失败", e);
            Toast.makeText(this, "操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 启动主界面
    private void startMainActivity() {
        try {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // 清除之前的Activity
            startActivity(intent);
            finish();
        } catch (Exception ex) {
            Log.e(TAG, "启动主界面失败", ex);
            Toast.makeText(LoginActivity.this, "启动应用失败: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
