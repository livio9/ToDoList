package com.example.todolist.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.todolist.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * 加载状态管理器
 * 用于管理加载中、错误、空数据等状态的显示
 */
public class LoadingStateManager {
    private static final String TAG = "LoadingStateManager";
    
    // 状态类型
    public static final int STATE_LOADING = 0;       // 加载中
    public static final int STATE_SUCCESS = 1;       // 加载成功
    public static final int STATE_ERROR = 2;         // 加载失败
    public static final int STATE_EMPTY = 3;         // 数据为空
    public static final int STATE_NETWORK_ERROR = 4; // 网络错误
    
    private final Context context;
    private ViewGroup rootView;          // 根视图
    private ViewGroup contentView;       // 内容视图
    private View loadingView;            // 加载中视图
    private View errorView;              // 错误视图
    private View emptyView;              // 空数据视图
    private View networkErrorView;       // 网络错误视图
    
    private int currentState = STATE_LOADING;
    private boolean isInitialized = false;
    
    // 初始化各种状态布局
    private void initializeStateViews() {
        if (isInitialized) {
            return;
        }
        
        try {
            // 加载中视图
            loadingView = View.inflate(context, R.layout.view_loading, null);
            
            // 错误视图
            errorView = View.inflate(context, R.layout.view_error, null);
            Button btnRetry = errorView.findViewById(R.id.btnRetry);
            btnRetry.setOnClickListener(v -> {
                if (retryListener != null) {
                    retryListener.onRetry();
                }
            });
            
            // 空数据视图
            emptyView = View.inflate(context, R.layout.view_empty, null);
            
            // 网络错误视图
            networkErrorView = View.inflate(context, R.layout.view_network_error, null);
            Button btnNetworkRetry = networkErrorView.findViewById(R.id.btnNetworkRetry);
            btnNetworkRetry.setOnClickListener(v -> {
                if (retryListener != null) {
                    retryListener.onRetry();
                }
            });
            
            // 添加到根视图
            for (View view : new View[]{loadingView, errorView, emptyView, networkErrorView}) {
                view.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                view.setVisibility(View.GONE);
                rootView.addView(view);
            }
            
            isInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "初始化状态视图失败", e);
        }
    }
    
    // 私有构造函数
    private LoadingStateManager(Context context) {
        this.context = context;
    }
    
    /**
     * 创建LoadingStateManager实例
     * @param context 上下文
     * @param contentView 内容视图
     * @return LoadingStateManager实例
     */
    public static LoadingStateManager wrap(Context context, ViewGroup contentView) {
        if (!(contentView.getParent() instanceof ViewGroup)) {
            throw new IllegalArgumentException("ContentView必须有父视图");
        }
        
        LoadingStateManager manager = new LoadingStateManager(context);
        manager.contentView = contentView;
        manager.rootView = (ViewGroup) contentView.getParent();
        manager.initializeStateViews();
        return manager;
    }
    
    /**
     * 显示指定状态
     * @param state 状态类型
     */
    public void showState(int state) {
        if (!isInitialized) {
            Log.e(TAG, "状态管理器未初始化");
            return;
        }
        
        try {
            // 如果状态未变，不处理
            if (currentState == state) {
                return;
            }
            
            currentState = state;
            
            // 隐藏所有状态视图
            loadingView.setVisibility(View.GONE);
            errorView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            networkErrorView.setVisibility(View.GONE);
            
            // 根据状态显示对应视图
            switch (state) {
                case STATE_LOADING:
                    contentView.setVisibility(View.GONE);
                    loadingView.setVisibility(View.VISIBLE);
                    break;
                case STATE_SUCCESS:
                    contentView.setVisibility(View.VISIBLE);
                    break;
                case STATE_ERROR:
                    contentView.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    break;
                case STATE_EMPTY:
                    contentView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    break;
                case STATE_NETWORK_ERROR:
                    contentView.setVisibility(View.GONE);
                    networkErrorView.setVisibility(View.VISIBLE);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "显示状态失败", e);
        }
    }
    
    /**
     * 获取当前状态
     * @return 当前状态
     */
    public int getCurrentState() {
        return currentState;
    }
    
    /**
     * 设置空视图提示文本和图标
     */
    public void setEmptyViewContent(@StringRes int textResId, @DrawableRes int iconResId) {
        try {
            TextView textView = emptyView.findViewById(R.id.textEmptyHint);
            ImageView imageView = emptyView.findViewById(R.id.imageEmpty);
            
            if (textResId != 0) {
                textView.setText(textResId);
            }
            
            if (iconResId != 0) {
                imageView.setImageResource(iconResId);
            }
        } catch (Exception e) {
            Log.e(TAG, "设置空视图内容失败", e);
        }
    }
    
    /**
     * 设置错误视图提示文本
     */
    public void setErrorViewContent(@StringRes int textResId, String errorMessage) {
        try {
            TextView textView = errorView.findViewById(R.id.textErrorMessage);
            
            if (textResId != 0) {
                textView.setText(textResId);
            } else if (errorMessage != null) {
                textView.setText(errorMessage);
            }
        } catch (Exception e) {
            Log.e(TAG, "设置错误视图内容失败", e);
        }
    }
    
    // 重试监听器
    private RetryListener retryListener;
    
    /**
     * 设置重试监听器
     */
    public void setRetryListener(RetryListener listener) {
        this.retryListener = listener;
    }
    
    /**
     * 显示网络错误提示
     */
    public static void showNetworkErrorDialog(Context context, Runnable onRetry) {
        try {
            if (context instanceof Activity && !((Activity) context).isFinishing()) {
                new AlertDialog.Builder(context)
                        .setTitle("网络连接异常")
                        .setMessage("请检查您的网络连接后重试")
                        .setPositiveButton("重试", (dialog, which) -> {
                            if (onRetry != null) {
                                onRetry.run();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        } catch (Exception e) {
            Log.e(TAG, "显示网络错误对话框失败", e);
        }
    }
    
    /**
     * 显示Snackbar提示
     */
    public static void showSnackbar(View view, String message, int duration) {
        try {
            if (view != null && message != null) {
                Snackbar.make(view, message, duration).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "显示Snackbar失败", e);
        }
    }
    
    /**
     * 重试监听器接口
     */
    public interface RetryListener {
        void onRetry();
    }
} 