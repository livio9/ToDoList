package com.example.todolist.ui;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.Todo;
import com.example.todolist.R;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.Calendar;
import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;
import android.graphics.Color;

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
        if (todo.completed) {
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_completed));
        } else {
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            // 判断是否已过期，过期任务显示红色
            long now = System.currentTimeMillis();
            if (todo.time < now) {
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_overdue));
            } else {
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_pending));
            }
        }
        
        // 格式化任务时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date(todo.time));
        
        // 设置详细信息
        StringBuilder details = new StringBuilder(timeStr);
        if (todo.place != null && !todo.place.isEmpty()) {
            details.append(" | ").append(todo.place);
        }
        holder.textDetails.setText(details.toString());
        
        // 设置分类芯片
        if (todo.category != null && !todo.category.isEmpty()) {
            holder.chipCategory.setVisibility(View.VISIBLE);
            handleCategoryChip(holder.chipCategory, todo.category);
        } else {
            holder.chipCategory.setVisibility(View.GONE);
        }
        
        holder.checkDone.setChecked(todo.completed);

        // 复选框勾选事件：更新完成状态
        holder.checkDone.setOnClickListener(v -> {
            boolean newStatus = holder.checkDone.isChecked();
            // 更新标题的样式
            if (newStatus) {
                holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_completed));
            } else {
                holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                // 判断是否已过期
                long now = System.currentTimeMillis();
                if (todo.time < now) {
                    holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_overdue));
                } else {
                    holder.textTitle.setTextColor(context.getResources().getColor(R.color.task_pending));
                }
            }
            
            // 更新数据对象并异步保存到本地数据库
            todo.completed = newStatus;
            new Thread(() -> {
                AppDatabase.getInstance(context).taskDao().updateTodo(todo);
            }).start();
            
            // 同步更新云端任务的完成状态字段
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                FirebaseFirestore.getInstance().collection("users")
                        .document(auth.getCurrentUser().getUid())
                        .collection("tasks").document(todo.id)
                        .update("completed", newStatus);
            }
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

    @Override
    public int getItemCount() {
        return taskList != null ? taskList.size() : 0;
    }

    // 自定义 ViewHolder，持有每个列表项的子视图
    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCheckBox checkDone;
        TextView textTitle;
        TextView textDetails;
        TextView textPlace;
        Chip chipCategory;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkDone = itemView.findViewById(R.id.checkDone);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDetails = itemView.findViewById(R.id.textDetails);
            textPlace = itemView.findViewById(R.id.textPlace);
            chipCategory = itemView.findViewById(R.id.chipCategory);
        }
    }

    // 添加handleCategoryChip方法，根据不同类别设置不同颜色
    private void handleCategoryChip(Chip chip, String category) {
        Context context = chip.getContext();
        int backgroundColor;
        
        switch (category) {
            case "工作":
                backgroundColor = ContextCompat.getColor(context, R.color.category_work);
                break;
            case "个人":
                backgroundColor = ContextCompat.getColor(context, R.color.category_personal);
                break;
            case "其他":
                backgroundColor = ContextCompat.getColor(context, R.color.category_other);
                break;
            default:
                backgroundColor = ContextCompat.getColor(context, R.color.primary);
                break;
        }
        
        chip.setChipBackgroundColor(ColorStateList.valueOf(backgroundColor));
        chip.setText(category);
    }
}
