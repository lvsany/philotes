package com.example.philotes;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
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
            showCardMode("正在深度分析屏幕内容...");

            new Thread(() -> {
                try {
                    // TODO: 集成实际的 OCR 和 AI 分析服务
                    // 1. 对图片进行 OCR 识别
                    // 2. 使用 AI 模型解析文本内容
                    // 3. 生成 ActionPlan

                    Thread.sleep(2000); // AI 处理耗时

                    // 暂时显示处理中的状态
                    String result = "图片分析功能正在开发中\n请使用主界面的文本输入功能";

                    new Handler(Looper.getMainLooper()).post(() -> showCardMode(result));
                } catch (InterruptedException e) {
                    Log.e(TAG, "Analysis interrupted", e);
                }
            }).start();
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
