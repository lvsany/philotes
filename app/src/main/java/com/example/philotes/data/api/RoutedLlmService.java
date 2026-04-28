package com.example.philotes.data.api;

import android.content.Context;
import android.util.Log;

import com.example.philotes.utils.AiSettingsManager;
import com.example.philotes.utils.ModelUtils;

import java.io.File;
import java.util.Locale;

/**
 * LLM router controlled by user settings.
 * Supports smart/local-only/cloud-only policies.
 */
public class RoutedLlmService implements ILlmService {
    private static final String TAG = "RoutedLlmService";

    private final Context appContext;
    private final AiSettingsManager settingsManager;

    private volatile ILlmService cachedLocalService;
    private volatile ILlmService cachedCloudService;

    public RoutedLlmService(Context context) {
        this.appContext = context.getApplicationContext();
        this.settingsManager = new AiSettingsManager(appContext);
    }

    @Override
    public String chatCompletion(String systemPrompt, String userMessage) {
        String policy = settingsManager.getRoutingPolicy();

        if (AiSettingsManager.ROUTING_LOCAL_ONLY.equals(policy)) {
            return localOrUnknown(systemPrompt, userMessage);
        }

        if (AiSettingsManager.ROUTING_CLOUD_ONLY.equals(policy)) {
            return cloudOrUnknown(systemPrompt, userMessage);
        }

        // Smart routing (default): local first, then cloud fallback.
        String localResp = localOrNull(systemPrompt, userMessage);
        if (localResp != null && !isUnknownResponse(localResp)) {
            return localResp;
        }

        String cloudResp = cloudOrNull(systemPrompt, userMessage);
        if (cloudResp != null && !cloudResp.trim().isEmpty()) {
            return cloudResp;
        }

        if (localResp != null && !localResp.trim().isEmpty()) {
            return localResp;
        }

        return unknownJson(userMessage);
    }

    @Override
    public void streamChatCompletion(String systemPrompt, String userMessage, StreamListener listener) {
        String policy = settingsManager.getRoutingPolicy();

        ILlmService target;
        if (AiSettingsManager.ROUTING_CLOUD_ONLY.equals(policy)) {
            target = getCloudService();
        } else if (AiSettingsManager.ROUTING_LOCAL_ONLY.equals(policy)) {
            target = getLocalService();
        } else {
            target = getLocalService();
            if (target == null) {
                target = getCloudService();
            }
        }

        if (target == null) {
            listener.onDelta(unknownJson(userMessage));
            listener.onComplete();
            return;
        }

        target.streamChatCompletion(systemPrompt, userMessage, listener);
    }

    private String localOrUnknown(String systemPrompt, String userMessage) {
        String resp = localOrNull(systemPrompt, userMessage);
        return (resp == null || resp.trim().isEmpty()) ? unknownJson(userMessage) : resp;
    }

    private String cloudOrUnknown(String systemPrompt, String userMessage) {
        String resp = cloudOrNull(systemPrompt, userMessage);
        return (resp == null || resp.trim().isEmpty()) ? unknownJson(userMessage) : resp;
    }

    private String localOrNull(String systemPrompt, String userMessage) {
        ILlmService local = getLocalService();
        if (local == null) {
            return null;
        }
        try {
            return local.chatCompletion(systemPrompt, userMessage);
        } catch (Exception e) {
            Log.w(TAG, "Local LLM failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private String cloudOrNull(String systemPrompt, String userMessage) {
        ILlmService cloud = getCloudService();
        if (cloud == null) {
            return null;
        }
        try {
            return cloud.chatCompletion(systemPrompt, userMessage);
        } catch (Exception e) {
            Log.w(TAG, "Cloud LLM failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private ILlmService getLocalService() {
        if (cachedLocalService != null) {
            return cachedLocalService;
        }

        File modelFile = ModelUtils.getModelFile(appContext);
        if (modelFile == null || !modelFile.exists() || !modelFile.getName().endsWith(".tflite")) {
            return null;
        }

        synchronized (this) {
            if (cachedLocalService == null) {
                cachedLocalService = new LiteRtLocalLlmService(modelFile);
            }
        }
        return cachedLocalService;
    }

    private ILlmService getCloudService() {
        if (cachedCloudService != null) {
            return cachedCloudService;
        }

        if (!settingsManager.isApiConfigured()) {
            return null;
        }

        synchronized (this) {
            if (cachedCloudService == null) {
                cachedCloudService = new OpenAIService(
                        settingsManager.getApiKey(),
                        settingsManager.getBaseUrl(),
                        settingsManager.getModelName());
            }
        }
        return cachedCloudService;
    }

    private boolean isUnknownResponse(String response) {
        if (response == null) {
            return true;
        }
        String compact = response.replace(" ", "").toUpperCase(Locale.ROOT);
        return compact.contains("\"TYPE\":\"UNKNOWN\"");
    }

    private String unknownJson(String originalText) {
        String safe = originalText == null ? "" : originalText
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

        return "{" +
                "\"type\":\"UNKNOWN\"," +
                "\"slots\":{}," +
                "\"confidence\":0.0," +
                "\"original_text\":\"" + safe + "\"" +
                "}";
    }
}
