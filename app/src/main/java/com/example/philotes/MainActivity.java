package com.example.philotes;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.domain.MockActionParser;
import com.example.philotes.utils.ModelUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 主活动
 * 集成日历、导航、待办三个核心功能
 * 使用 ActionParser 解析文本，ActionExecutor 执行动作
 */
public class MainActivity extends AppCompatActivity {

    // --- UI Components ---
    private TextView statusText;
    private RecyclerView rvActionCards;
    private ActionCardAdapter actionCardAdapter;
    private List<ActionPlan> actionPlanList = new ArrayList<>();

    // LLM AI Components
    private EditText etInput;
    private Button btnParse;

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
    private ActivityResultLauncher<Intent> overlayPermissionLauncher; // 悬浮窗权限启动器
    private ActionPlan pendingActionPlan; // 等待权限授予后执行的 ActionPlan

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化执行器
        actionExecutor = new ActionExecutor(this);

        // 初始化视图
        initViews();

        // 初始化权限请求
        initPermissionLauncher();

        // 检查悬浮窗权限
        checkAndRequestOverlayPermission();

        // 设置点击事件
        setupClickListeners();

        // 检查 Intent 是否包含分享内容
        handleIntent(getIntent());

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
        // 状态显示
        statusText = findViewById(R.id.statusText);

        // LLM 相关视图
        etInput = findViewById(R.id.etInput);
        btnParse = findViewById(R.id.btnParse);

        // 卡片列表
        rvActionCards = findViewById(R.id.rvActionCards);
        rvActionCards.setLayoutManager(new LinearLayoutManager(this));
        actionCardAdapter = new ActionCardAdapter(actionPlanList, new ActionCardAdapter.OnActionClickListener() {
            @Override
            public void onExecute(ActionPlan plan) {
                executeAction(plan);
            }

            @Override
            public void onEdit(ActionPlan plan) {
                Intent intent = new Intent(MainActivity.this, ActionDetailActivity.class);
                intent.putExtra("action_plan", new com.google.gson.Gson().toJson(plan));
                startActivity(intent);
            }
        });
        rvActionCards.setAdapter(actionCardAdapter);

        // 下载界面
        layoutDownload = findViewById(R.id.layoutDownload);
        progressBar = findViewById(R.id.progressBar);
        btnDownload = findViewById(R.id.btnDownload);
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus);

        // FAB 开启悬浮球
        FloatingActionButton fab = findViewById(R.id.fabEnableFloating);
        fab.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                overlayPermissionLauncher.launch(intent);
            } else {
                // Android 14+ 要求启动 MediaProjection 类型的 FGS 前必须先获得用户授权
                Toast.makeText(this, "请授予录屏权限以开启悬浮球", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, ScreenCaptureActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * 初始化权限请求启动器
     */
    private void initPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean readGranted = permissions.getOrDefault(Manifest.permission.READ_CALENDAR, false);
                    boolean writeGranted = permissions.getOrDefault(Manifest.permission.WRITE_CALENDAR, false);

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

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "悬浮窗权限已授予，请继续授权录屏", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, ScreenCaptureActivity.class);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "悬浮窗权限被拒绝，功能可能受限", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * 检查并请求悬浮窗权限
     */
    private void checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("需要悬浮窗权限")
                    .setMessage("为了在其他应用中使用Snap2Action，请授予悬浮窗权限。")
                    .setPositiveButton("去授权", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        overlayPermissionLauncher.launch(intent);
                    })
                    .setNegativeButton("稍后", null)
                    .show();
        }
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
        statusText.setText(result);
    }

    /**
     * 解析文本并执行动作
     */
    private void performParse(String text) {
        statusText.setText("正在解析...");

        // 模拟解析逻辑，增加到列表
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 假装在思考
                ActionPlan plan;
                if (useMockParser) {
                    plan = MockActionParser.parse(text);
                } else {
                    plan = actionParser.parse(text);
                }

                if (plan != null) {
                    runOnUiThread(() -> {
                        actionPlanList.add(0, plan);
                        actionCardAdapter.notifyItemInserted(0);
                        rvActionCards.scrollToPosition(0);
                        statusText.setText("解析成功");
                    });
                } else {
                    runOnUiThread(() -> statusText.setText("未能识别出动作"));
                }
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("解析失败: " + e.getMessage()));
            }
        }).start();
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

    /**
     * 处理传入的 Intent
     */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSharedText(intent);
            } else if (type.startsWith("image/")) {
                handleSharedImage(intent);
            }
        }
    }

    private void handleSharedText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            etInput.setText(sharedText);
            // 自动开始解析
            performParse(sharedText);
        }
    }

    private void handleSharedImage(Intent intent) {
        String imagePath = intent.getStringExtra("image_path");
        Uri imageUri = null;

        if (imagePath != null) {
            imageUri = Uri.fromFile(new File(imagePath));
        } else {
            imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (imageUri != null) {
            // 显示截图预览
            findViewById(R.id.screenshotPreview).setVisibility(View.VISIBLE);
            findViewById(R.id.placeholderText).setVisibility(View.GONE);
            ((android.widget.ImageView)findViewById(R.id.screenshotPreview)).setImageURI(imageUri);

            updateStatus("正在进行 OCR 识别...");

            // 模拟 OCR 过程
            new Thread(() -> {
                try {
                    Thread.sleep(1500); // 模拟网络耗时

                    // 假设 OCR 返回了如下文本
                    String simulatedOcrText = "明天下午三点在会议室开会讨论项目进度";

                    runOnUiThread(() -> {
                        Toast.makeText(this, "OCR 识别完成", Toast.LENGTH_SHORT).show();
                        etInput.setText(simulatedOcrText); // 填充到输入框供用户修改
                        performParse(simulatedOcrText); // 自动开始解析
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    // --- 模型下载和初始化 ---

    private void showDownloadUI() {
        layoutDownload.setVisibility(View.VISIBLE);
        btnParse.setEnabled(true); // Mock 模式下仍可解析
        etInput.setEnabled(true);
        statusText.setText("使用规则解析模式（Mock）\n下载模型后可启用 AI 解析");
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
        statusText.setText("模型已就绪: " + modelFile.getName());
        updateStatus("AI 模型已加载");
        useMockParser = false; // 关闭 Mock 模式
    }
}
