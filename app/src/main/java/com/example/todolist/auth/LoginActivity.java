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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化 Firebase
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        Log.d("FirebaseCheck", "FirebaseApp is: " + FirebaseApp.getInstance());
        // 如果用户已经登录，直接进入主界面
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }
        // 加载登录界面布局
        setContentView(R.layout.activity_login);
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
            // 使用 Firebase Auth 进行登录
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // 登录成功，进入主界面
                            Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
//                            SyncWorker.triggerSyncNow(getApplicationContext());
                            SyncWorker.pullCloudToLocal(getApplicationContext());
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                            }, 1000); // 延迟1秒进入主界面
//                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//                            if (user != null) {
//                                String uid = user.getUid();
//                                Log.d("FirebaseUID", "当前用户 UID: " + uid);  // 打印到 Logcat
//                                Toast.makeText(getApplicationContext(), "当前UID: " + uid, Toast.LENGTH_LONG).show(); // 显示给用户看
//                            }

//                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
//                            finish();

                        } else {
                            // 登录失败
                            Toast.makeText(LoginActivity.this, "登录失败："
                                            + (task.getException() != null ? task.getException().getMessage() : ""),
                                    Toast.LENGTH_SHORT).show();
                            Exception e = task.getException();
                            e.printStackTrace();
                            Log.e("FirebaseAuth", "登录失败", e);
                            Toast.makeText(LoginActivity.this, "登录失败：" + (e != null ? e.getMessage() : "未知错误"), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // 注册按钮点击事件
        btnRegister.setOnClickListener(v -> {
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString().trim();
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(LoginActivity.this, "请输入邮箱和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // 注册成功，自动登录并进入主界面
                            Toast.makeText(LoginActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            // 注册失败
                            Toast.makeText(LoginActivity.this, "注册失败："
                                            + (task.getException() != null ? task.getException().getMessage() : ""),
                                    Toast.LENGTH_SHORT).show();
                            Exception e = task.getException();
                            e.printStackTrace(); // ✅ 打印异常堆栈到 Logcat
                            Log.e("FirebaseAuth", "注册失败", e); // ✅ 添加 Log 输出
                            Toast.makeText(LoginActivity.this, "注册失败：" + (e != null ? e.getMessage() : "未知错误"), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
