package com.example.todolist.ai;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TaskDecomposer {
    private static final String TAG = "TaskDecomposer";
    private static final String MODEL = "google/gemma-3-27b-it:free";
    private static final String BASE_URL = "https://openrouter.ai/api/v1";
    private static final String API_KEY = "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";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // 任务分解的提示模板
    private static final String PROMPT_TEMPLATE =
            "将以下大任务拆分为多个具体、可执行的小任务: \"%s\"\n\n" +
                    "请使用JSON格式响应，格式如下:\n" +
                    "{\n" +
                    "  \"mainTask\": \"原始大任务的名称\",\n" +
                    "  \"category\": \"推荐的任务类别\",\n" +
                    "  \"estimatedDays\": 预计完成所需的天数,\n" +
                    "  \"subTasks\": [\n" +
                    "    {\n" +
                    "      \"title\": \"小任务1标题\",\n" +
                    "      \"description\": \"小任务1的简短描述\",\n" +
                    "      \"estimatedHours\": 预计完成所需的小时数\n" +
                    "    },\n" +
                    "    ...\n" +
                    "  ]\n" +
                    "}\n\n" +
                    "要求:\n" +
                    "1. 首先评估任务的复杂度:\n" +
                    "   - 简单任务（可在1-2天内完成的）: 分解为2-3个子任务\n" +
                    "   - 中等复杂度任务（需要3-5天的）: 分解为4-6个子任务\n" +
                    "   - 复杂任务（需要1周以上的）: 分解为7-10个子任务\n" +
                    "2. 每个小任务必须具体、明确、可操作，避免过于笼统的描述\n" +
                    "3. 估计合理的完成时间，不要高估或低估\n" +
                    "4. 根据任务性质推荐适合的任务类别(工作、个人、学习或其他)\n" +
                    "5. 确保只返回JSON格式，不要添加任何解释或额外文本\n" +
                    "6. 子任务应该是原子化的，不应该再需要进一步分解";

    /**
     * 任务项类，表示分解后的小任务
     */
    public static class SubTask {
        private String title;
        private String description;
        private double estimatedHours;

        public SubTask(String title, String description, double estimatedHours) {
            this.title = title;
            this.description = description;
            this.estimatedHours = estimatedHours;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public double getEstimatedHours() {
            return estimatedHours;
        }
    }

    /**
     * 分解结果类，包含主任务信息和分解后的子任务列表
     */
    public static class DecompositionResult {
        private String mainTask;
        private String category;
        private int estimatedDays;
        private List<SubTask> subTasks;

        public DecompositionResult(String mainTask, String category, int estimatedDays) {
            this.mainTask = mainTask;
            this.category = category;
            this.estimatedDays = estimatedDays;
            this.subTasks = new ArrayList<>();
        }

        public void addSubTask(SubTask task) {
            subTasks.add(task);
        }

        public String getMainTask() {
            return mainTask;
        }

        public String getCategory() {
            return category;
        }

        public int getEstimatedDays() {
            return estimatedDays;
        }

        public List<SubTask> getSubTasks() {
            return subTasks;
        }
    }

    /**
     * 将大任务分解为小任务
     * @param mainTaskTitle 主任务标题
     * @return 分解结果，包含子任务列表
     * @throws IOException 网络错误
     * @throws JSONException JSON解析错误
     */
    public static DecompositionResult decomposeTask(String mainTaskTitle) throws IOException, JSONException {
        String prompt = String.format(PROMPT_TEMPLATE, mainTaskTitle);

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", MODEL);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);

        requestBody.put("messages", messages);

        // 设置请求参数
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        // 创建请求
        Request request = new Request.Builder()
                .url(BASE_URL + "/chat/completions")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();

        // 发送请求
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            Log.d(TAG, "API响应: " + responseBody);

            // 解析响应
            JSONObject jsonResponse = new JSONObject(responseBody);

            // 检查API是否返回错误
            if (jsonResponse.has("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                String errorMessage = error.getString("message");
                throw new IOException("API返回错误: " + errorMessage);
            }

            // 检查是否存在choices字段
            if (!jsonResponse.has("choices")) {
                throw new JSONException("API响应中缺少choices字段");
            }

            String content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // 从内容中提取JSON字符串
            String jsonContent = extractJsonFromString(content);

            // 检查是否成功提取到JSON
            if (jsonContent.trim().isEmpty() || !jsonContent.startsWith("{")) {
                throw new JSONException("无法从API响应中提取有效的JSON数据");
            }

            JSONObject taskJson = new JSONObject(jsonContent);

            // 检查必要字段是否存在
            if (!taskJson.has("mainTask") || !taskJson.has("category") ||
                    !taskJson.has("estimatedDays") || !taskJson.has("subTasks")) {
                throw new JSONException("API返回的JSON缺少必要字段");
            }

            // 创建分解结果对象
            DecompositionResult result = new DecompositionResult(
                    taskJson.getString("mainTask"),
                    taskJson.getString("category"),
                    taskJson.getInt("estimatedDays")
            );

            // 解析子任务
            JSONArray subTasksJson = taskJson.getJSONArray("subTasks");
            for (int i = 0; i < subTasksJson.length(); i++) {
                JSONObject subTaskJson = subTasksJson.getJSONObject(i);
                SubTask subTask = new SubTask(
                        subTaskJson.getString("title"),
                        subTaskJson.getString("description"),
                        subTaskJson.getDouble("estimatedHours")
                );
                result.addSubTask(subTask);
            }

            return result;
        }
    }

    /**
     * 从字符串中提取JSON内容
     * 处理模型可能在JSON前后添加的额外文本
     */
    private static String extractJsonFromString(String input) {
        // 查找第一个{和最后一个}的位置
        int start = input.indexOf('{');
        int end = input.lastIndexOf('}');

        if (start >= 0 && end >= 0 && end > start) {
            return input.substring(start, end + 1);
        }

        // 如果没有找到完整的JSON，返回原字符串
        return input;
    }
} 