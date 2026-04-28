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
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.OcrResult;
import com.example.philotes.data.api.RoutedLlmService;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.domain.ActionParser;
import com.example.philotes.domain.PrivacyFirewall;
import com.example.philotes.domain.RuleEngine;
import com.example.philotes.input.MultimodalInputCoordinator;
import com.example.philotes.ui.AiStateOrbView;
import com.example.philotes.utils.ContextEnricher;
import com.example.philotes.utils.PaddleOcrService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FloatingButtonService extends AccessibilityService {
    private static final String CHANNEL_ID = "FloatingButtonServiceChannel";
    private static final String TAG = "FloatingButtonService";

    private static final long UI_STABLE_DEBOUNCE_MS = 1000L;
    private static final long MIN_ANALYZE_INTERVAL_MS = 1500L;
    private static final long MANUAL_OCR_TIMEOUT_MS = 15000L;
    private static final int MAX_TRAVERSE_NODES = 220;
    private static final int MAX_EXTRACT_TEXT_CHARS = 1500;
    private static final int MAX_TEXT_PER_NODE = 80;

    private WindowManager windowManager;
    private View floatingView;
    private View iconView;
    private View cardView;
    private TextView tvCardContent;
    private AiStateOrbView aiStateOrb;
    private WindowManager.LayoutParams params;
    private View inlineBannerView;
    private WindowManager.LayoutParams inlineBannerParams;

    private boolean isFloatingViewAdded = false;
    private boolean isInlineBannerAdded = false;

    // AI组件
    private ActionParser actionParser;
    private ActionExecutor actionExecutor;
    private MultimodalInputCoordinator inputCoordinator;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastUiEventAt;
    private long lastAnalyzeAt;
    private String lastScreenFingerprint = "";
    private String lastRuleHitFingerprint = "";
    private String lastSilentFallbackFingerprint = "";
    private volatile boolean silentFallbackRunning;
    private volatile boolean manualCaptureInProgress;
    private Runnable manualOcrTimeoutRunnable;

    private final List<ActionPlan> pendingActionPlans = new ArrayList<>();
    private int currentPlanIndex = 0;
    private String lastMatchedKeyword = "";
    private RuleEngine ruleEngine;

    // 情境感知：追踪当前前台应用包名
    private String currentFrontPackage = "";


    private View layoutPlanNav;
    private TextView tvPlanIndicator;

    private final Runnable debounceAnalyzeRunnable = this::analyzeScreenAfterIdle;
    private final Runnable hideInlineBannerRunnable = this::hideInlineBanner;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        if (manualCaptureInProgress) {
            return;
        }

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        // 追踪当前前台应用包名，供隐私防火墙与情境感知使用
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && pkg.length() > 0) {
                currentFrontPackage = pkg.toString();
            }
        }

        lastUiEventAt = SystemClock.uptimeMillis();
        mainHandler.removeCallbacks(debounceAnalyzeRunnable);
        mainHandler.postDelayed(debounceAnalyzeRunnable, UI_STABLE_DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {
        // no-op
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ruleEngine = RuleEngine.getInstance();

        // 初始化AI组件
        initAiComponents();

        createNotificationChannel();
        // 启动前台服务以保持存活
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
            com.example.philotes.utils.AiSettingsManager settingsManager = new com.example.philotes.utils.AiSettingsManager(
                    this);
            settingsManager.applyToLlmConfig();
            applyRuleEngineSettings(settingsManager);

            // 初始化ActionExecutor
            actionExecutor = new ActionExecutor(this);

            actionParser = new ActionParser(new RoutedLlmService(this));
            Log.i(TAG, "AI initialized with routed policy: " + settingsManager.getRoutingPolicy());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AI components", e);
        } finally {
            inputCoordinator = new MultimodalInputCoordinator(actionParser, actionExecutor);
        }
    }

    private void applyRuleEngineSettings(com.example.philotes.utils.AiSettingsManager settingsManager) {
        if (ruleEngine == null) {
            return;
        }
        // 先加载默认关键词，再追加用户自定义词（合并模式，防止用户配置覆盖默认规则）
        ruleEngine.resetDefaultRules();
        List<String> customKeywords = new ArrayList<>();
        for (String kw : settingsManager.getCustomTriggerKeywords()) {
            if (kw != null && !kw.trim().isEmpty()) {
                customKeywords.add(kw.trim().toLowerCase());
            }
        }
        ruleEngine.addCustomKeywords(customKeywords);
    }

    private void initFloatingView() {
        if (isFloatingViewAdded)
            return;

        Context themedContext = new android.view.ContextThemeWrapper(this, R.style.Theme_Philotes);
        floatingView = LayoutInflater.from(themedContext).inflate(
                R.layout.layout_floating_button,
                new android.widget.FrameLayout(themedContext),
                false);

        iconView = floatingView.findViewById(R.id.floating_button);
        cardView = floatingView.findViewById(R.id.card_result);
        tvCardContent = floatingView.findViewById(R.id.tv_card_content);
        aiStateOrb = floatingView.findViewById(R.id.aiStateOrb);
        layoutPlanNav = floatingView.findViewById(R.id.layout_plan_nav);
        tvPlanIndicator = floatingView.findViewById(R.id.tv_plan_indicator);
        View cardDragHandle = floatingView.findViewById(R.id.card_drag_handle);
        View btnClose = floatingView.findViewById(R.id.btn_close_card);
        View btnAction = floatingView.findViewById(R.id.btn_action);
        View btnClear = floatingView.findViewById(R.id.btn_clear);
        View btnPrev = floatingView.findViewById(R.id.btn_prev_plan);
        View btnNext = floatingView.findViewById(R.id.btn_next_plan);

        setOrbState(AiStateOrbView.State.IDLE);

        btnClose.setOnClickListener(v -> showIconMode());
        btnClear.setOnClickListener(v -> {
            pendingActionPlans.clear();
            currentPlanIndex = 0;
            hideInlineBanner();
            setOrbState(AiStateOrbView.State.IDLE);
            showIconMode();
        });
        btnAction.setOnClickListener(v -> {
            if (!pendingActionPlans.isEmpty() && currentPlanIndex < pendingActionPlans.size()) {
                executeActionPlan(pendingActionPlans.get(currentPlanIndex));
            }
        });
        btnPrev.setOnClickListener(v -> {
            if (!pendingActionPlans.isEmpty()) {
                currentPlanIndex = (currentPlanIndex - 1 + pendingActionPlans.size()) % pendingActionPlans.size();
                showPlanAt(currentPlanIndex);
            }
        });
        btnNext.setOnClickListener(v -> {
            if (!pendingActionPlans.isEmpty()) {
                currentPlanIndex = (currentPlanIndex + 1) % pendingActionPlans.size();
                showPlanAt(currentPlanIndex);
            }
        });

        int layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

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

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
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
                        int movedX = Math.abs(params.x - initialX);
                        int movedY = Math.abs(params.y - initialY);
                        if (movedX < dp(8) && movedY < dp(8)) {
                            v.performClick();
                            onFloatingButtonClick();
                        }
                        snapToEdge();
                        return true;
                }
                return false;
            }
        });

        cardDragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(floatingView, params);
                        } catch (Exception e) {
                            Log.e(TAG, "card drag updateViewLayout failed", e);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        snapToEdge();
                        return true;
                }
                return false;
            }
        });
    }

    private void showIconMode() {
        if (floatingView == null)
            return;
        manualCaptureInProgress = false;
        cancelManualOcrTimeout();
        floatingView.setVisibility(View.VISIBLE);
        iconView.setVisibility(View.VISIBLE);
        cardView.setVisibility(View.GONE);
        setOrbState(!pendingActionPlans.isEmpty() ? AiStateOrbView.State.READY : AiStateOrbView.State.IDLE);
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
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void onFloatingButtonClick() {
        if (floatingView == null)
            return;

        if (manualCaptureInProgress) {
            return;
        }

        if (!pendingActionPlans.isEmpty()) {
            List<ActionPlan> plans = new ArrayList<>(pendingActionPlans);
            pendingActionPlans.clear();
            currentPlanIndex = 0;
            hideInlineBanner();
            displayActionPlans(plans);
            return;
        }

        setOrbState(AiStateOrbView.State.CAPTURING);
        manualCaptureInProgress = true;
        floatingView.setVisibility(View.GONE);
        // 给一点时间让悬浮球消失，避免出现在截屏中
        mainHandler.postDelayed(this::performCapture, 150);
    }

    private void analyzeScreenAfterIdle() {
        if (manualCaptureInProgress) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastUiEventAt < UI_STABLE_DEBOUNCE_MS) {
            mainHandler.postDelayed(debounceAnalyzeRunnable, UI_STABLE_DEBOUNCE_MS);
            return;
        }
        if (now - lastAnalyzeAt < MIN_ANALYZE_INTERVAL_MS) {
            return;
        }
        lastAnalyzeAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }

        String mergedText;
        try {
            mergedText = extractVisibleTextBfs(root);
        } finally {
            root.recycle();
        }

        if (mergedText.isEmpty()) {
            return;
        }

        String fingerprint = Integer.toHexString(mergedText.hashCode());
        if (fingerprint.equals(lastScreenFingerprint)) {
            return;
        }
        lastScreenFingerprint = fingerprint;

        String matched = ruleEngine == null ? null : ruleEngine.findFirstMatchedKeyword(mergedText);
        if (matched == null) {
            if (pendingActionPlans.isEmpty()) {
                setOrbState(AiStateOrbView.State.IDLE);
            }
            return;
        }

        if (fingerprint.equals(lastRuleHitFingerprint)) {
            return;
        }
        lastRuleHitFingerprint = fingerprint;
        lastMatchedKeyword = matched;

        runDeepIntentAnalysis(mergedText, fingerprint);
    }

    private String extractVisibleTextBfs(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        StringBuilder merged = new StringBuilder(512);
        queue.offer(root);

        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_TRAVERSE_NODES && merged.length() < MAX_EXTRACT_TEXT_CHARS) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) {
                continue;
            }
            visited++;

            try {
                if (node.isVisibleToUser()) {
                    appendNodeText(merged, node.getText());
                    appendNodeText(merged, node.getContentDescription());
                }

                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        queue.offer(child);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse node", e);
            }

            if (node != root) {
                node.recycle();
            }
        }

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node != null && node != root) {
                node.recycle();
            }
        }

        return merged.toString().trim();
    }

    private void appendNodeText(StringBuilder merged, CharSequence candidate) {
        if (candidate == null || candidate.length() == 0) {
            return;
        }
        String value = candidate.toString().trim();
        if (value.isEmpty()) {
            return;
        }
        if (value.length() > MAX_TEXT_PER_NODE) {
            value = value.substring(0, MAX_TEXT_PER_NODE);
        }
        if (merged.indexOf(value) >= 0) {
            return;
        }
        if (merged.length() > 0) {
            merged.append('\n');
        }
        merged.append(value);
    }

    private void runDeepIntentAnalysis(String mergedText, String fingerprint) {
        if (inputCoordinator == null || !inputCoordinator.canParse()) {
            Log.d(TAG, "Skip proactive analysis: AI is not configured");
            return;
        }

        // === 动态隐私防火墙 ===
        PrivacyFirewall.PrivacyLevel privacyLevel =
            PrivacyFirewall.check(currentFrontPackage, mergedText);
        if (privacyLevel == PrivacyFirewall.PrivacyLevel.SENSITIVE) {
            Log.i(TAG, "PrivacyFirewall: SENSITIVE content detected, cloud requests blocked");
            showInlineBanner("🔒 隐私保护：本次推理已强制使用本地模型");
            // 仅在本地模型可用时继续，否则跳过
        }

        // === 情境感知：构建设备状态描述符 ===
        String contextDescriptor = ContextEnricher.buildContextDescriptor(this, currentFrontPackage);
        Log.d(TAG, "ContextDescriptor: " + contextDescriptor.replace("\n", " | "));

        setOrbState(AiStateOrbView.State.THINKING);

        new Thread(() -> {
            try {
                List<ActionPlan> plans = inputCoordinator.parseTextMultiple(
                        mergedText, lastMatchedKeyword, contextDescriptor);
                if (plans.isEmpty()) {
                    mainHandler.post(() -> trySilentOcrFallback(fingerprint));
                    return;
                }

                mainHandler.post(() -> {
                    pendingActionPlans.clear();
                    pendingActionPlans.addAll(plans);
                    lastRuleHitFingerprint = fingerprint;
                    setOrbState(AiStateOrbView.State.READY);
                    String msg = plans.size() > 1
                            ? "已发现 " + plans.size() + " 个可执行动作，点击悬浮球查看"
                            : "已发现可执行动作，点击悬浮球查看";
                    showInlineBanner(msg);
                });
            } catch (Exception e) {
                Log.e(TAG, "Proactive analysis failed", e);
                mainHandler.post(() -> {
                    if (pendingActionPlans.isEmpty()) {
                        setOrbState(AiStateOrbView.State.ERROR);
                        mainHandler.postDelayed(() -> setOrbState(AiStateOrbView.State.IDLE), 1000);
                    }
                });
            }
        }).start();
    }

    private void trySilentOcrFallback(String fingerprint) {
        if (manualCaptureInProgress) {
            return;
        }

        if (silentFallbackRunning) {
            return;
        }
        if (fingerprint != null && fingerprint.equals(lastSilentFallbackFingerprint)) {
            if (pendingActionPlans.isEmpty()) {
                setOrbState(AiStateOrbView.State.IDLE);
            }
            return;
        }
        lastSilentFallbackFingerprint = fingerprint == null ? "" : fingerprint;
        performSilentCaptureAndAnalyze();
    }

    private void performSilentCaptureAndAnalyze() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (pendingActionPlans.isEmpty()) {
                setOrbState(AiStateOrbView.State.IDLE);
            }
            return;
        }

        silentFallbackRunning = true;
        setOrbState(AiStateOrbView.State.THINKING);

        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull ScreenshotResult result) {
                    Bitmap softwareBitmap = null;
                    try {
                        android.hardware.HardwareBuffer hardwareBuffer = result.getHardwareBuffer();
                        Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
                        if (hardwareBitmap == null) {
                            hardwareBuffer.close();
                            onSilentFallbackFailed("silent screenshot bitmap null");
                            return;
                        }

                        softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true);
                        hardwareBitmap.recycle();
                        hardwareBuffer.close();

                        if (softwareBitmap == null) {
                            onSilentFallbackFailed("silent screenshot copy failed");
                            return;
                        }

                        if (softwareBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                            Bitmap tmp = softwareBitmap.copy(Bitmap.Config.ARGB_8888, true);
                            softwareBitmap.recycle();
                            softwareBitmap = tmp;
                        }

                        if (softwareBitmap == null || softwareBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                            onSilentFallbackFailed("silent screenshot remains hardware bitmap");
                            return;
                        }

                        runSilentOcrAnalysis(softwareBitmap);
                    } catch (Exception e) {
                        if (softwareBitmap != null && !softwareBitmap.isRecycled()) {
                            softwareBitmap.recycle();
                        }
                        onSilentFallbackFailed("silent screenshot processing exception: " + e.getClass().getSimpleName());
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    onSilentFallbackFailed("silent screenshot failed code=" + errorCode);
                }
            });
        } catch (Exception e) {
            onSilentFallbackFailed("silent screenshot exception: " + e.getClass().getSimpleName());
        }
    }

    private void runSilentOcrAnalysis(Bitmap bitmap) {
        PaddleOcrService.recognizeTextAsync(FloatingButtonService.this, bitmap, new PaddleOcrService.OcrCallback() {
            @Override
            public void onSuccess(OcrResult result) {
                bitmap.recycle();
                if (result == null || result.getTextBlocks().isEmpty()) {
                    onSilentFallbackCompletedWithoutAction();
                    return;
                }

                new Thread(() -> {
                    try {
                        String ctxDesc = ContextEnricher.buildContextDescriptor(
                                FloatingButtonService.this, currentFrontPackage);
                        List<ActionPlan> plans = inputCoordinator == null
                                ? Collections.emptyList()
                                : inputCoordinator.parseOcrMultiple(result, lastMatchedKeyword, ctxDesc);
                        if (plans.isEmpty()) {
                            onSilentFallbackCompletedWithoutAction();
                            return;
                        }

                        mainHandler.post(() -> {
                            pendingActionPlans.clear();
                            pendingActionPlans.addAll(plans);
                            setOrbState(AiStateOrbView.State.READY);
                            String msg = plans.size() > 1
                                    ? "已发现 " + plans.size() + " 个可执行动作，点击悬浮球查看"
                                    : "已发现可执行动作，点击悬浮球查看";
                            showInlineBanner(msg);
                            silentFallbackRunning = false;
                        });
                    } catch (Exception e) {
                        onSilentFallbackFailed("silent parseOcr failed: " + e.getClass().getSimpleName());
                    }
                }).start();
            }

            @Override
            public void onError(Exception e) {
                bitmap.recycle();
                onSilentFallbackFailed("silent OCR failed: " + (e == null ? "unknown" : e.getClass().getSimpleName()));
            }
        });
    }

    private void onSilentFallbackCompletedWithoutAction() {
        mainHandler.post(() -> {
            if (pendingActionPlans.isEmpty()) {
                setOrbState(AiStateOrbView.State.IDLE);
            }
            silentFallbackRunning = false;
        });
    }

    private void onSilentFallbackFailed(String reason) {
        Log.d(TAG, reason);
        mainHandler.post(() -> {
            if (pendingActionPlans.isEmpty()) {
                setOrbState(AiStateOrbView.State.IDLE);
            }
            silentFallbackRunning = false;
        });
    }

    private void showInlineBanner(String message) {
        if (windowManager == null) {
            return;
        }

        if (inlineBannerView == null) {
            TextView banner = new TextView(this);
            banner.setTextSize(14f);
            banner.setTypeface(Typeface.DEFAULT_BOLD);
            banner.setTextColor(0xFFFFFFFF);
            int paddingH = dp(14);
            int paddingV = dp(10);
            banner.setPadding(paddingH, paddingV, paddingH, paddingV);
            banner.setBackgroundResource(R.drawable.bg_inline_banner);
            banner.setOnClickListener(v -> {
                if (!pendingActionPlans.isEmpty()) {
                    onFloatingButtonClick();
                }
            });
            inlineBannerView = banner;

            inlineBannerParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            inlineBannerParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            inlineBannerParams.y = dp(70);
        }

        ((TextView) inlineBannerView).setText(message);

        try {
            if (!isInlineBannerAdded) {
                windowManager.addView(inlineBannerView, inlineBannerParams);
                isInlineBannerAdded = true;
            } else {
                windowManager.updateViewLayout(inlineBannerView, inlineBannerParams);
            }
        } catch (Exception e) {
            Log.w(TAG, "showInlineBanner failed", e);
        }

        mainHandler.removeCallbacks(hideInlineBannerRunnable);
        mainHandler.postDelayed(hideInlineBannerRunnable, 3000);
    }

    private void hideInlineBanner() {
        if (windowManager == null || inlineBannerView == null || !isInlineBannerAdded) {
            return;
        }
        try {
            windowManager.removeView(inlineBannerView);
            isInlineBannerAdded = false;
        } catch (Exception e) {
            Log.w(TAG, "hideInlineBanner failed", e);
        }
    }

    private void snapToEdge() {
        if (floatingView == null || windowManager == null) return;
        int screenWidth;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenWidth = windowManager.getCurrentWindowMetrics().getBounds().width();
        } else {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            //noinspection deprecation
            windowManager.getDefaultDisplay().getMetrics(dm);
            screenWidth = dm.widthPixels;
        }
        int viewWidth = floatingView.getWidth();
        int centerX = params.x + viewWidth / 2;
        params.x = (centerX < screenWidth / 2) ? 0 : screenWidth - viewWidth;
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception e) {
            Log.e(TAG, "snapToEdge failed", e);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void performCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(@NonNull ScreenshotResult result) {
                        Bitmap softwareBitmap = null;
                        try {
                            // 获取 HardwareBuffer 并立即转换为软件 Bitmap
                            android.hardware.HardwareBuffer hardwareBuffer = result.getHardwareBuffer();

                            // 包装硬件缓冲区为 Bitmap
                            Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
                            if (hardwareBitmap == null) {
                                Log.e(TAG, "Bitmap wrapHardwareBuffer returned null");
                                hardwareBuffer.close();
                                showErrorAndRecover("截屏失败：图像为空");
                                return;
                            }

                            // 立即复制到软件 Bitmap（使用 ARGB_8888 格式）
                            // 必须先将硬件 Bitmap 复制为软件 Bitmap，OCR 引擎与部分渲染路径不支持硬件 Bitmap
                            // 使用 copy() 方法而不是 Canvas，这样可以确保创建真正的软件 Bitmap
                            softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true);

                            // 立即释放硬件资源
                            hardwareBitmap.recycle();
                            hardwareBuffer.close();

                            // 验证是否成功创建软件 Bitmap
                            if (softwareBitmap == null) {
                                Log.e(TAG, "Failed to copy hardware bitmap to software bitmap");
                                showErrorAndRecover("截屏失败：无法转换图像格式");
                                return;
                            }

                            // 双重检查：如果仍然是硬件 Bitmap，再次转换
                            if (softwareBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                                Log.w(TAG, "Bitmap still in HARDWARE config, converting again...");
                                Bitmap temp = softwareBitmap.copy(Bitmap.Config.ARGB_8888, true);
                                softwareBitmap.recycle();
                                softwareBitmap = temp;
                                if (softwareBitmap == null || softwareBitmap.getConfig() == Bitmap.Config.HARDWARE) {
                                    Log.e(TAG, "Unable to convert hardware bitmap to software bitmap");
                                    if (softwareBitmap != null)
                                        softwareBitmap.recycle();
                                    showErrorAndRecover("截屏失败：设备不支持图像格式转换");
                                    return;
                                }
                            }

                            Log.d(TAG, "Successfully converted hardware bitmap to software bitmap: "
                                    + softwareBitmap.getWidth() + "x" + softwareBitmap.getHeight());

                            // 处理软件 Bitmap
                            processBitmap(softwareBitmap);

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing screenshot result", e);
                            if (softwareBitmap != null) {
                                softwareBitmap.recycle();
                            }
                            showErrorAndRecover("截屏处理失败: " + e.getMessage());
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
        mainHandler.post(() -> {
            manualCaptureInProgress = false;
            cancelManualOcrTimeout();
            Toast.makeText(FloatingButtonService.this, errorMessage, Toast.LENGTH_LONG).show();
            recoverFloatingView();
        });
    }

    private void processBitmap(Bitmap bitmap) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            if (!cachePath.exists() && !cachePath.mkdirs()) {
                Log.e(TAG, "Failed to create cache directory: " + cachePath.getAbsolutePath());
                recoverFloatingView();
                return;
            }
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
        mainHandler.post(() -> {
            manualCaptureInProgress = false;
            cancelManualOcrTimeout();
            if (floatingView != null) {
                floatingView.setVisibility(View.VISIBLE);
                showIconMode();
            }
        });
    }

    private void processAndShowCard(File imageFile) {
        mainHandler.post(() -> {
            showCardMode("正在识别屏幕文字...");
            startManualOcrTimeout();

            new Thread(() -> {
                Bitmap mutableBitmap = null;
                try {
                    // 1. 加载图片 - 使用 BitmapFactory.Options 确保格式正确
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    options.inMutable = false; // 先加载为不可变的

                    final Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                    if (bitmap == null) {
                        mainHandler.post(() -> showCardMode("图片加载失败\n文件路径：" + imageFile.getAbsolutePath()));
                        return;
                    }

                    Log.d(TAG, "Loaded bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + ", config: " + bitmap.getConfig() + ", isMutable: " + bitmap.isMutable());

                    // 2. 确保创建一个可变的、ARGB_8888 格式的 Bitmap 副本
                    // OCR 需要可以安全访问的像素数据，不能是 HARDWARE 配置
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                        Log.w(TAG, "Loaded bitmap is HARDWARE config, converting...");
                        mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        bitmap.recycle();
                    } else {
                        // 即使不是 HARDWARE，也创建一个 ARGB_8888 的可变副本
                        mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        bitmap.recycle();
                    }

                    if (mutableBitmap == null) {
                        mainHandler.post(() -> showCardMode("图片处理失败\n无法转换图像格式"));
                        return;
                    }

                    Log.d(TAG, "Created mutable bitmap for OCR: " + mutableBitmap.getWidth()
                            + "x" + mutableBitmap.getHeight());

                    final Bitmap finalBitmap = mutableBitmap;

                    // 3. 在主线程执行 PaddleOCR-Lite（内部会管理线程）
                    mainHandler.post(() -> {
                        PaddleOcrService.recognizeTextAsync(FloatingButtonService.this, finalBitmap,
                                new PaddleOcrService.OcrCallback() {
                                    @Override
                                    public void onSuccess(OcrResult result) {
                                        // OCR完成后释放bitmap
                                        finalBitmap.recycle();
                                        cancelManualOcrTimeout();
                                        Log.d(TAG, "OCR completed successfully");

                                        if (result.getTextBlocks().isEmpty()) {
                                            showCardMode("未识别到文字\n\n可能原因：\n" +
                                                    "1. 截图中没有清晰的文本\n" +
                                                    "2. 文字太小或模糊\n" +
                                                    "3. 文字颜色与背景对比度低");
                                            return;
                                        }

                                        // OCR成功，继续AI解析
                                        String ocrText = result.toStructuredText();
                                        Log.d(TAG, "OCR text length: " + ocrText.length());
                                        showCardMode("✅ 识别成功\n\n正在AI分析...");

                                        // 4. 自动进行AI解析
                                        performAiAnalysis(ocrText, result);
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        // 发生错误时也要释放bitmap
                                        finalBitmap.recycle();
                                        manualCaptureInProgress = false;
                                        cancelManualOcrTimeout();

                                        Log.e(TAG, "OCR error", e);
                                        String errorMsg = "OCR识别失败\n\n";

                                        if (e.getMessage() != null) {
                                            if (e.getMessage().contains("empty result")) {
                                                errorMsg += "图像处理失败 - 可能是图像格式问题\n\n";
                                            } else {
                                                errorMsg += "错误：" + e.getMessage() + "\n\n";
                                            }
                                        }

                                        errorMsg += "可能的解决方法：\n" +
                                                "1. 确保截图中有清晰的文字\n" +
                                                "2. 首次使用需要联网下载OCR模型\n" +
                                                "3. 重启应用后重试\n" +
                                                "4. 检查存储权限";

                                        showCardMode(errorMsg);
                                    }
                                });
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Analysis error", e);
                    manualCaptureInProgress = false;
                    cancelManualOcrTimeout();
                    if (mutableBitmap != null && !mutableBitmap.isRecycled()) {
                        mutableBitmap.recycle();
                    }
                    mainHandler.post(() -> showCardMode("分析失败: " + e.getMessage()));
                }
            }).start();
        });
    }

    private void performAiAnalysis(String ocrText, OcrResult ocrResult) {
        if (inputCoordinator == null || !inputCoordinator.canParse()) {
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
            manualCaptureInProgress = false;
            return;
        }

        // 在后台线程执行AI解析
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting AI analysis");

                String ctxDesc = ContextEnricher.buildContextDescriptor(this, currentFrontPackage);
                List<ActionPlan> plans = inputCoordinator == null
                        ? Collections.emptyList()
                        : inputCoordinator.parseOcrMultiple(ocrResult, lastMatchedKeyword, ctxDesc);

                Log.d(TAG, "AI analysis result: " + plans.size() + " plans");

                if (plans.isEmpty()) {
                    mainHandler.post(() -> {
                        String plainText = ocrResult.getPlainText();
                        String displayText = plainText.length() > 300 ? plainText.substring(0, 300) + "..." : plainText;

                        showCardMode("✅ 识别成功\n\n" + displayText +
                                "\n\n⚠️ AI无法识别动作\n" +
                                "可能原因：截图内容不包含明确的任务/日程/导航信息\n" +
                                "点击「执行」跳转主界面查看详情");

                        setupCardActionButton(() -> {
                            Intent intent = new Intent(FloatingButtonService.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setAction(Intent.ACTION_SEND);
                            intent.putExtra(Intent.EXTRA_TEXT, ocrText);
                            startActivity(intent);
                            mainHandler.postDelayed(() -> showIconMode(), 500);
                        });
                        manualCaptureInProgress = false;
                    });
                    return;
                }

                mainHandler.post(() -> {
                    manualCaptureInProgress = false;
                    displayActionPlans(plans);
                });

            } catch (Exception e) {
                Log.e(TAG, "AI analysis error", e);
                mainHandler.post(() -> {
                    manualCaptureInProgress = false;
                    showCardMode("AI分析失败\n" + e.getMessage());
                });
            }
        }).start();
    }

    private void startManualOcrTimeout() {
        cancelManualOcrTimeout();
        manualOcrTimeoutRunnable = () -> {
            if (!manualCaptureInProgress || tvCardContent == null) {
                return;
            }
            CharSequence current = tvCardContent.getText();
            if (current != null && current.toString().contains("正在识别屏幕文字")) {
                manualCaptureInProgress = false;
                showCardMode("OCR识别超时\n\n可能原因：\n1. 截图内容过大\n2. 设备负载过高\n3. 模型推理拥塞\n\n点击「执行」返回悬浮球后重试");
                setupCardActionButton(this::showIconMode);
            }
        };
        mainHandler.postDelayed(manualOcrTimeoutRunnable, MANUAL_OCR_TIMEOUT_MS);
    }

    private void cancelManualOcrTimeout() {
        if (manualOcrTimeoutRunnable != null) {
            mainHandler.removeCallbacks(manualOcrTimeoutRunnable);
            manualOcrTimeoutRunnable = null;
        }
    }

    private void displayActionPlans(List<ActionPlan> plans) {
        if (plans == null || plans.isEmpty()) return;
        pendingActionPlans.clear();
        pendingActionPlans.addAll(plans);
        currentPlanIndex = 0;
        showPlanAt(0);
    }

    private void showPlanAt(int index) {
        if (pendingActionPlans.isEmpty() || index < 0 || index >= pendingActionPlans.size()) return;
        ActionPlan plan = pendingActionPlans.get(index);

        boolean multiPlan = pendingActionPlans.size() > 1;
        if (layoutPlanNav != null) {
            layoutPlanNav.setVisibility(multiPlan ? View.VISIBLE : View.GONE);
        }
        if (tvPlanIndicator != null) {
            tvPlanIndicator.setText((index + 1) + " / " + pendingActionPlans.size());
        }

        StringBuilder displayText = new StringBuilder();
        displayText.append("🎯 AI分析结果\n\n");

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

        setupCardActionButton(() -> executeActionPlan(plan));
    }

    private void executeActionPlan(ActionPlan plan) {
        showCardMode("正在执行...");

        new Thread(() -> {
            ActionExecutor.ExecutionResult result = inputCoordinator == null
                    ? new ActionExecutor.ExecutionResult(false, "执行器未初始化")
                    : inputCoordinator.execute(plan);

            mainHandler.post(() -> {
                if (result.success) {
                    showCardMode("✅ 执行成功！\n\n" + result.message);
                    Toast.makeText(FloatingButtonService.this, result.message, Toast.LENGTH_LONG).show();
                    mainHandler.postDelayed(this::showIconMode, 3000);
                } else {
                    showCardMode("❌ 执行失败\n\n" + result.message);
                    mainHandler.postDelayed(this::showIconMode, 5000);
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
        if (floatingView == null)
            return;
        floatingView.setVisibility(View.VISIBLE);
        tvCardContent.setText(content);
        setOrbState(resolveState(content));
        iconView.setVisibility(View.GONE);
        cardView.setVisibility(View.VISIBLE);
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception e) {
            Log.e(TAG, "updateViewLayout failed", e);
        }
    }

    private void setOrbState(AiStateOrbView.State state) {
        if (iconView instanceof AiStateOrbView) {
            ((AiStateOrbView) iconView).setState(state);
        }
        if (aiStateOrb != null) {
            aiStateOrb.setState(state);
        }
    }

    private AiStateOrbView.State resolveState(String content) {
        if (content == null) {
            return AiStateOrbView.State.IDLE;
        }
        if (content.contains("失败") || content.contains("⚠️") || content.contains("❌")) {
            return AiStateOrbView.State.ERROR;
        }
        if (content.contains("执行成功") || content.contains("AI分析结果") || content.contains("识别成功")) {
            return AiStateOrbView.State.READY;
        }
        if (content.contains("识别") || content.contains("截屏")) {
            return AiStateOrbView.State.CAPTURING;
        }
        if (content.contains("分析") || content.contains("解析") || content.contains("执行")) {
            return AiStateOrbView.State.THINKING;
        }
        return AiStateOrbView.State.IDLE;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        manualCaptureInProgress = false;
        cancelManualOcrTimeout();
        mainHandler.removeCallbacks(debounceAnalyzeRunnable);
        mainHandler.removeCallbacks(hideInlineBannerRunnable);
        hideInlineBanner();
        if (floatingView != null && isFloatingViewAdded) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.w(TAG, "removeView failed", e);
            }
        }
    }

    // AccessibilityService doesn't use onBind like a regular service for other apps
    // But it's final in AccessibilityService so we don't override it improperly.
    // The super class handles it.
}
