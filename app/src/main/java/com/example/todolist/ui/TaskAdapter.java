package com.example.todolist.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.Todo;
import com.example.todolist.R;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.parse.ParseObject;
import com.parse.ParseUser;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.Calendar;
import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.Toast;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
    private List<Todo> taskList;
    private LayoutInflater inflater;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;
    private Context context;

    // 列表项点击事件接口
    public interface OnItemClickListener {
        void onItemClick(Todo todo);
    }
    public interface OnItemLongClickListener {
        void onItemLongClick(Todo todo);
    }

    public TaskAdapter(Context context, List<Todo> taskList) {
        this.context = context;
        this.taskList = taskList;
        this.inflater = LayoutInflater.from(context);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.itemLongClickListener = listener;
    }

    // 更新列表数据并刷新
    public void updateList(List<Todo> newList) {
        this.taskList = newList;
        notifyDataSetChanged();
    }
    
    // 添加updateData方法，与updateList功能相同
    public void updateData(List<Todo> newList) {
        this.taskList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = inflater.inflate(R.layout.item_todo, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Todo todo = taskList.get(position);
        holder.textTitle.setText(todo.title);
        
        // 根据完成状态设置标题文字效果（删除线/正常）
        updateTitleAppearance(holder, todo.completed, todo.time);
        
        // 格式化任务时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date(todo.time));
        
        // 设置时间和地点
        holder.textTime.setText(timeStr);
        
        if (todo.place != null && !todo.place.isEmpty()) {
            holder.textPlace.setText(todo.place);
            holder.layoutPlace.setVisibility(View.VISIBLE);
        } else {
            holder.layoutPlace.setVisibility(View.GONE);
        }
        
        // 设置分类标签
        if (todo.category != null && !todo.category.isEmpty()) {
            holder.textCategory.setVisibility(View.VISIBLE);
            handleCategoryChip(holder.textCategory, todo.category);
        } else {
            holder.textCategory.setVisibility(View.GONE);
        }
        
        // 设置番茄时钟图标
        if (todo.pomodoroEnabled != null && todo.pomodoroEnabled) {
            holder.imagePomodoroTimer.setVisibility(View.VISIBLE);
            // 添加点击事件
            holder.imagePomodoroTimer.setOnClickListener(v -> {
                // 启动番茄定时器
                if (context instanceof AddEditTaskActivity) {
                    ((AddEditTaskActivity) context).showPomodoroTimer(todo);
                } else if (context instanceof MainActivity) {
                    // 如果在主界面启动，需要先创建对话框
                    PomodoroTimerDialog dialog = new PomodoroTimerDialog(context, todo);
                    dialog.setOnTimerCompletedListener(isBreakCompleted -> {
                        if (isBreakCompleted) {
                            Toast.makeText(context, "休息结束，开始新的专注", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "专注完成，开始休息", Toast.LENGTH_SHORT).show();
                        }
                    });
                    dialog.show();
                }
            });
        } else {
            holder.imagePomodoroTimer.setVisibility(View.GONE);
        }
        
        holder.checkCompleted.setChecked(todo.completed);

        // 复选框勾选事件：更新完成状态，并添加动画效果
        holder.checkCompleted.setOnClickListener(v -> {
            boolean newStatus = holder.checkCompleted.isChecked();
            
            // 添加动画效果
            if (newStatus) {
                // 勾选时的动画
                animateTaskCompletion(holder);
            } else {
                // 取消勾选时的动画
                animateTaskUncomplete(holder);
            }
            
            // 更新数据对象并异步保存到本地数据库
            todo.completed = newStatus;
            new Thread(() -> {
                AppDatabase.getInstance(context).taskDao().updateTodo(todo);
            }).start();
        });

        // 列表项点击：触发监听回调（打开编辑）
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(todo);
            }
        });
        
        // 列表项长按：触发监听回调（删除）
        holder.itemView.setOnLongClickListener(v -> {
            if (itemLongClickListener != null) {
                itemLongClickListener.onItemLongClick(todo);
            }
            return true;
        });
    }

    // 更新标题外观的辅助方法
    private void updateTitleAppearance(@NonNull ViewHolder holder, boolean completed, long taskTime) {
        if (completed) {
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_completed));
        } else {
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            // 判断是否已过期，过期任务显示红色
            long now = System.currentTimeMillis();
            if (taskTime < now) {
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_overdue));
            } else {
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.text_primary));
            }
        }
    }
    
    // 任务完成动画效果
    private void animateTaskCompletion(@NonNull ViewHolder holder) {
        // 添加删除线动画
        ValueAnimator strikeAnimator = ValueAnimator.ofInt(0, holder.textTitle.getText().length());
        strikeAnimator.setDuration(300);
        strikeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        strikeAnimator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_completed));
            holder.textTitle.invalidate();
        });
        strikeAnimator.start();
        
        // 添加淡出动画
        ValueAnimator alphaAnimator = ValueAnimator.ofFloat(1.0f, 0.7f);
        alphaAnimator.setDuration(300);
        alphaAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        alphaAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            holder.itemView.setAlpha(alpha);
        });
        alphaAnimator.start();
    }
    
    // 任务取消完成动画效果
    private void animateTaskUncomplete(@NonNull ViewHolder holder) {
        // 添加恢复动画
        ValueAnimator alphaAnimator = ValueAnimator.ofFloat(0.7f, 1.0f);
        alphaAnimator.setDuration(300);
        alphaAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        alphaAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            holder.itemView.setAlpha(alpha);
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            
            // 判断是否已过期，过期任务显示红色
            Todo todo = taskList.get(holder.getAdapterPosition());
            long now = System.currentTimeMillis();
            if (todo.time < now) {
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_overdue));
            } else {
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.text_primary));
            }
            holder.textTitle.invalidate();
        });
        alphaAnimator.start();
    }

    @Override
    public int getItemCount() {
        return taskList != null ? taskList.size() : 0;
    }

    // 自定义 ViewHolder，持有每个列表项的子视图
    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCheckBox checkCompleted; // 改为MaterialCheckBox
        TextView textTitle;
        TextView textTime;
        TextView textPlace;
        TextView textCategory;
        View layoutPlace;
        View imagePomodoroTimer;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkCompleted = itemView.findViewById(R.id.checkBoxCompleted);
            textTitle = itemView.findViewById(R.id.textViewTitle);
            textTime = itemView.findViewById(R.id.textViewTime);
            textPlace = itemView.findViewById(R.id.textViewPlace);
            textCategory = itemView.findViewById(R.id.textViewCategory);
            layoutPlace = itemView.findViewById(R.id.layoutPlace);
            imagePomodoroTimer = itemView.findViewById(R.id.imagePomodoroTimer);
        }
    }

    // 添加handleCategoryChip方法，根据不同类别设置不同颜色
    private void handleCategoryChip(TextView textView, String category) {
        Context context = textView.getContext();
        int backgroundColor;
        
        switch (category) {
            case "工作":
                backgroundColor = ContextCompat.getColor(context, R.color.category_work);
                break;
            case "个人":
                backgroundColor = ContextCompat.getColor(context, R.color.category_personal);
                break;
            case "学习":
                backgroundColor = ContextCompat.getColor(context, R.color.category_study);
                break;
            case "健康":
                backgroundColor = ContextCompat.getColor(context, R.color.category_health);
                break;
            case "其他":
                backgroundColor = ContextCompat.getColor(context, R.color.category_other);
                break;
            default:
                backgroundColor = ContextCompat.getColor(context, R.color.primary);
                break;
        }
        
        textView.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        textView.setText(category);
    }
}
