package com.example.todolist.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.example.todolist.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.UUID;

public class AddEditTaskActivity extends AppCompatActivity {
    private EditText editTitle;
    private EditText editPlace;
    private Spinner spinnerCategory;
    private TextView textDateTime;
    private CheckBox checkCompleted;
    private Button buttonSave;
    private Button buttonDelete;
    private TaskDao taskDao;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private Todo currentTodo;         // 编辑模式下传入的任务对象
    private Calendar selectedCalendar; // 选定的日期时间

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_task);
        // 初始化 DAO 和 Firebase 引用
        taskDao = AppDatabase.getInstance(getApplicationContext()).taskDao();
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        // 获取界面控件引用
        editTitle = findViewById(R.id.editTitle);
        editPlace = findViewById(R.id.editPlace);
        spinnerCategory = findViewById(R.id.spinnerCategoryInput);
        textDateTime = findViewById(R.id.textDateTime);
        checkCompleted = findViewById(R.id.checkCompleted);
        buttonSave = findViewById(R.id.buttonSave);
        buttonDelete = findViewById(R.id.buttonDelete);
        // 设置类别下拉框选项
        String[] categories = {"工作", "个人", "其他"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        // 判断编辑还是新增
        currentTodo = (Todo) getIntent().getSerializableExtra("todo");
        if (currentTodo != null) {
            editTitle.setText(currentTodo.title);
            editPlace.setText(currentTodo.place);
            checkCompleted.setChecked(currentTodo.completed);
            int catIndex = 0;
            for (int i = 0; i < categories.length; i++) {
                if (categories[i].equals(currentTodo.category)) {
                    catIndex = i;
                    break;
                }
            }
            spinnerCategory.setSelection(catIndex);
            selectedCalendar = Calendar.getInstance();
            selectedCalendar.setTimeInMillis(currentTodo.time);
            updateDateTimeText();
            buttonDelete.setVisibility(Button.VISIBLE);
        } else {
            currentTodo = null;
            selectedCalendar = Calendar.getInstance();
            updateDateTimeText();
            checkCompleted.setChecked(false);
            buttonDelete.setVisibility(Button.GONE);
        }

        textDateTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedCalendar != null) {
                cal = selectedCalendar;
            }
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            Calendar finalCal = cal;
            new DatePickerDialog(AddEditTaskActivity.this, (view, year1, month1, dayOfMonth) -> {
                selectedCalendar.set(Calendar.YEAR, year1);
                selectedCalendar.set(Calendar.MONTH, month1);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                int hour = finalCal.get(Calendar.HOUR_OF_DAY);
                int minute = finalCal.get(Calendar.MINUTE);
                new TimePickerDialog(AddEditTaskActivity.this, (view1, hourOfDay, minute1) -> {
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedCalendar.set(Calendar.MINUTE, minute1);
                    selectedCalendar.set(Calendar.SECOND, 0);
                    selectedCalendar.set(Calendar.MILLISECOND, 0);
                    updateDateTimeText();
                }, hour, minute, true).show();
            }, year, month, day).show();
        });

        buttonSave.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String place = editPlace.getText().toString().trim();
            String category = spinnerCategory.getSelectedItem().toString();
            boolean completed = checkCompleted.isChecked();
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(AddEditTaskActivity.this, "标题不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            long time = selectedCalendar.getTimeInMillis();
            if (currentTodo == null) {
                String newId = UUID.randomUUID().toString();
                currentTodo = new Todo(newId, title, time, place, category, completed);
            } else {
                currentTodo.title = title;
                currentTodo.place = place;
                currentTodo.category = category;
                currentTodo.time = time;
                currentTodo.completed = completed;
            }
            // 每次保存更新更新时间，并确保标记未删除
            currentTodo.updatedAt = System.currentTimeMillis();
            currentTodo.deleted = false;
            new Thread(() -> taskDao.insertTodo(currentTodo)).start();

            // 同步到云端
            if (auth.getCurrentUser() != null) {
                firestore.collection("users")
                        .document(auth.getCurrentUser().getUid())
                        .collection("tasks").document(currentTodo.id)
                        .set(currentTodo);
            }
            Toast.makeText(AddEditTaskActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });

        // 删除操作：采用软删除
        buttonDelete.setOnClickListener(v -> {
            if (currentTodo != null) {
                currentTodo.deleted = true;
                currentTodo.updatedAt = System.currentTimeMillis();
                new Thread(() -> taskDao.insertTodo(currentTodo)).start();
                if (auth.getCurrentUser() != null) {
                    firestore.collection("users")
                            .document(auth.getCurrentUser().getUid())
                            .collection("tasks").document(currentTodo.id)
                            .update("deleted", true, "updatedAt", currentTodo.updatedAt);
                }
                Toast.makeText(AddEditTaskActivity.this, "任务已删除", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    private void updateDateTimeText() {
        if (selectedCalendar == null) {
            selectedCalendar = Calendar.getInstance();
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        String formatted = sdf.format(selectedCalendar.getTime());
        textDateTime.setText(formatted);
    }
}
