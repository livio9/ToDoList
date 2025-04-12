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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Date;

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
            holder.textTitle.setTextColor(0xFF888888);  // 灰色
        } else {
            holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.textTitle.setTextColor(0xFF000000);  // 黑色
        }
        // 格式化任务时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date(todo.time));
        // 组合详细信息字符串: 时间 | 地点 | 类别
        String details = timeStr;
        if (todo.place != null && !todo.place.isEmpty()) {
            details += " | " + todo.place;
        }
        if (todo.category != null && !todo.category.isEmpty()) {
            details += " | " + todo.category;
        }
        holder.textDetails.setText(details);
        holder.checkDone.setChecked(todo.completed);

        // 复选框勾选事件：更新完成状态
        holder.checkDone.setOnClickListener(v -> {
            boolean newStatus = holder.checkDone.isChecked();
            // 更新标题的样式
            if (newStatus) {
                holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.textTitle.setTextColor(0xFF888888);
            } else {
                holder.textTitle.setPaintFlags(holder.textTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                holder.textTitle.setTextColor(0xFF000000);
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
        CheckBox checkDone;
        TextView textTitle;
        TextView textDetails;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkDone = itemView.findViewById(R.id.checkDone);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDetails = itemView.findViewById(R.id.textDetails);
        }
    }
}
