package com.example.todolist.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络状态监听器
 * 用于监听网络状态变化并通知观察者
 */
public class NetworkStateMonitor {
    private static final String TAG = "NetworkStateMonitor";
    
    private static NetworkStateMonitor instance;
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private NetworkReceiver networkReceiver;
    private boolean isRegistered = false;
    private boolean isNetworkAvailable = false;
    
    // 网络状态变化监听器列表
    private final List<NetworkStateListener> listeners = new ArrayList<>();
    
    // 私有构造函数
    private NetworkStateMonitor(Context context) {
        this.context = context.getApplicationContext();
        connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        checkNetworkState();
    }
    
    // 单例模式获取实例
    public static synchronized NetworkStateMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkStateMonitor(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 开始监听网络状态变化
     */
    public void startMonitoring() {
        try {
            if (isRegistered) {
                return;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0及以上使用NetworkCallback
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        Log.d(TAG, "网络连接可用");
                        updateNetworkState(true);
                    }
                    
                    @Override
                    public void onLost(Network network) {
                        Log.d(TAG, "网络连接丢失");
                        updateNetworkState(false);
                    }
                };
                
                NetworkRequest networkRequest = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            } else {
                // Android 7.0以下使用BroadcastReceiver
                networkReceiver = new NetworkReceiver();
                IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
                context.registerReceiver(networkReceiver, filter);
            }
            
            isRegistered = true;
            Log.d(TAG, "网络状态监听已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动网络状态监听失败", e);
        }
    }
    
    /**
     * 停止监听网络状态变化
     */
    public void stopMonitoring() {
        try {
            if (!isRegistered) {
                return;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            } else if (networkReceiver != null) {
                context.unregisterReceiver(networkReceiver);
                networkReceiver = null;
            }
            
            isRegistered = false;
            Log.d(TAG, "网络状态监听已停止");
        } catch (Exception e) {
            Log.e(TAG, "停止网络状态监听失败", e);
        }
    }
    
    /**
     * 添加网络状态监听器
     * @param listener 监听器
     */
    public void addListener(NetworkStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除网络状态监听器
     * @param listener 监听器
     */
    public void removeListener(NetworkStateListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 检查当前网络状态
     * @return 网络是否可用
     */
    public boolean isNetworkAvailable() {
        try {
            checkNetworkState();
            return isNetworkAvailable;
        } catch (Exception e) {
            Log.e(TAG, "检查网络状态失败", e);
            return false;
        }
    }
    
    /**
     * 检查当前网络状态
     */
    private void checkNetworkState() {
        try {
            if (connectivityManager == null) {
                isNetworkAvailable = false;
                return;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0及以上
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                        connectivityManager.getActiveNetwork());
                
                isNetworkAvailable = capabilities != null && (
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                // Android 6.0以下
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                isNetworkAvailable = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "获取网络状态失败", e);
            isNetworkAvailable = false;
        }
    }
    
    /**
     * 更新网络状态并通知监听器
     * @param available 网络是否可用
     */
    private void updateNetworkState(boolean available) {
        // 状态没变化，不通知
        if (isNetworkAvailable == available) {
            return;
        }
        
        isNetworkAvailable = available;
        notifyListeners();
    }
    
    /**
     * 通知所有监听器网络状态变化
     */
    private void notifyListeners() {
        try {
            for (NetworkStateListener listener : new ArrayList<>(listeners)) {
                if (isNetworkAvailable) {
                    listener.onNetworkAvailable();
                } else {
                    listener.onNetworkLost();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "通知网络状态变化失败", e);
        }
    }
    
    /**
     * 广播接收器，用于接收网络状态变化广播
     */
    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                checkNetworkState();
                notifyListeners();
            }
        }
    }
    
    /**
     * 网络状态变化监听器接口
     */
    public interface NetworkStateListener {
        /**
         * 网络变为可用时调用
         */
        void onNetworkAvailable();
        
        /**
         * 网络变为不可用时调用
         */
        void onNetworkLost();
    }
} 