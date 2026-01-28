package com.example.philotes;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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
    private static final boolean USE_MOCK_ONLY = true;

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
        startForeground(1, createNotification());

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
            Toast.makeText(this, "动作已执行 (Mock)", Toast.LENGTH_SHORT).show();
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
        if (USE_MOCK_ONLY) {
            processAndShowCard(null);
            return;
        }
        floatingView.setVisibility(View.GONE);
        // 给一点时间让悬浮球消失，避免出现在截屏中
        new Handler(Looper.getMainLooper()).postDelayed(this::performCapture, 150);
    }

    private void performCapture() {
        if (USE_MOCK_ONLY) {
            processAndShowCard(null);
            return;
        }
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
                                    recoverFloatingView();
                                }
                            } else {
                                Log.e(TAG, "Bitmap wrapHardwareBuffer returned null");
                                recoverFloatingView();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing screenshot result", e);
                            recoverFloatingView();
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        Log.e(TAG, "Screenshot failed with code: " + errorCode);
                        Toast.makeText(FloatingButtonService.this, "截屏失败: " + errorCode, Toast.LENGTH_SHORT).show();
                        recoverFloatingView();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "takeScreenshot threw exception", e);
                recoverFloatingView();
            }
        } else {
            Toast.makeText(this, "当前系统版本不支持直接截屏分析", Toast.LENGTH_SHORT).show();
            recoverFloatingView();
        }
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
        // 在实际开发中，这里会调用 AI 接口分析图片
        // 模拟分析过程
        new Handler(Looper.getMainLooper()).post(() -> {
            showCardMode("正在深度分析屏幕内容...");

            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 模拟网络延迟或 AI 计算耗时

                    String[] mockResults = {
                        "识别到日程安排：\n\n- 时间：本周五下午 14:00\n- 内容：与产品经理对接需求\n- 地点：3号会议室\n- 建议：点击“执行”添加到日历",
                        "识别到餐厅信息：\n\n- 店名：炭火烤肉(人民路店)\n- 地址：人民路 123 号\n- 动作：点击“执行”开启导航",
                        "识别到待办事项：\n\n- 项目：Philotes APP 开发\n- 任务：完善 LLM 接口对接\n- 优先级：高\n- 动作：点击“执行”添加到待办列表",
                        "发现优惠活动：\n\n- 内容：某咖啡买一送一券\n- 有效期：截止今晚 23:59\n- 动作：点击“执行”保存到优惠券包"
                    };

                    String randomResult = mockResults[(int) (Math.random() * mockResults.length)];

                    new Handler(Looper.getMainLooper()).post(() -> showCardMode(randomResult));
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
