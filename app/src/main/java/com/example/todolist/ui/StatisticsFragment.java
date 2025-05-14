package com.example.todolist.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.todolist.R;
import com.example.todolist.data.AppDatabase;
import com.example.todolist.data.TaskDao;
import com.example.todolist.data.Todo;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsFragment extends Fragment {
    private static final String TAG = "StatisticsFragment";
    
    // 任务总览
    private TextView textTotalTasks;
    private TextView textCompletedTasks;
    private TextView textPendingTasks;
    
    // 完成率
    private TextView textCompletionRate;
    private ProgressBar progressCompletionRate;
    
    // 图表
    private PieChart pieChart;
    private BarChart barChart;
    private LineChart lineChart;

    private TaskDao taskDao;

    public StatisticsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);
        
        try {
            // 初始化数据库
            taskDao = AppDatabase.getInstance(requireContext()).taskDao();
            
            // 初始化UI组件
            // 任务总览
            textTotalTasks = view.findViewById(R.id.textTotalTasks);
            textCompletedTasks = view.findViewById(R.id.textCompletedTasks);
            textPendingTasks = view.findViewById(R.id.textPendingTasks);
            
            // 完成率
            textCompletionRate = view.findViewById(R.id.textCompletionRate);
            progressCompletionRate = view.findViewById(R.id.progressCompletionRate);
            
            // 初始化图表
            initCharts(view);
            
            // 加载统计数据
            loadStatistics();
            
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
        }
        
        return view;
    }

    private void initCharts(View view) {
        // 初始化饼图
        pieChart = new PieChart(requireContext());
        ViewGroup pieContainer = (ViewGroup) view.findViewById(R.id.pieChartContainer);
        pieContainer.addView(pieChart);
        setupPieChart();

        // 初始化柱状图
        barChart = new BarChart(requireContext());
        ViewGroup barContainer = (ViewGroup) view.findViewById(R.id.barChartContainer);
        barContainer.addView(barChart);
        setupBarChart();

        // 初始化折线图
        lineChart = new LineChart(requireContext());
        ViewGroup lineContainer = (ViewGroup) view.findViewById(R.id.lineChartContainer);
        lineContainer.addView(lineChart);
        setupLineChart();
    }

    private void setupPieChart() {
        pieChart.setDescription(null);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setDrawCenterText(true);
        pieChart.setRotationAngle(0);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.animateY(1000);
    }

    private void setupBarChart() {
        barChart.setDescription(null);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        
        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.animateY(1000);
    }

    private void setupLineChart() {
        lineChart.setDescription(null);
        lineChart.setDrawGridBackground(false);
        lineChart.setDrawBorders(false);
        
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.animateX(1000);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadStatistics();
    }
    
    private void loadStatistics() {
        try {
            new Thread(() -> {
                try {
                    String currentUserId = CurrentUserUtil.getCurrentUserId();
                    // 从数据库加载所有非删除的任务
                    List<Todo> tasks = taskDao.getVisibleTasksForUser();

                    // 计算统计信息
                    final int totalTasks = tasks.size();
                    final int completedTasks = (int) tasks.stream().filter(task -> task.completed).count();
                    final int pendingTasks = totalTasks - completedTasks;
                    
                    // 计算完成率
                    final int completionRate = totalTasks > 0 ? (completedTasks * 100) / totalTasks : 0;
                    
                    // 计算分类统计
                    Map<String, Integer> categoryCount = new HashMap<>();
                    for (Todo task : tasks) {
                        String category = task.category != null ? task.category : "其他";
                        categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
                    }
                    
                    // 获取近5天的日期
                    Calendar calendar = Calendar.getInstance();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
                    String[] dates = new String[5];
                    for (int i = 4; i >= 0; i--) {
                        calendar.setTime(new Date());
                        calendar.add(Calendar.DAY_OF_MONTH, -i);
                        dates[4-i] = dateFormat.format(calendar.getTime());
                    }
                    
                    // 计算每日专注用时（使用番茄钟分钟数）
                    Map<String, Float> dailyFocusTime = new HashMap<>();
                    for (Todo task : tasks) {
                        if (task.time > 0) {
                            String date = dateFormat.format(new Date(task.time));
                            if (dailyFocusTime.containsKey(date)) {
                                dailyFocusTime.put(date, dailyFocusTime.get(date) + task.pomodoroMinutes);
                            } else {
                                dailyFocusTime.put(date, (float)task.pomodoroMinutes);
                            }
                        }
                    }
                    
                    // 计算每日完成任务数（使用任务时间）
                    Map<String, Integer> dailyCompletedTasks = new HashMap<>();
                    for (Todo task : tasks) {
                        if (task.completed && task.time > 0) {
                            String date = dateFormat.format(new Date(task.time));
                            dailyCompletedTasks.put(date, dailyCompletedTasks.getOrDefault(date, 0) + 1);
                        }
                    }
                    
                    // 在UI线程更新界面
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            // 更新任务总览
                            textTotalTasks.setText(String.valueOf(totalTasks));
                            textCompletedTasks.setText(String.valueOf(completedTasks));
                            textPendingTasks.setText(String.valueOf(pendingTasks));
                            
                            // 更新完成率
                            textCompletionRate.setText(completionRate + "%");
                            progressCompletionRate.setProgress(completionRate);
                            
                            // 更新饼图
                            updatePieChart(categoryCount);
                            
                            // 更新柱状图
                            updateBarChart(dates, dailyFocusTime);
                            
                            // 更新折线图
                            updateLineChart(dates, dailyCompletedTasks);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "加载统计数据失败", e);
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "创建统计线程失败", e);
        }
    }

    private void updatePieChart(Map<String, Integer> categoryCount) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "任务分类");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate();
    }

    private void updateBarChart(String[] dates, Map<String, Float> dailyFocusTime) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            float focusTime = dailyFocusTime.getOrDefault(dates[i], 0f);
            entries.add(new BarEntry(i, focusTime));
        }

        BarDataSet dataSet = new BarDataSet(entries, "专注用时(分钟)");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));
        barChart.setData(data);
        barChart.invalidate();
    }

    private void updateLineChart(String[] dates, Map<String, Integer> dailyCompletedTasks) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            int completedCount = dailyCompletedTasks.getOrDefault(dates[i], 0);
            entries.add(new Entry(i, completedCount));
        }

        LineDataSet dataSet = new LineDataSet(entries, "完成任务数");
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.BLUE);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);

        LineData data = new LineData(dataSet);
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));
        lineChart.setData(data);
        lineChart.invalidate();
    }
} 