package com.example.todolist.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todolist.R;
import com.example.todolist.ai.TaskDecomposer;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.TaskGroup;
import com.example.todolist.data.TaskGroupDao;
import com.example.todolist.data.Todo;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TaskGroupsFragment extends Fragment {
    private static final String TAG = "TaskGroupsFragment";
    private RecyclerView recyclerViewGroups;
    private View emptyViewGroups;
    private TaskDao taskDao;
    private TaskGroupDao taskGroupDao;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private TaskGroupAdapter taskGroupAdapter;
    private List<TaskGroup> allTaskGroups = new ArrayList<>();

    // 代办集点击监听接口
    public interface OnItemClickListener {
        void onItemClick(TaskGroup taskGroup);
    }

    public TaskGroupsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_task_groups, container, false);
        
        try {
            // 初始化DAO和Firebase
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
            taskGroupDao = AppDatabase.getInstance(requireContext()).taskGroupDao();
            auth = FirebaseAuth.getInstance();
            firestore = FirebaseFirestore.getInstance();
            
            // 初始化UI组件
            recyclerViewGroups = view.findViewById(R.id.recyclerViewGroups);
            recyclerViewGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
            emptyViewGroups = view.findViewById(R.id.emptyViewGroups);
            
            // 设置代办集列表适配器
            taskGroupAdapter = new TaskGroupAdapter(requireContext(), allTaskGroups);
            recyclerViewGroups.setAdapter(taskGroupAdapter);
            
            // 设置代办集点击事件
            taskGroupAdapter.setOnItemClickListener(taskGroup -> {
                Intent intent = new Intent(requireContext(), TaskGroupActivity.class);
                intent.putExtra("group_id", taskGroup.id);
                startActivity(intent);
            });
            
            // 添加待办集按钮
            FloatingActionButton fabAddGroup = view.findViewById(R.id.fabAddGroup);
            fabAddGroup.setOnClickListener(v -> {
                // 显示选择对话框：直接创建待办集或使用AI分解
                new AlertDialog.Builder(requireContext())
                    .setTitle("创建代办集")
                    .setItems(new String[]{"手动创建", "AI任务分解"}, (dialog, which) -> {
                        if (which == 0) {
                            // 手动创建代办集
                            Intent addIntent = new Intent(requireContext(), AddEditTaskActivity.class);
                            addIntent.putExtra("task_group_mode", true);
                            startActivity(addIntent);
                        } else {
                            // AI任务分解
                            showCreateTaskGroupDialog();
                        }
                    })
                    .show();
            });

            // 加载代办集数据
            loadTaskGroups();
            
        } catch (Exception e) {
            Log.e(TAG, "UI初始化失败", e);
            Toast.makeText(requireContext(), "初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadTaskGroups();
    }
    
    private void loadTaskGroups() {
        try {
            new Thread(() -> {
                try {
                    Log.d(TAG, "正在加载所有代办集...");
                    List<TaskGroup> groups = taskGroupDao != null ? taskGroupDao.getAllTaskGroups() : new ArrayList<>();
                    if (groups == null) {
                        Log.e(TAG, "代办集查询返回 null");
                        groups = new ArrayList<>();
                    }
                    Log.d(TAG, "成功加载 " + groups.size() + " 个代办集");
                    allTaskGroups.clear();
                    allTaskGroups.addAll(groups);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            taskGroupAdapter.notifyDataSetChanged();
                            // 根据是否有数据显示空视图
                            if (allTaskGroups.isEmpty()) {
                                recyclerViewGroups.setVisibility(View.GONE);
                                emptyViewGroups.setVisibility(View.VISIBLE);
                            } else {
                                recyclerViewGroups.setVisibility(View.VISIBLE);
                                emptyViewGroups.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "在加载代办集中发生异常", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "加载代办集执行失败", e);
        }
    }
    
    /**
     * 显示创建待办集的对话框
     */
    private void showCreateTaskGroupDialog() {
        // 创建输入对话框
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_task_group, null);
        EditText editTaskTitle = dialogView.findViewById(R.id.editTaskGroupTitle);
        
        // 找到提示文本并设置内容
        TextView textTaskHint = dialogView.findViewById(R.id.textTaskHint);
        if (textTaskHint != null) {
            textTaskHint.setText("输入您想要分解的任务。系统会根据任务复杂度自动分解：\n" +
                    "• 简单任务：分解为2-3个子任务\n" +
                    "• 中等任务：分解为4-6个子任务\n" +
                    "• 复杂任务：分解为7-10个子任务");
            textTaskHint.setVisibility(View.VISIBLE);
        }
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle("创建代办集")
            .setView(dialogView)
            .setPositiveButton("AI分解任务", null) // 设为null，后面自定义点击事件
            .setNegativeButton("取消", null)
            .create();
        
        // 显示对话框
        dialog.show();
        
        // 自定义正向按钮点击事件，防止输入为空时自动关闭对话框
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String taskTitle = editTaskTitle.getText().toString().trim();
            if (TextUtils.isEmpty(taskTitle)) {
                Toast.makeText(requireContext(), "请输入任务标题", Toast.LENGTH_SHORT).show();
                return; // 不关闭对话框
            }
            
            // 关闭对话框
            dialog.dismiss();
            
            // 执行AI任务分解
            new DecomposeTaskAsyncTask().execute(taskTitle);
        });
    }
    
    /**
     * 显示任务分解结果对话框
     */
    private void showDecompositionResultDialog(TaskDecomposer.DecompositionResult result) {
        // 创建子任务列表的视图
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task_decomposition, null);
        TextView textMainTask = dialogView.findViewById(R.id.textMainTask);
        TextView textCategory = dialogView.findViewById(R.id.textCategory);
        TextView textEstimatedDays = dialogView.findViewById(R.id.textEstimatedDays);
        RecyclerView recyclerSubTasks = dialogView.findViewById(R.id.recyclerSubTasks);
        
        // 设置主任务信息
        textMainTask.setText(result.getMainTask());
        textCategory.setText("推荐类别: " + result.getCategory());
        
        // 根据任务天数判断难度
        String complexity = "简单任务";
        if (result.getEstimatedDays() > 5) {
            complexity = "复杂任务";
        } else if (result.getEstimatedDays() > 2) {
            complexity = "中等难度任务";
        }
        textEstimatedDays.setText(String.format("预计天数: %d天（%s）", 
                result.getEstimatedDays(), complexity));
        
        // 设置子任务列表
        recyclerSubTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        SubTaskAdapter adapter = new SubTaskAdapter(result.getSubTasks());
        recyclerSubTasks.setAdapter(adapter);
        
        // 创建并显示对话框
        String dialogTitle = String.format("任务分解结果（%d个子任务）", result.getSubTasks().size());
        new AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton("保存为代办集", (dialog, which) -> {
                // 创建代办集和子任务
                saveAsTaskGroup(result);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 保存为代办集
     */
    private void saveAsTaskGroup(TaskDecomposer.DecompositionResult result) {
        // 创建代办集
        String groupId = UUID.randomUUID().toString();
        TaskGroup taskGroup = new TaskGroup(
            groupId,
            result.getMainTask(),
            result.getCategory(),
            result.getEstimatedDays()
        );
        
        // 保存代办集
        new Thread(() -> {
            taskGroupDao.insertTaskGroup(taskGroup);
            
            // 设置子任务的默认值
            String category = result.getCategory();
            if (TextUtils.isEmpty(category) || !isValidCategory(category)) {
                category = "其他"; // 默认类别
            }
            
            // 设置第一个子任务的开始时间为当前时间
            Calendar taskCalendar = Calendar.getInstance();
            
            // 为每个子任务创建新的Todo对象并保存
            final String finalCategory = category;
            for (TaskDecomposer.SubTask subTask : result.getSubTasks()) {
                String subTaskId = UUID.randomUUID().toString();
                Todo newTask = new Todo(
                    subTaskId,
                    subTask.getTitle(),
                    taskCalendar.getTimeInMillis(),
                    "", // 地点为空
                    finalCategory,
                    false
                );
                // 重要：标记这个任务属于代办集，不应显示在主页面
                newTask.updatedAt = System.currentTimeMillis();
                newTask.belongsToTaskGroup = true;  // 标记为属于代办集
                
                // 将任务时间递增3小时，让子任务时间有序排列
                taskCalendar.add(Calendar.HOUR_OF_DAY, 3);
                
                // 保存到本地数据库
                taskDao.insertTodo(newTask);
                
                // 添加到代办集
                taskGroup.addSubTask(newTask.id);
                
                // 同步到云端
                if (auth.getCurrentUser() != null) {
                    firestore.collection("users")
                            .document(auth.getCurrentUser().getUid())
                            .collection("tasks").document(newTask.id)
                            .set(newTask);
                }
            }
            
            // 更新代办集
            taskGroupDao.insertTaskGroup(taskGroup);
            
            // 同步代办集到云端
            if (auth.getCurrentUser() != null) {
                firestore.collection("users")
                        .document(auth.getCurrentUser().getUid())
                        .collection("taskgroups").document(taskGroup.id)
                        .set(taskGroup);
            }
            
            // 刷新界面
            getActivity().runOnUiThread(() -> {
                loadTaskGroups();
                Toast.makeText(requireContext(), "已创建代办集：" + result.getMainTask(), Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
    
    /**
     * 检查类别是否在预定义的类别列表中
     */
    private boolean isValidCategory(String category) {
        String[] validCategories = {"工作", "个人", "学习", "其他"};
        for (String valid : validCategories) {
            if (valid.equalsIgnoreCase(category)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 代办集适配器
     */
    private class TaskGroupAdapter extends RecyclerView.Adapter<TaskGroupAdapter.ViewHolder> {
        private Context context;
        private List<TaskGroup> taskGroups;
        private OnItemClickListener listener;

        // 接口定义在外部，避免内部类嵌套导致的编译问题
        public void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        TaskGroupAdapter(Context context, List<TaskGroup> taskGroups) {
            this.context = context;
            this.taskGroups = taskGroups;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_task_group, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            TaskGroup taskGroup = taskGroups.get(position);
            holder.textTitle.setText(taskGroup.title);
            holder.textCategory.setText(taskGroup.category);
            
            // 根据类别设置不同颜色
            int backgroundColor;
            switch (taskGroup.category) {
                case "工作":
                    backgroundColor = ContextCompat.getColor(context, R.color.category_work);
                    break;
                case "学习":
                    backgroundColor = ContextCompat.getColor(context, R.color.category_study);
                    break;
                case "个人":
                    backgroundColor = ContextCompat.getColor(context, R.color.category_personal);
                    break;
                case "健康":
                    backgroundColor = ContextCompat.getColor(context, R.color.category_health);
                    break;
                default:
                    backgroundColor = ContextCompat.getColor(context, R.color.category_other);
                    break;
            }
            
            // 设置类别标签背景颜色
            holder.textCategory.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
            holder.textCategory.setTextColor(ContextCompat.getColor(context, R.color.white));
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            holder.textCreatedAt.setText(sdf.format(taskGroup.createdAt));
            
            int completedTasks = 0;
            int totalTasks = taskGroup.subTaskIds.size();
            holder.textProgress.setText(String.format("%d/%d 已完成", completedTasks, totalTasks));
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(taskGroup);
                }
            });
        }

        @Override
        public int getItemCount() {
            return taskGroups.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textTitle;
            TextView textCategory;
            TextView textCreatedAt;
            TextView textProgress;

            ViewHolder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textGroupTitle);
                textCategory = itemView.findViewById(R.id.textGroupCategory);
                textCreatedAt = itemView.findViewById(R.id.textGroupCreatedAt);
                textProgress = itemView.findViewById(R.id.textGroupProgress);
            }
        }
    }
    
    /**
     * 子任务列表适配器
     */
    private class SubTaskAdapter extends RecyclerView.Adapter<SubTaskAdapter.ViewHolder> {
        private List<TaskDecomposer.SubTask> subTasks;
        
        public SubTaskAdapter(List<TaskDecomposer.SubTask> subTasks) {
            this.subTasks = subTasks;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subtask, parent, false);
            return new ViewHolder(itemView);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            TaskDecomposer.SubTask task = subTasks.get(position);
            holder.textTitle.setText(task.getTitle());
            holder.textDescription.setText(task.getDescription());
            holder.textEstimatedHours.setText(String.format("预计耗时: %.1f小时", task.getEstimatedHours()));
        }
        
        @Override
        public int getItemCount() {
            return subTasks.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textTitle;
            TextView textDescription;
            TextView textEstimatedHours;
            
            ViewHolder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textSubtaskTitle);
                textDescription = itemView.findViewById(R.id.textSubtaskDescription);
                textEstimatedHours = itemView.findViewById(R.id.textSubtaskTime);
            }
        }
    }
    
    /**
     * 后台任务分解异步任务
     */
    private class DecomposeTaskAsyncTask extends AsyncTask<String, Void, TaskDecomposer.DecompositionResult> {
        private ProgressDialog progressDialog;
        private Exception error;
        
        @Override
        protected void onPreExecute() {
            // 显示进度对话框
            progressDialog = new ProgressDialog(requireContext());
            progressDialog.setMessage("正在分解任务...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        
        @Override
        protected TaskDecomposer.DecompositionResult doInBackground(String... params) {
            try {
                String taskTitle = params[0];
                return TaskDecomposer.decomposeTask(taskTitle);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "任务分解失败", e);
                error = e;
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(TaskDecomposer.DecompositionResult result) {
            // 关闭进度对话框
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            
            if (result != null) {
                showDecompositionResultDialog(result);
            } else {
                String errorMsg = "未知错误";
                if (error != null) {
                    errorMsg = error.getMessage();
                    
                    // 对OpenRouter API错误进行特殊处理
                    if (errorMsg.contains("API返回错误: Internal Server Error") || 
                        errorMsg.contains("500")) {
                        errorMsg = "AI服务器暂时不可用，请稍后再试";
                    } else if (errorMsg.contains("API响应中缺少choices字段")) {
                        errorMsg = "AI服务返回格式异常，请稍后再试";
                    } else if (errorMsg.contains("无法从API响应中提取有效的JSON数据")) {
                        errorMsg = "AI返回数据格式错误，请稍后再试";
                    } else if (errorMsg.contains("timed out")) {
                        errorMsg = "AI服务连接超时，请检查网络后重试";
                    }
                }
                
                // 显示错误提示
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle("任务分解失败")
                       .setMessage("抱歉，无法分解任务：" + errorMsg)
                       .setPositiveButton("重试", (dialog, which) -> {
                           // 如果输入框还存在，重新尝试
                           View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_task_group, null);
                           EditText editTaskGroupTitle = dialogView.findViewById(R.id.editTaskGroupTitle);
                           String lastTitle = editTaskGroupTitle.getText().toString().trim();
                           if (!lastTitle.isEmpty()) {
                               new DecomposeTaskAsyncTask().execute(lastTitle);
                           } else {
                               showCreateTaskGroupDialog();
                           }
                       })
                       .setNegativeButton("取消", null)
                       .show();
                
                Log.e(TAG, "任务分解失败: " + (error != null ? error.toString() : "未知错误"), error);
            }
        }
    }
} 