package com.example.todolist.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.todolist.sync.SyncWorker;
import com.example.todolist.ui.MainActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.todolist.R;

public class LoginActivity extends AppCompatActivity {
    private EditText editEmail;
    private EditText editPassword;
    private Button btnLogin;
    private Button btnRegister;
    private FirebaseAuth auth;
    private static final String TAG = "LoginActivity";
    private boolean firebaseInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "LoginActivity onCreate 开始");
        
        try {
            // 初始化 Firebase
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this);
                    auth = FirebaseAuth.getInstance();
                    firebaseInitialized = true;
                    Log.d(TAG, "Firebase 初始化成功");
                } catch (Exception e) {
                    Log.e(TAG, "Firebase 初始化失败", e);
                    firebaseInitialized = false;
                    // Firebase初始化失败，继续加载UI
                }
            } else {
                auth = FirebaseAuth.getInstance();
                firebaseInitialized = true;
                Log.d(TAG, "Firebase实例已存在");
            }
            
            // 如果Firebase初始化成功且用户已经登录，直接进入主界面
            if (firebaseInitialized && auth.getCurrentUser() != null) {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Firebase初始化或登录状态检查失败", e);
            // 继续加载UI，用户可以手动登录
        }
        
        // 加载登录界面布局
        try {
            setContentView(R.layout.activity_login);
            Log.d(TAG, "LoginActivity 布局加载完成");
        } catch (Exception e) {
            Log.e(TAG, "加载登录布局失败", e);
            // 如果无法加载登录界面，尝试直接进入主界面
            try {
                Toast.makeText(this, "登录界面加载失败，尝试直接进入应用", Toast.LENGTH_LONG).show();
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
                return;
            } catch (Exception ex) {
                Log.e(TAG, "无法启动任何活动", ex);
                Toast.makeText(this, "应用启动失败，请重新安装", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        
        try {
            editEmail = findViewById(R.id.editEmail);
            editPassword = findViewById(R.id.editPassword);
            btnLogin = findViewById(R.id.buttonLogin);
            btnRegister = findViewById(R.id.buttonRegister);

            // 登录按钮点击事件
            btnLogin.setOnClickListener(v -> {
                String email = editEmail.getText().toString().trim();
                String password = editPassword.getText().toString().trim();
                if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                    Toast.makeText(LoginActivity.this, "请输入邮箱和密码", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                try {
                    // 使用 Firebase Auth 进行登录
                    auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    // 登录成功，进入主界面
                                    Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                                    try {
                                        SyncWorker.pullCloudToLocal(getApplicationContext());
                                    } catch (Exception e) {
                                        Log.e(TAG, "同步数据失败", e);
                                    }
                                    
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        finish();
                                    }, 1000); // 延迟1秒进入主界面
                                } else {
                                    // 登录失败
                                    Exception e = task.getException();
                                    Log.e(TAG, "登录失败", e);
                                    Toast.makeText(LoginActivity.this, "登录失败：" + (e != null ? e.getMessage() : "未知错误"), Toast.LENGTH_LONG).show();
                                }
                            });
                } catch (Exception e) {
                    Log.e(TAG, "登录操作失败", e);
                    Toast.makeText(LoginActivity.this, "登录失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    // 如果Firebase身份验证失败，直接进入主界面（开发时使用，实际应删除）
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                }
            });

            // 注册按钮点击事件
            btnRegister.setOnClickListener(v -> {
                String email = editEmail.getText().toString().trim();
                String password = editPassword.getText().toString().trim();
                if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                    Toast.makeText(LoginActivity.this, "请输入邮箱和密码", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                try {
                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    // 注册成功，自动登录并进入主界面
                                    Toast.makeText(LoginActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                } else {
                                    // 注册失败
                                    Exception e = task.getException();
                                    Log.e(TAG, "注册失败", e);
                                    Toast.makeText(LoginActivity.this, "注册失败：" + (e != null ? e.getMessage() : "未知错误"), Toast.LENGTH_LONG).show();
                                }
                            });
                } catch (Exception e) {
                    Log.e(TAG, "注册操作失败", e);
                    Toast.makeText(LoginActivity.this, "注册失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    // 如果Firebase注册失败，仍允许进入主界面（开发时使用，实际应删除）
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "UI初始化失败", e);
            // 如果UI初始化失败，仍然尝试启动主活动
            try {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            } catch (Exception ex) {
                Log.e(TAG, "无法启动主活动", ex);
                Toast.makeText(this, "应用启动失败，请重新安装应用", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
