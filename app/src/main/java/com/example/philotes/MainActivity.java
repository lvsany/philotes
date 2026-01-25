package com.example.philotes;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.domain.MockActionParser;
import com.example.philotes.utils.ModelUtils;
import com.google.gson.GsonBuilder;

import java.io.File;

/**
 * 主活动
 * 集成日历、导航、待办三个核心功能
 * 使用 ActionParser 解析文本，ActionExecutor 执行动作
 */
public class MainActivity extends AppCompatActivity {

    // --- UI Components ---
    private TextView statusText;
    private TextView recognitionResult;

    // LLM AI Components
    private EditText etInput;
    private Button btnParse;
    private TextView tvResult;

    // Download UI
    private LinearLayout layoutDownload;
    private ProgressBar progressBar;
    private Button btnDownload;
    private TextView tvDownloadStatus;

    // 核心组件
    private ActionParser actionParser;
    private ActionExecutor actionExecutor;
    
    // Mock 模式开关（模型不可用时使用）
    private boolean useMockParser = true; // 默认使用 Mock 模式

    // 权限请求启动器
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActionPlan pendingActionPlan; // 等待权限授予后执行的 ActionPlan

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化执行器
        actionExecutor = new ActionExecutor(this);

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

        // 检查并初始化模型
        File modelFile = ModelUtils.getModelFile(this);
        if (modelFile.exists()) {
            initModel(modelFile);
            useMockParser = false; // 模型可用，关闭 Mock 模式
        } else {
            showDownloadUI();
            useMockParser = true; // 模型不可用，使用 Mock 模式
            Toast.makeText(this, "模型未下载，使用规则解析模式", Toast.LENGTH_LONG).show();
        }

        // 显示提示
        showSimulatedRecognitionResult();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        // 状态和结果显示
        statusText = findViewById(R.id.statusText);
        recognitionResult = findViewById(R.id.recognitionResult);

        // LLM 相关视图
        etInput = findViewById(R.id.etInput);
        btnParse = findViewById(R.id.btnParse);
        tvResult = findViewById(R.id.tvResult);

        // 下载界面
        layoutDownload = findViewById(R.id.layoutDownload);
        progressBar = findViewById(R.id.progressBar);
        btnDownload = findViewById(R.id.btnDownload);
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus);
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
                        if (pendingActionPlan != null) {
                            executeAction(pendingActionPlan);
                            pendingActionPlan = null;
                        }
                    } else {
                        updateStatus("日历权限被拒绝");
                        Toast.makeText(this, "需要日历权限才能创建事件", Toast.LENGTH_LONG).show();
                        pendingActionPlan = null;
                    }
                });
    }

    /**
     * 设置按钮点击事件
     */
    private void setupClickListeners() {
        // 解析按钮
        btnParse.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show();
                return;
            }
            performParse(text);
        });

        // 下载按钮
        btnDownload.setOnClickListener(v -> startDownload(ModelUtils.getModelFile(this)));
    }

    /**
     * 显示提示信息
     */
    private void showSimulatedRecognitionResult() {
        String result = "请输入文本并点击解析按钮\n\nMock 测试模式：\n输入 1 = 创建日历事件\n输入 2 = 开始导航\n输入 3 = 添加待办";
        recognitionResult.setText(result);
    }

    /**
     * 解析文本并执行动作
     */
    private void performParse(String text) {
        tvResult.setText("正在解析...");
        btnParse.setEnabled(false);
        updateStatus("正在解析输入内容...");

        new Thread(() -> {
            try {
                ActionPlan plan;
                
                // 根据模式选择解析器
                if (useMockParser) {
                    // 使用 Mock 解析器
                    plan = MockActionParser.parse(text);
                    runOnUiThread(() -> updateStatus("使用规则解析模式"));
                } else {
                    // 使用真实的 LLM 模型
                    if (actionParser == null) {
                        runOnUiThread(() -> {
                            tvResult.setText("模型未就绪，请先下载模型");
                            btnParse.setEnabled(true);
                            updateStatus("模型未就绪");
                        });
                        return;
                    }
                    plan = actionParser.parse(text);
                    runOnUiThread(() -> updateStatus("使用 AI 模型解析"));
                }
                
                if (plan == null) {
                    runOnUiThread(() -> {
                        tvResult.setText("解析失败");
                        btnParse.setEnabled(true);
                        updateStatus("解析失败");
                    });
                    return;
                }

                // 显示解析结果
                String jsonResult = new GsonBuilder().setPrettyPrinting().create().toJson(plan);
                String summary = ActionExecutor.getActionSummary(plan);

                runOnUiThread(() -> {
                    tvResult.setText(jsonResult);
                    recognitionResult.setText(summary);
                    btnParse.setEnabled(true);
                    updateStatus("解析完成，准备执行...");

                    // 显示确认对话框
                    showExecutionConfirmDialog(plan);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvResult.setText("解析错误: " + e.getMessage());
                    btnParse.setEnabled(true);
                    updateStatus("解析出错");
                });
            }
        }).start();
    }

    /**
     * 显示执行确认对话框
     */
    private void showExecutionConfirmDialog(ActionPlan plan) {
        String message = ActionExecutor.getActionSummary(plan);
        
        new AlertDialog.Builder(this)
                .setTitle("确认执行")
                .setMessage(message + "\n\n确认执行此操作吗？")
                .setPositiveButton("执行", (dialog, which) -> executeAction(plan))
                .setNegativeButton("取消", (dialog, which) -> updateStatus("已取消"))
                .show();
    }

    /**
     * 执行动作
     */
    private void executeAction(ActionPlan plan) {
        // 检查是否需要日历权限
        if (plan.getType() == com.example.philotes.data.model.ActionType.CREATE_CALENDAR) {
            if (!checkCalendarPermissions()) {
                pendingActionPlan = plan;
                requestCalendarPermissions();
                return;
            }
        }

        // 执行动作
        updateStatus("正在执行...");
        
        new Thread(() -> {
            ActionExecutor.ExecutionResult result = actionExecutor.execute(plan);
            
            runOnUiThread(() -> {
                if (result.success) {
                    updateStatus("✅ " + result.message);
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                } else {
                    updateStatus("❌ " + result.message);
                    Toast.makeText(this, "执行失败: " + result.message, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /**
     * 检查日历权限
     */
    private boolean checkCalendarPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求日历权限
     */
    private void requestCalendarPermissions() {
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
                    pendingActionPlan = null;
                })
                .show();
    }

    /**
     * 更新状态文本
     */
    private void updateStatus(String status) {
        statusText.setText(status);
    }

    // --- 模型下载和初始化 ---

    private void showDownloadUI() {
        layoutDownload.setVisibility(View.VISIBLE);
        btnParse.setEnabled(true); // Mock 模式下仍可解析
        etInput.setEnabled(true);
        tvResult.setText("使用规则解析模式（Mock）\n下载模型后可启用 AI 解析");
    }

    private void startDownload(File targetFile) {
        btnDownload.setEnabled(false);
        tvDownloadStatus.setText("正在下载模型...");

        ModelUtils.downloadModel(this, ModelUtils.MODEL_URL, targetFile, new ModelUtils.DownloadListener() {
            @Override
            public void onProgress(int percentage) {
                runOnUiThread(() -> progressBar.setProgress(percentage));
            }

            @Override
            public void onCompleted(File file) {
                runOnUiThread(() -> {
                    layoutDownload.setVisibility(View.GONE);
                    initModel(file);
                    Toast.makeText(MainActivity.this, "下载完成！", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    String msg = "下载失败\n请检查 ModelUtils.java 中的 MODEL_URL\n错误: " + e.getMessage();
                    tvDownloadStatus.setText(msg);
                    tvDownloadStatus.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    btnDownload.setEnabled(true);
                    btnDownload.setText("重试下载");
                });
            }
        });
    }

    private void initModel(File modelFile) {
        // 初始化 ActionParser
        actionParser = new ActionParser(new com.example.philotes.data.api.OnDeviceLlmService(this, modelFile.getAbsolutePath()));

        btnParse.setEnabled(true);
        etInput.setEnabled(true);
        tvResult.setText("模型已就绪: " + modelFile.getName());
        updateStatus("AI 模型已加载");
        useMockParser = false; // 关闭 Mock 模式
    }
}
