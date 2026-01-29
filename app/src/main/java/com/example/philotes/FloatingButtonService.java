package com.example.philotes;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.OcrResult;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.utils.MlKitOcrService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FloatingButtonService extends AccessibilityService {
    private static final String CHANNEL_ID = "FloatingButtonServiceChannel";
    private static final String TAG = "FloatingButtonService";

    private WindowManager windowManager;
    private View floatingView;
    private View iconView;
    private View cardView;
    private TextView tvCardContent;
    private WindowManager.LayoutParams params;

    private boolean isFloatingViewAdded = false;

    // AI组件
    private ActionParser actionParser;
    private ActionExecutor actionExecutor;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始化AI组件
        initAiComponents();

        createNotificationChannel();
        // 启动前台服务以保持存活
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, createNotification());
        }

        initFloatingView();

        Toast.makeText(this, "悬浮截屏服务已启动", Toast.LENGTH_SHORT).show();
    }

    private void initAiComponents() {
        try {
            // 加载用户设置
            com.example.philotes.utils.AiSettingsManager settingsManager =
                new com.example.philotes.utils.AiSettingsManager(this);
            settingsManager.applyToLlmConfig();

            // 初始化ActionExecutor
            actionExecutor = new ActionExecutor(this);

            // 初始化ActionParser - 尝试使用云端API或端侧模型
            if (settingsManager.isCloudApiMode() && settingsManager.isApiConfigured()) {
                // 使用云端API
                String apiKey = com.example.philotes.utils.LlmConfig.getOpenAiApiKey();
                String baseUrl = com.example.philotes.utils.LlmConfig.getOpenAiBaseUrl();
                String model = com.example.philotes.utils.LlmConfig.getOpenAiModel();

                com.example.philotes.data.api.OpenAIService openAiService =
                    new com.example.philotes.data.api.OpenAIService(apiKey, baseUrl, model);
                actionParser = new ActionParser(openAiService);

                Log.i(TAG, "AI initialized with Cloud API: " + model);
            } else {
                Log.w(TAG, "AI not initialized - need API configuration");
                // 可以选择初始化端侧模型，但需要模型文件
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AI components", e);
        }
    }

    private void initFloatingView() {
        if (isFloatingViewAdded) return;

        Context themedContext = new android.view.ContextThemeWrapper(this, R.style.Theme_Philotes);
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_button, null);

        iconView = floatingView.findViewById(R.id.floating_button);
        cardView = floatingView.findViewById(R.id.card_result);
        tvCardContent = floatingView.findViewById(R.id.tv_card_content);
        View btnClose = floatingView.findViewById(R.id.btn_close_card);
        View btnAction = floatingView.findViewById(R.id.btn_action);

        btnClose.setOnClickListener(v -> showIconMode());
        btnAction.setOnClickListener(v -> {
            Toast.makeText(this, "动作已执行", Toast.LENGTH_SHORT).show();
            showIconMode();
        });

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        try {
            windowManager.addView(floatingView, params);
            isFloatingViewAdded = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating view", e);
        }

        iconView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long startTime;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTime = System.currentTimeMillis();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - startTime;
                        if (duration < 200) {
                            onFloatingButtonClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showIconMode() {
        if (floatingView == null) return;
        floatingView.setVisibility(View.VISIBLE);
        iconView.setVisibility(View.VISIBLE);
        cardView.setVisibility(View.GONE);
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception e) {
            Log.e(TAG, "updateViewLayout failed", e);
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Philotes助手")
                .setContentText("服务已就绪")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Button Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void onFloatingButtonClick() {
        if (floatingView == null) return;
        floatingView.setVisibility(View.GONE);
        // 给一点时间让悬浮球消失，避免出现在截屏中
        new Handler(Looper.getMainLooper()).postDelayed(this::performCapture, 150);
    }

    private void performCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull ScreenshotResult result) {
                        try (android.hardware.HardwareBuffer hardwareBuffer = result.getHardwareBuffer()) {
                            Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
                            if (bitmap != null) {
                                // 必须复制硬件 Bitmap 到软件 Bitmap，因为 HardwareBuffer 会失效
                                // 而且硬件 Bitmap 不支持某些操作（如 compress 在旧版本上）
                                Bitmap softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                                bitmap.recycle();
                                if (softwareBitmap != null) {
                                    processBitmap(softwareBitmap);
                                } else {
                                    Log.e(TAG, "Failed to copy hardware bitmap to software");
                                    showErrorAndRecover("截屏失败：无法处理图像");
                                }
                            } else {
                                Log.e(TAG, "Bitmap wrapHardwareBuffer returned null");
                                showErrorAndRecover("截屏失败：图像为空");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing screenshot result", e);
                            showErrorAndRecover("截屏处理失败");
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        Log.e(TAG, "Screenshot failed with code: " + errorCode);
                        showErrorAndRecover("截屏失败（错误码: " + errorCode + "）\n请确保已授予截屏权限");
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "takeScreenshot security exception - missing permission", e);
                showErrorAndRecover("截屏权限未授予\n请重新开启辅助功能服务");
            } catch (Exception e) {
                Log.e(TAG, "takeScreenshot threw exception", e);
                showErrorAndRecover("截屏失败：" + e.getMessage());
            }
        } else {
            showErrorAndRecover("当前系统版本不支持直接截屏分析\n需要 Android 11+");
        }
    }

    private void showErrorAndRecover(String errorMessage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(FloatingButtonService.this, errorMessage, Toast.LENGTH_LONG).show();
            recoverFloatingView();
        });
    }

    private void processBitmap(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            if (!cachePath.exists()) cachePath.mkdirs();
            File file = new File(cachePath, "screenshot_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
            processAndShowCard(file);
        } catch (IOException e) {
            Log.e(TAG, "Save bitmap failed", e);
            recoverFloatingView();
        } finally {
            bitmap.recycle();
        }
    }

    private void recoverFloatingView() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (floatingView != null) {
                floatingView.setVisibility(View.VISIBLE);
                showIconMode();
            }
        });
    }

    private void processAndShowCard(File imageFile) {
        // 使用实际的 AI 接口分析图片
        new Handler(Looper.getMainLooper()).post(() -> {
            showCardMode("正在识别屏幕文字...");

            new Thread(() -> {
                try {
                    // 1. 加载图片
                    final Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                    if (bitmap == null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                            showCardMode("图片加载失败"));
                        return;
                    }

                    // 2. 创建可变的Bitmap副本，确保ML Kit可以安全访问
                    final Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    bitmap.recycle(); // 立即释放原始bitmap

                    if (mutableBitmap == null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                            showCardMode("图片处理失败"));
                        return;
                    }

                    // 3. 使用ML Kit进行OCR识别
                    new Handler(Looper.getMainLooper()).post(() -> {
                        MlKitOcrService.recognizeTextAsync(mutableBitmap,
                            new MlKitOcrService.OcrCallback() {
                                @Override
                                public void onSuccess(OcrResult result) {
                                    // OCR完成后释放bitmap
                                    mutableBitmap.recycle();

                                    if (result.getTextBlocks().isEmpty()) {
                                        showCardMode("未识别到文字\n请确保截图中包含清晰的文本内容");
                                        return;
                                    }

                                    // OCR成功，继续AI解析
                                    String ocrText = result.toStructuredText();
                                    showCardMode("✅ 识别成功\n\n正在AI分析...");

                                    // 4. 自动进行AI解析
                                    performAiAnalysis(ocrText, result);
                                }

                                @Override
                                public void onError(Exception e) {
                                    // 发生错误时也要释放bitmap
                                    mutableBitmap.recycle();

                                    Log.e(TAG, "OCR error", e);
                                    showCardMode("OCR识别失败\n" + e.getMessage() +
                                        "\n\n可能原因：\n" +
                                        "1. 图片中没有清晰的文字\n" +
                                        "2. 首次使用需联网下载模型");
                                }
                            });
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Analysis error", e);
                    new Handler(Looper.getMainLooper()).post(() ->
                        showCardMode("分析失败: " + e.getMessage()));
                }
            }).start();
        });
    }

    private void performAiAnalysis(String ocrText, OcrResult ocrResult) {
        if (actionParser == null) {
            // AI未初始化，显示文本并提供手动选项
            String plainText = ocrResult.getPlainText();
            showCardMode("✅ 识别成功\n\n" + plainText +
                "\n\n⚠️ AI未配置\n点击「查看」跳转主界面手动解析");

            // 设置按钮点击跳转
            setupCardActionButton(() -> {
                Intent intent = new Intent(FloatingButtonService.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, ocrText);
                startActivity(intent);
                new Handler(Looper.getMainLooper()).postDelayed(() -> showIconMode(), 500);
            });
            return;
        }

        // 在后台线程执行AI解析
        new Thread(() -> {
            try {
                ActionPlan actionPlan = actionParser.parse(ocrText);

                if (actionPlan == null || actionPlan.getType() == com.example.philotes.data.model.ActionType.UNKNOWN) {
                    // AI解析失败
                    new Handler(Looper.getMainLooper()).post(() -> {
                        String plainText = ocrResult.getPlainText();
                        showCardMode("✅ 识别成功\n\n" + plainText +
                            "\n\n⚠️ AI无法理解内容\n点击「查看」跳转主界面");

                        setupCardActionButton(() -> {
                            Intent intent = new Intent(FloatingButtonService.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setAction(Intent.ACTION_SEND);
                            intent.putExtra(Intent.EXTRA_TEXT, ocrText);
                            startActivity(intent);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> showIconMode(), 500);
                        });
                    });
                    return;
                }

                // AI解析成功，显示ActionPlan
                new Handler(Looper.getMainLooper()).post(() -> {
                    displayActionPlan(actionPlan);
                });

            } catch (Exception e) {
                Log.e(TAG, "AI analysis error", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    showCardMode("AI分析失败\n" + e.getMessage());
                });
            }
        }).start();
    }

    private void displayActionPlan(ActionPlan plan) {
        StringBuilder displayText = new StringBuilder();
        displayText.append("🎯 AI分析结果\n\n");

        // 显示动作类型
        switch (plan.getType()) {
            case CREATE_CALENDAR:
                displayText.append("📅 创建日历事件\n\n");
                if (plan.getSlots().containsKey("title")) {
                    displayText.append("标题：").append(plan.getSlots().get("title")).append("\n");
                }
                if (plan.getSlots().containsKey("time")) {
                    displayText.append("时间：").append(plan.getSlots().get("time")).append("\n");
                }
                if (plan.getSlots().containsKey("location")) {
                    displayText.append("地点：").append(plan.getSlots().get("location")).append("\n");
                }
                break;

            case NAVIGATE:
                displayText.append("🗺️ 导航到目的地\n\n");
                if (plan.getSlots().containsKey("location")) {
                    displayText.append("目的地：").append(plan.getSlots().get("location")).append("\n");
                }
                break;

            case ADD_TODO:
                displayText.append("✅ 添加待办事项\n\n");
                if (plan.getSlots().containsKey("title")) {
                    displayText.append("内容：").append(plan.getSlots().get("title")).append("\n");
                }
                break;

            case COPY_TEXT:
                displayText.append("📋 复制文本\n\n");
                if (plan.getSlots().containsKey("content")) {
                    displayText.append("内容：").append(plan.getSlots().get("content")).append("\n");
                }
                break;

            default:
                displayText.append("❓ 未知动作\n");
                break;
        }

        displayText.append("\n点击「执行」立即执行此动作");
        showCardMode(displayText.toString());

        // 设置执行按钮
        setupCardActionButton(() -> {
            executeActionPlan(plan);
        });
    }

    private void executeActionPlan(ActionPlan plan) {
        showCardMode("正在执行...");

        new Thread(() -> {
            ActionExecutor.ExecutionResult result = actionExecutor.execute(plan);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (result.success) {
                    showCardMode("✅ 执行成功！\n\n" + result.message);
                    Toast.makeText(FloatingButtonService.this, result.message, Toast.LENGTH_LONG).show();

                    // 3秒后自动隐藏
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        showIconMode();
                    }, 3000);
                } else {
                    showCardMode("❌ 执行失败\n\n" + result.message);

                    // 5秒后自动隐藏
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        showIconMode();
                    }, 5000);
                }
            });
        }).start();
    }

    private void setupCardActionButton(Runnable action) {
        View btnAction = cardView.findViewById(R.id.btn_action);
        btnAction.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
        });
    }

    private void showCardMode(String content) {
        if (floatingView == null) return;
        floatingView.setVisibility(View.VISIBLE);
        tvCardContent.setText(content);
        iconView.setVisibility(View.GONE);
        cardView.setVisibility(View.VISIBLE);
        // 卡片模式可以需要更大的宽度
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception e) {
            Log.e(TAG, "updateViewLayout failed", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && isFloatingViewAdded) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {}
        }
    }

    // AccessibilityService doesn't use onBind like a regular service for other apps
    // But it's final in AccessibilityService so we don't override it improperly.
    // The super class handles it.
}
