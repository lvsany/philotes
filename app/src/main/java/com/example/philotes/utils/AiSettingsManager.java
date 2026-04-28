package com.example.philotes.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 设置管理类
 * 使用 SharedPreferences 存储用户配置
 */
public class AiSettingsManager {

    private static final String PREFS_NAME = "ai_settings";

    // Keys
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_API_PROVIDER = "api_provider";
    private static final String KEY_ROUTING_POLICY = "routing_policy";
    private static final String KEY_CUSTOM_TRIGGER_KEYWORDS = "custom_trigger_keywords";
    private static final String KEY_KEYWORDS_SEEDED = "keywords_seeded";

    // API 提供商常量
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_CUSTOM = "custom";

    // 路由策略
    public static final String ROUTING_SMART = "smart";
    public static final String ROUTING_LOCAL_ONLY = "local_only";
    public static final String ROUTING_CLOUD_ONLY = "cloud_only";

    private final SharedPreferences prefs;

    public AiSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 是否需要云端配置（路由策略不是强制端侧时，云端 API 配置生效）
     */
    public boolean needsCloudConfig() {
        return !ROUTING_LOCAL_ONLY.equals(getRoutingPolicy());
    }

    /**
     * 设置 API Key
     */
    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    /**
     * 获取 API Key
     */
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    /**
     * 设置 Base URL
     */
    public void setBaseUrl(String baseUrl) {
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
    }

    /**
     * 获取 Base URL
     */
    public String getBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1");
    }

    /**
     * 设置模型名称
     */
    public void setModelName(String modelName) {
        prefs.edit().putString(KEY_MODEL_NAME, modelName).apply();
    }

    /**
     * 获取模型名称
     */
    public String getModelName() {
        return prefs.getString(KEY_MODEL_NAME, "gpt-3.5-turbo");
    }

    /**
     * 设置 API 提供商类型
     */
    public void setApiProvider(String provider) {
        prefs.edit().putString(KEY_API_PROVIDER, provider).apply();
    }

    /**
     * 获取 API 提供商类型
     */
    public String getApiProvider() {
        return prefs.getString(KEY_API_PROVIDER, PROVIDER_OPENAI);
    }

    public void setRoutingPolicy(String policy) {
        prefs.edit().putString(KEY_ROUTING_POLICY, policy).apply();
    }

    public String getRoutingPolicy() {
        return prefs.getString(KEY_ROUTING_POLICY, ROUTING_SMART);
    }

    public void setCustomTriggerKeywords(List<String> keywords) {
        Set<String> set = new LinkedHashSet<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword == null) {
                    continue;
                }
                String trimmed = keyword.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
        }
        prefs.edit().putStringSet(KEY_CUSTOM_TRIGGER_KEYWORDS, set).apply();
    }

    public List<String> getCustomTriggerKeywords() {
        Set<String> set = prefs.getStringSet(KEY_CUSTOM_TRIGGER_KEYWORDS, new LinkedHashSet<>());
        return new ArrayList<>(set == null ? new LinkedHashSet<>() : set);
    }

    public boolean isKeywordsSeeded() {
        return prefs.getBoolean(KEY_KEYWORDS_SEEDED, false);
    }

    public void markKeywordsSeeded() {
        prefs.edit().putBoolean(KEY_KEYWORDS_SEEDED, true).apply();
    }

    /**
     * 检查 API 是否已配置
     */
    public boolean isApiConfigured() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 应用当前设置到 LlmConfig
     */
    public void applyToLlmConfig() {
        if (needsCloudConfig() && isApiConfigured()) {
            LlmConfig.setOpenAiApiKey(getApiKey());
            LlmConfig.setOpenAiBaseUrl(getBaseUrl());
            LlmConfig.setOpenAiModel(getModelName());
        }
    }
}
