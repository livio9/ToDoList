package com.example.todolist.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.todolist.R;
import com.example.todolist.data.Todo;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

public class PomodoroTimerDialog extends Dialog {

    private static final long FOCUS_TIME_MILLIS = 25 * 60 * 1000; // 25分钟
    private static final long BREAK_TIME_MILLIS = 5 * 60 * 1000;  // 5分钟
    private static final int MAX_PROGRESS = 100;

    private CircularProgressIndicator progressBar;
    private TextView textTimeRemaining;
    private TextView textTitle;
    private TextView textStatus;
    private Button buttonPause;
    private Button buttonStop;
    private TextView textTaskName;

    private CountDownTimer timer;
    private long timeLeftMillis;
    private boolean isPaused = false;
    private boolean isBreakTime = false;
    private Todo currentTask;
    private OnTimerCompletedListener listener;

    public interface OnTimerCompletedListener {
        void onTimerCompleted(boolean isBreakCompleted);
    }

    public PomodoroTimerDialog(@NonNull Context context, Todo task) {
        super(context);
        this.currentTask = task;
    }

    public void setOnTimerCompletedListener(OnTimerCompletedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_pomodoro_timer);

        // 初始化视图
        progressBar = findViewById(R.id.progressBar);
        textTimeRemaining = findViewById(R.id.textTimeRemaining);
        textTitle = findViewById(R.id.textTitle);
        textStatus = findViewById(R.id.textStatus);
        buttonPause = findViewById(R.id.buttonPause);
        buttonStop = findViewById(R.id.buttonStop);
        textTaskName = findViewById(R.id.textTaskName);

        // 设置任务标题
        if (currentTask != null && currentTask.title != null) {
            textTaskName.setText(currentTask.title);
        }

        // 初始化为专注时间
        startFocusTimer();

        // 设置按钮点击事件
        buttonPause.setOnClickListener(v -> {
            if (isPaused) {
                resumeTimer();
            } else {
                pauseTimer();
            }
        });

        buttonStop.setOnClickListener(v -> {
            stopTimer();
            dismiss();
        });

        // 设置不可取消
        setCancelable(false);
    }

    private void startFocusTimer() {
        isBreakTime = false;
        textTitle.setText("专注时间");
        textStatus.setText("正在专注，请勿分心...");
        timeLeftMillis = FOCUS_TIME_MILLIS;
        startTimer();
    }

    private void startBreakTimer() {
        isBreakTime = true;
        textTitle.setText("休息时间");
        textStatus.setText("休息一下，准备下一个番茄钟...");
        timeLeftMillis = BREAK_TIME_MILLIS;
        startTimer();
    }

    private void startTimer() {
        progressBar.setProgress(MAX_PROGRESS);
        
        if (timer != null) {
            timer.cancel();
        }
        
        // 更新倒计时文本
        updateCountDownText();
        
        // 创建并启动倒计时
        timer = new CountDownTimer(timeLeftMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = millisUntilFinished;
                updateCountDownText();
                
                // 更新进度条
                int progress = (int) (millisUntilFinished * MAX_PROGRESS / 
                        (isBreakTime ? BREAK_TIME_MILLIS : FOCUS_TIME_MILLIS));
                progressBar.setProgress(progress);
            }

            @Override
            public void onFinish() {
                // 当前计时结束
                if (isBreakTime) {
                    // 休息时间结束，回到专注时间
                    if (listener != null) {
                        listener.onTimerCompleted(true);
                    }
                    startFocusTimer();
                } else {
                    // 专注时间结束，进入休息时间
                    if (listener != null) {
                        listener.onTimerCompleted(false);
                    }
                    startBreakTimer();
                }
            }
        }.start();
        
        buttonPause.setText("暂停");
        isPaused = false;
    }

    private void pauseTimer() {
        if (timer != null) {
            timer.cancel();
        }
        isPaused = true;
        buttonPause.setText("继续");
    }

    private void resumeTimer() {
        startTimer();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftMillis / 1000) / 60;
        int seconds = (int) (timeLeftMillis / 1000) % 60;
        String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        textTimeRemaining.setText(timeFormatted);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTimer();
    }
} 