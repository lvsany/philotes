package com.example.philotes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 主活动
 * 集成日历、导航、待办三个核心功能
 */
public class MainActivity extends AppCompatActivity {

    private Button btnCreateCalendar;
    private Button btnOpenNavigation;
    private Button btnCreateTodo;
    private TextView statusText;
    private TextView recognitionResult;

    // 权限请求启动器
    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    // 待处理的操作（权限授予后执行）
    private Runnable pendingAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 设置窗口边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化视图
        initViews();

        // 初始化权限请求
        initPermissionLauncher();

        // 设置点击事件
        setupClickListeners();

        // 显示模拟识别结果
        showSimulatedRecognitionResult();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        btnCreateCalendar = findViewById(R.id.btnCreateCalendar);
        btnOpenNavigation = findViewById(R.id.btnOpenNavigation);
        btnCreateTodo = findViewById(R.id.btnCreateTodo);
        statusText = findViewById(R.id.statusText);
        recognitionResult = findViewById(R.id.recognitionResult);
    }

    /**
     * 初始化权限请求启动器
     */
    private void initPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    Boolean readGranted = permissions.getOrDefault(Manifest.permission.READ_CALENDAR, false);
                    Boolean writeGranted = permissions.getOrDefault(Manifest.permission.WRITE_CALENDAR, false);

                    if (readGranted && writeGranted) {
                        updateStatus("日历权限已授予");
                        // 执行待处理的操作
                        if (pendingAction != null) {
                            pendingAction.run();
                            pendingAction = null;
                        }
                    } else {
                        updateStatus("日历权限被拒绝");
                        Toast.makeText(this, "需要日历权限才能创建事件", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * 设置按钮点击事件
     */
    private void setupClickListeners() {
        // 创建日历事件
        btnCreateCalendar.setOnClickListener(v -> onCreateCalendarClick());

        // 打开导航
        btnOpenNavigation.setOnClickListener(v -> onOpenNavigationClick());

        // 创建待办事项
        btnCreateTodo.setOnClickListener(v -> onCreateTodoClick());
    }

    /**
     * 显示模拟的识别结果
     */
    private void showSimulatedRecognitionResult() {
        String result = "📋 事件: " + CalendarHelper.EVENT_TITLE + "\n" +
                "⏰ 时间: 2026-01-25 14:00-15:00\n" +
                "📍 地点: " + NavigationHelper.DESTINATION_NAME + "\n" +
                "📝 备注: " + TodoHelper.TODO_DESCRIPTION;
        recognitionResult.setText(result);
    }

    /**
     * 创建日历事件按钮点击
     */
    private void onCreateCalendarClick() {
        if (checkCalendarPermissions()) {
            createCalendarEvent();
        } else {
            // 设置待处理操作
            pendingAction = this::createCalendarEvent;
            // 请求权限
            requestCalendarPermissions();
        }
    }

    /**
     * 检查日历权限
     */
    private boolean checkCalendarPermissions() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求日历权限
     */
    private void requestCalendarPermissions() {
        // 显示权限说明对话框
        new AlertDialog.Builder(this)
                .setTitle("需要日历权限")
                .setMessage("为了创建日历事件，需要访问您的日历。请授予日历读写权限。")
                .setPositiveButton("授予权限", (dialog, which) -> {
                    requestPermissionLauncher.launch(new String[] {
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                    });
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    updateStatus("已取消权限请求");
                    pendingAction = null;
                })
                .show();
    }

    /**
     * 执行创建日历事件
     */
    private void createCalendarEvent() {
        updateStatus("正在创建日历事件...");

        Uri eventUri = CalendarHelper.createCalendarEvent(this);

        if (eventUri != null) {
            updateStatus("✅ 日历事件创建成功！");
            Toast.makeText(this,
                    "已创建事件: " + CalendarHelper.EVENT_TITLE + "\n请查看日历应用",
                    Toast.LENGTH_LONG).show();

            // 显示成功对话框
            new AlertDialog.Builder(this)
                    .setTitle("创建成功")
                    .setMessage(CalendarHelper.getEventSummary())
                    .setPositiveButton("确定", null)
                    .show();
        } else {
            updateStatus("❌ 日历事件创建失败");
            Toast.makeText(this, "创建失败，请确保设备已登录日历账户", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 打开导航按钮点击
     */
    private void onOpenNavigationClick() {
        updateStatus("正在打开导航...");

        boolean success = NavigationHelper.startNavigation(this);

        if (success) {
            updateStatus("✅ 已打开导航到 " + NavigationHelper.DESTINATION_NAME);
        } else {
            updateStatus("❌ 无法打开导航");
            Toast.makeText(this, "打开导航失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 创建待办事项按钮点击
     */
    private void onCreateTodoClick() {
        updateStatus("正在创建待办事项...");

        // 显示选项对话框
        new AlertDialog.Builder(this)
                .setTitle("创建待办/提醒")
                .setMessage("待办内容:\n" + TodoHelper.getTodoSummary())
                .setPositiveButton("创建提醒", (dialog, which) -> {
                    boolean success = TodoHelper.createTodo(this);
                    if (success) {
                        updateStatus("✅ 待办提醒已创建");
                    } else {
                        updateStatus("❌ 创建待办失败");
                        Toast.makeText(this, "创建失败，请手动添加待办", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    updateStatus("已取消创建待办");
                })
                .show();
    }

    /**
     * 更新状态文本
     */
    private void updateStatus(String status) {
        statusText.setText(status);
    }
}