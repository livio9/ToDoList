package com.example.todolist.ui;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.todolist.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 设置 Toolbar 和返回按钮
        Toolbar toolbar = findViewById(R.id.aboutToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // 设置文本内容
        TextView textView = findViewById(R.id.textAboutInfo);
        textView.setText("智能待办清单App\n\n" +
                "版本：v1.0\n" +
                "\n" +
                "【产品简介】\n" +
                "本应用是一款面向高效生活与学习的智能待办清单工具，集任务管理、专注统计、云同步、个性化主题于一体，助力用户科学规划、专注执行、持续成长。\n" +
                "\n" +
                "【核心功能亮点】\n" +
                "- 多端云同步：支持本地与云端数据实时同步，换设备、重装App都不丢数据。\n" +
                "- 任务/代办集/子任务多层级管理：支持任务分组、子任务拆解，复杂目标也能轻松拆分执行。\n" +
                "- 专注时长统计与可视化分析：自动统计每日专注用时、任务完成趋势，生成柱状图、折线图等多种可视化报表。\n" +
                "- 丰富主题与个性化头像：多种配色风格、头像自定义，打造专属你的待办体验。\n" +
                "- 番茄钟与优先级：内置番茄工作法，支持任务优先级、分类、提醒，科学提升效率。\n" +
                "- 离线可用：断网时本地操作无障碍，联网后自动同步。\n" +
                "\n" +
                "【适用场景】\n" +
                "- 学习计划、考试备考、论文进度管理\n" +
                "- 工作任务、项目协作、会议备忘\n" +
                "- 生活琐事、购物清单、习惯养成\n" +
                "- 个人成长、目标拆解、时间管理\n" +
                "\n" +
                "【技术实现】\n" +
                "- Android原生开发，采用MVVM架构，Room数据库本地存储\n" +
                "- Parse云服务实现数据同步与用户管理\n" +
                "- MPAndroidChart实现数据可视化\n" +
                "- Material Design风格，兼容多种主题与深色模式\n" +
                "\n" +
                "【设计理念】\n" +
                "- 极简高效，界面清爽，操作直观，功能实用不冗余\n" +
                "- 数据安全，隐私保护，所有数据加密存储\n" +
                "- 支持个性化，满足不同用户的习惯和审美\n" +
                "\n" +
                "【未来展望】\n" +
                "- 支持Web/小程序多端同步\n" +
                "- AI智能推荐与自动任务拆解\n" +
                "- 更丰富的统计分析与成长激励体系\n" +
                "- 团队协作与共享清单功能\n" +
                "\n" +
                "【开发团队】\n" +
                "Echo & 派小星\n" +
                "\n" +
                "【联系方式】\n" +
                "邮箱：3148445926@qq.com\n" +
                "邮箱：1817873008@qq.com\n" +
                "\n" +
                "感谢您的使用与支持！如有建议或Bug欢迎随时反馈，我们会持续优化产品体验。\n");
    }
}
