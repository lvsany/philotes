package com.example.philotes;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.api.LiteRtQwenService;
import com.example.philotes.data.api.LiteRtLocalLlmService;
import com.example.philotes.data.api.RoutedLlmService;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.input.MultimodalInputCoordinator;
import com.example.philotes.render.CardRenderEngine;
import com.example.philotes.render.CardRenderEvent;
import com.example.philotes.utils.ModelUtils;
import com.example.philotes.utils.PaddleOcrService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 主活动
 * 集成日历、导航、待办三个核心功能
 * 使用 ActionParser 解析文本，ActionExecutor 执行动作
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_DESTINATION = "extra_destination";
    public static final String EXTRA_ACTION_PLAN_JSON = "extra_action_plan_json";
    public static final String DEST_HOME = "home";
    public static final String DEST_SETTINGS = "settings";
    public static final String DEST_ACTION_DETAIL = "action_detail";

    // --- UI Components ---
    private TextView statusText;
    private RecyclerView rvActionCards;
    private ActionCardAdapter actionCardAdapter;
    private final List<ActionCardItem> actionCardList = new ArrayList<>();

    // LLM AI Components
    private EditText etInput;
    private Button btnParse;

    // Download UI
    private LinearLayout layoutDownload;
    private ProgressBar progressBar;
    private Button btnDownload;
    private TextView tvDownloadStatus;
    private BottomNavigationView bottomNav;
    private View homeContent;
    private View fragmentContainer;

    // 核心组件
    private ActionParser actionParser;
    private ActionExecutor actionExecutor;
    private MultimodalInputCoordinator inputCoordinator;
    private CardRenderEngine cardRenderEngine;

    // 权限请求启动器
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ActionPlan pendingActionPlan; // 等待权限授予后执行的 ActionPlan

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ========== 加载用户设置 ==========
        com.example.philotes.utils.AiSettingsManager settingsManager = new com.example.philotes.utils.AiSettingsManager(
                this);
        settingsManager.applyToLlmConfig();
        // ==================================

        // 初始化执行器
        actionExecutor = new ActionExecutor(this);
        refreshInputCoordinator();

        // 初始化视图
        initViews();
        cardRenderEngine = new CardRenderEngine();
        observeRenderEvents();

        // 初始化权限请求
        initPermissionLauncher();

        // 设置点击事件
        setupClickListeners();

        // 检查 Intent 是否包含分享内容
        handleIntent(getIntent());
        handleNavigationIntent(getIntent());

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

        // Handle system back (including gesture back) with AndroidX dispatcher.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fragmentContainer != null && fragmentContainer.getVisibility() == View.VISIBLE) {
                    navigateToHomeTab();
                    return;
                }
                // Delegate to default back behavior when custom handling is not needed.
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
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

        bottomNav = findViewById(R.id.bottomNav);
        homeContent = findViewById(R.id.homeContent);
        fragmentContainer = findViewById(R.id.fragmentContainer);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) {
                showHomeContent();
                return true;
            }
            if (item.getItemId() == R.id.nav_settings) {
                showSettingsContent();
                return true;
            }
            return false;
        });

        // 卡片列表
        rvActionCards = findViewById(R.id.rvActionCards);
        rvActionCards.setLayoutManager(new LinearLayoutManager(this));
        actionCardAdapter = new ActionCardAdapter(actionCardList, new ActionCardAdapter.OnActionClickListener() {
            @Override
            public void onExecute(ActionPlan plan) {
                executeAction(plan);
            }

            @Override
            public void onEdit(ActionPlan plan) {
                showActionDetailContent(plan);
            }
        });
        rvActionCards.setAdapter(actionCardAdapter);

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
        if (inputCoordinator == null || !inputCoordinator.canParse()) {
            Toast.makeText(this, "模型未加载，请先下载并初始化模型", Toast.LENGTH_SHORT).show();
            return;
        }

        String requestId = UUID.randomUUID().toString();
        statusText.setText("正在流式解析...");
        cardRenderEngine.startTextRender(requestId, text, inputCoordinator);
    }

    private void observeRenderEvents() {
        cardRenderEngine.getEvents().observe(this, event -> {
            if (event == null) {
                return;
            }

            if (event.getType() == CardRenderEvent.Type.LOADING) {
                actionCardAdapter.showStreamingCard(event.getRequestId());
                rvActionCards.scrollToPosition(0);
                statusText.setText("正在流式解析...");
                return;
            }

            if (event.getType() == CardRenderEvent.Type.STREAMING) {
                actionCardAdapter.updateStreamingCard(event.getRequestId(), event.getText());
                return;
            }

            if (event.getType() == CardRenderEvent.Type.CARD_READY) {
                ActionPlan plan = event.getActionPlan();
                int position = actionCardAdapter.replaceStreamingCard(event.getRequestId(), plan);
                rvActionCards.scrollToPosition(position);
                animateCardReveal(position);

                if (plan != null && plan.getType() == com.example.philotes.data.model.ActionType.UNKNOWN) {
                    statusText.setText("⚠️ 暂未识别到明确动作\n\n可尝试输入更具体的任务描述（时间/地点/待办）。");
                } else {
                    statusText.setText("流式解析中，已更新卡片...");
                }
                return;
            }

            if (event.getType() == CardRenderEvent.Type.ERROR) {
                actionCardAdapter.removeCardByRequestId(event.getRequestId());
                String errorText = event.getText() == null ? "解析失败" : event.getText();
                statusText.setText("解析失败: " + errorText);
                Toast.makeText(this, "解析失败: " + errorText, Toast.LENGTH_LONG).show();
                return;
            }

            if (event.getType() == CardRenderEvent.Type.COMPLETED) {
                statusText.setText("解析完成");
            }
        });
    }

    private void animateCardReveal(int position) {
        RecyclerView.ViewHolder holder = rvActionCards.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            rvActionCards.post(() -> animateCardReveal(position));
            return;
        }

        View itemView = holder.itemView;
        itemView.setAlpha(0f);
        itemView.setScaleX(0.96f);
        itemView.setScaleY(0.96f);
        itemView.setTranslationY(40f);

        SpringAnimation springY = new SpringAnimation(itemView, DynamicAnimation.TRANSLATION_Y, 0f);
        SpringForce spring = new SpringForce(0f);
        spring.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        spring.setStiffness(SpringForce.STIFFNESS_LOW);
        springY.setSpring(spring);
        springY.start();

        itemView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .start();
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
            ActionExecutor.ExecutionResult result = inputCoordinator == null
                    ? new ActionExecutor.ExecutionResult(false, "执行器未初始化")
                    : inputCoordinator.execute(plan);

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
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
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
        if (intent == null)
            return;
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
            ((android.widget.ImageView) findViewById(R.id.screenshotPreview)).setImageURI(imageUri);

            updateStatus("正在进行 OCR 识别...");

            // 使用 PaddleOCR-Lite 识别图片文本
            try {
                Bitmap originalBitmap;
                if (imagePath != null) {
                    originalBitmap = BitmapFactory.decodeFile(imagePath);
                } else {
                    java.io.InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    originalBitmap = BitmapFactory.decodeStream(inputStream);
                    if (inputStream != null)
                        inputStream.close();
                }

                if (originalBitmap != null) {
                    // 创建可变的 Bitmap 副本，确保 OCR 引擎可以安全读取像素
                    final Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                    originalBitmap.recycle(); // 立即释放原始bitmap

                    if (mutableBitmap == null) {
                        updateStatus("图片处理失败");
                        Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    PaddleOcrService.recognizeTextAsync(MainActivity.this, mutableBitmap,
                            new PaddleOcrService.OcrCallback() {
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
        if (modelFile.getName().endsWith(".tflite")) {
            runLiteRtSmokeTest(modelFile);
            return;
        }

        // 获取用户设置
        com.example.philotes.utils.AiSettingsManager settingsManager = new com.example.philotes.utils.AiSettingsManager(
                this);

        // 用户设置优先：如果路由策略需要云端且已配置
        if (settingsManager.needsCloudConfig() && settingsManager.isApiConfigured()) {
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
            com.example.philotes.data.api.OnDeviceLlmService llmService = new com.example.philotes.data.api.OnDeviceLlmService(
                    this, modelFile.getAbsolutePath());

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
                actionParser = new ActionParser(new RoutedLlmService(this));
                refreshInputCoordinator();
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

    private void runLiteRtSmokeTest(File modelFile) {
        new Thread(() -> {
            LiteRtQwenService liteRtService = new LiteRtQwenService(modelFile);
            try {
                String result = liteRtService.runSmokeTest();
                runOnUiThread(() -> {
                        actionParser = new ActionParser(new RoutedLlmService(this));
                    refreshInputCoordinator();

                    statusText.setText("✅ LiteRT 本地推理链路已打通\n" + result +
                            "\n\n本地动作解析已启用（无需配置 API）。");
                    btnParse.setEnabled(true);
                    btnParse.setText("AI 解析（本地LiteRT）");
                    etInput.setEnabled(true);
                    etInput.setHint("输入文本进行本地动作解析");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("❌ LiteRT 推理测试失败\n" + e.getMessage() +
                            "\n\n请确认下载的是可用的 Qwen LiteRT .tflite 文件（默认: Qwen3.5-0.8B-LiteRT）。\n" +
                            "当前模型路径: " + modelFile.getAbsolutePath());
                    btnParse.setEnabled(false);
                });
            } finally {
                liteRtService.close();
            }
        }).start();
    }

    /**
     * 初始化 OpenAI API 服务
     */
    private void initOpenAiService() {
        try {
            String apiKey = com.example.philotes.utils.LlmConfig.getOpenAiApiKey();
            String baseUrl = com.example.philotes.utils.LlmConfig.getOpenAiBaseUrl();
            String model = com.example.philotes.utils.LlmConfig.getOpenAiModel();

                actionParser = new ActionParser(new RoutedLlmService(this));
            refreshInputCoordinator();

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

    private void refreshInputCoordinator() {
        inputCoordinator = new MultimodalInputCoordinator(actionParser, actionExecutor);
    }

    private void showHomeContent() {
        homeContent.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
        Fragment existing = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (existing != null) {
            getSupportFragmentManager().beginTransaction().remove(existing).commitAllowingStateLoss();
        }
    }

    private void showSettingsContent() {
        homeContent.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new SettingsFragment())
                .commit();
    }

    private void showActionDetailContent(ActionPlan plan) {
        if (plan == null) {
            Toast.makeText(this, "动作数据为空", Toast.LENGTH_SHORT).show();
            return;
        }
        homeContent.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, ActionDetailFragment.newInstance(new com.google.gson.Gson().toJson(plan)))
                .commit();
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    public void navigateToHomeTab() {
        bottomNav.setSelectedItemId(R.id.nav_home);
        showHomeContent();
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String destination = intent.getStringExtra(EXTRA_DESTINATION);
        if (destination == null) {
            return;
        }

        if (DEST_SETTINGS.equals(destination)) {
            bottomNav.setSelectedItemId(R.id.nav_settings);
            showSettingsContent();
            return;
        }

        if (DEST_ACTION_DETAIL.equals(destination)) {
            String planJson = intent.getStringExtra(EXTRA_ACTION_PLAN_JSON);
            if (planJson != null && !planJson.isEmpty()) {
                try {
                    ActionPlan plan = new com.google.gson.Gson().fromJson(planJson, ActionPlan.class);
                    showActionDetailContent(plan);
                } catch (Exception ignored) {
                    showHomeContent();
                }
            } else {
                showHomeContent();
            }
            return;
        }

        showHomeContent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        handleNavigationIntent(intent);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cardRenderEngine != null) {
            cardRenderEngine.release();
        }
    }
}
