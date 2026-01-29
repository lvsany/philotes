package com.example.philotes;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
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
import com.example.philotes.utils.ModelUtils;
import com.example.philotes.utils.MlKitOcrService;
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

    // 权限请求启动器
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher; // 悬浮窗权限启动器
    private ActionPlan pendingActionPlan; // 等待权限授予后执行的 ActionPlan

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ========== 加载用户设置 ==========
        com.example.philotes.utils.AiSettingsManager settingsManager =
            new com.example.philotes.utils.AiSettingsManager(this);
        settingsManager.applyToLlmConfig();
        // ==================================

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
        } else {
            showDownloadUI();
            Toast.makeText(this, "模型未下载，请先下载模型", Toast.LENGTH_LONG).show();
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

        // 设置按钮
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

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
            if (!isAccessibilityServiceEnabled()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要辅助功能权限")
                        .setMessage("请在设置中开启“Philotes助手”辅助功能，以便使用悬浮截图功能。")
                        .setPositiveButton("去开启", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                Toast.makeText(this, "悬浮截屏服务已开启", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String serviceId = getPackageName() + "/" + FloatingButtonService.class.getName();

        for (AccessibilityServiceInfo service : enabledServices) {
            if (serviceId.equals(service.getId())) {
                return true;
            }
        }
        return false;
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
                    // Overlay permission check logic might be redundant if using Accessibility Service
                    // keeping it for now in case other parts need it, but removing screen capture link
                    if (Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
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
        String result = "请输入文本并点击解析按钮";
        statusText.setText(result);
    }

    /**
     * 解析文本并执行动作
     */
    private void performParse(String text) {
        if (actionParser == null) {
            Toast.makeText(this, "模型未加载，请先下载并初始化模型", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("正在解析...");

        // 使用 AI 模型解析
        new Thread(() -> {
            try {
                Thread.sleep(1000); // AI 处理耗时
                ActionPlan plan = actionParser.parse(text);

                if (plan != null) {
                    runOnUiThread(() -> {
                        // 检查是否是 UNKNOWN 类型
                        if (plan.getType() == com.example.philotes.data.model.ActionType.UNKNOWN) {
                            statusText.setText("⚠️ 解析失败\n\n可能原因：\n• API Key 无效（401 错误）\n• 网络连接问题\n• API 配置错误\n\n请检查设置页面的 API 配置");
                            Toast.makeText(this, "解析失败：请检查 API 设置", Toast.LENGTH_LONG).show();
                        } else {
                            actionPlanList.add(0, plan);
                            actionCardAdapter.notifyItemInserted(0);
                            rvActionCards.scrollToPosition(0);
                            statusText.setText("解析成功");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        statusText.setText("未能识别出动作");
                        Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String errorMsg = "解析失败: " + e.getMessage();
                    statusText.setText(errorMsg);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
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

            // 使用 ML Kit OCR 识别图片文本
            try {
                Bitmap originalBitmap;
                if (imagePath != null) {
                    originalBitmap = BitmapFactory.decodeFile(imagePath);
                } else {
                    java.io.InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    originalBitmap = BitmapFactory.decodeStream(inputStream);
                    if (inputStream != null) inputStream.close();
                }

                if (originalBitmap != null) {
                    // 创建可变的Bitmap副本，确保ML Kit可以安全访问
                    final Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    originalBitmap.recycle(); // 立即释放原始bitmap

                    if (mutableBitmap == null) {
                        updateStatus("图片处理失败");
                        Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    MlKitOcrService.recognizeTextAsync(mutableBitmap, new MlKitOcrService.OcrCallback() {
                        @Override
                        public void onSuccess(com.example.philotes.data.model.OcrResult result) {
                            // OCR完成后释放bitmap
                            mutableBitmap.recycle();

                            runOnUiThread(() -> {
                                if (result.getTextBlocks().isEmpty()) {
                                    updateStatus("❌ 未识别到文字\n请确保图片中包含清晰的文本");
                                    Toast.makeText(MainActivity.this,
                                        "未识别到文字", Toast.LENGTH_LONG).show();
                                } else {
                                    // 将结构化文本填充到输入框
                                    String structuredText = result.toStructuredText();
                                    etInput.setText(structuredText);
                                    updateStatus("✅ OCR识别成功\n识别到 " +
                                        result.getTextBlocks().size() + " 个文本块\n\n" +
                                        "可以编辑后点击「AI解析」按钮");

                                    Toast.makeText(MainActivity.this,
                                        "识别成功！可编辑后解析", Toast.LENGTH_SHORT).show();

                                    // 自动解析（可选，也可以让用户手动点击）
                                    // performParse(structuredText);
                                }
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            // 发生错误时也要释放bitmap
                            mutableBitmap.recycle();

                            Log.e("MainActivity", "OCR error", e);
                            runOnUiThread(() -> {
                                updateStatus("❌ OCR识别失败\n" + e.getMessage() +
                                    "\n\n可能原因：\n" +
                                    "1. 图片中没有清晰的文字\n" +
                                    "2. 首次使用需联网下载模型");
                                Toast.makeText(MainActivity.this,
                                    "OCR识别失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                } else {
                    updateStatus("图片加载失败");
                    Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Image processing error", e);
                updateStatus("图片处理失败: " + e.getMessage());
                Toast.makeText(this, "图片处理失败", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- 模型下载和初始化 ---

    private void showDownloadUI() {
        layoutDownload.setVisibility(View.VISIBLE);

        // 检查是否配置了 OpenAI API
        if (com.example.philotes.utils.LlmConfig.isOpenAiConfigured()) {
            // 有 API 配置，可以直接使用
            initOpenAiService();
        } else {
            // 没有 API 配置
            btnParse.setEnabled(false);
            btnParse.setText("需下载模型或配置 API");
            etInput.setEnabled(true);
            etInput.setHint("下载模型或配置 OpenAI API");

            statusText.setText("⚠️ 模型未下载\n\n" +
                              "选项 1: 下载端侧模型（需真机）\n" +
                              "选项 2: 配置 OpenAI API（可用模拟器）\n\n" +
                              "您仍可以测试 UI 和其他功能");
        }
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
        // 获取用户设置
        com.example.philotes.utils.AiSettingsManager settingsManager =
            new com.example.philotes.utils.AiSettingsManager(this);

        // 用户设置优先：如果用户选择云端模式且已配置
        if (settingsManager.isCloudApiMode() && settingsManager.isApiConfigured()) {
            initOpenAiService();
            return;
        }

        // 检查是否在模拟器上运行
        boolean isEmulator = com.example.philotes.utils.LlmConfig.isEmulator();

        // 模拟器且配置了 OpenAI API - 使用 OpenAI
        if (isEmulator && com.example.philotes.utils.LlmConfig.isOpenAiConfigured()) {
            initOpenAiService();
            return;
        }

        // 尝试初始化端侧 LLM
        try {
            com.example.philotes.data.api.OnDeviceLlmService llmService =
                new com.example.philotes.data.api.OnDeviceLlmService(this, modelFile.getAbsolutePath());

            // 尝试初始化
            llmService.initialize();

            if (llmService.hasInitializationFailed()) {
                // 端侧初始化失败
                if (com.example.philotes.utils.LlmConfig.isOpenAiConfigured()) {
                    // 有 API 配置，切换到 OpenAI
                    String errorMsg = "⚠️ 端侧 LLM 初始化失败\n正在切换到 OpenAI API...";
                    statusText.setText(errorMsg);
                    Toast.makeText(this, "切换到云端 AI", Toast.LENGTH_SHORT).show();
                    initOpenAiService();
                } else {
                    // 没有 API 配置
                    String errorMsg = "⚠️ 模拟器模式\n\n" +
                                     "端侧 LLM 仅支持真实 ARM64 设备\n\n" +
                                     "💡 提示：您可以配置 OpenAI API 在模拟器上使用 AI\n" +
                                     "在代码中设置 LlmConfig.setOpenAiApiKey()";
                    statusText.setText(errorMsg);

                    btnParse.setEnabled(false);
                    btnParse.setText("需配置 API 或使用真机");
                    etInput.setEnabled(true);
                    etInput.setHint("模拟器模式 - 需配置 OpenAI API");

                    Toast.makeText(this, "请配置 OpenAI API 或在真机上运行", Toast.LENGTH_LONG).show();
                }
            } else {
                // 端侧初始化成功
                actionParser = new ActionParser(llmService);
                btnParse.setEnabled(true);
                btnParse.setText("AI 解析（端侧）");
                etInput.setEnabled(true);
                etInput.setHint("输入文本进行 AI 解析");
                statusText.setText("✅ 端侧模型已就绪: " + modelFile.getName());
                updateStatus("AI 模型已加载");
            }
        } catch (Exception e) {
            String errorMsg = "模型加载异常: " + e.getMessage();
            Log.e("MainActivity", errorMsg, e);

            // 尝试使用 OpenAI API 作为备选
            if (com.example.philotes.utils.LlmConfig.isOpenAiConfigured()) {
                statusText.setText("端侧模型异常，切换到 OpenAI API...");
                initOpenAiService();
            } else {
                statusText.setText(errorMsg + "\n\n您可以配置 OpenAI API");
                btnParse.setEnabled(false);
                etInput.setEnabled(true);
                Toast.makeText(this, "模型加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 初始化 OpenAI API 服务
     */
    private void initOpenAiService() {
        try {
            String apiKey = com.example.philotes.utils.LlmConfig.getOpenAiApiKey();
            String baseUrl = com.example.philotes.utils.LlmConfig.getOpenAiBaseUrl();
            String model = com.example.philotes.utils.LlmConfig.getOpenAiModel();

            com.example.philotes.data.api.OpenAIService openAiService =
                new com.example.philotes.data.api.OpenAIService(apiKey, baseUrl, model);

            actionParser = new ActionParser(openAiService);

            btnParse.setEnabled(true);
            btnParse.setText("AI 解析（云端）");
            etInput.setEnabled(true);
            etInput.setHint("输入文本进行 AI 解析（使用 " + model + "）");
            statusText.setText("✅ OpenAI API 已就绪\n模型: " + model + "\n模式: 云端推理");

            Toast.makeText(this, "使用 OpenAI API - 可在模拟器运行", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String errorMsg = "OpenAI API 初始化失败: " + e.getMessage();
            Log.e("MainActivity", errorMsg, e);
            statusText.setText(errorMsg);
            btnParse.setEnabled(false);
            Toast.makeText(this, "API 初始化失败", Toast.LENGTH_SHORT).show();
        }
    }
}
