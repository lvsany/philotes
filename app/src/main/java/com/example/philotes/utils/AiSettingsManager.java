package com.example.philotes.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI 设置管理类
 * 使用 SharedPreferences 存储用户配置
 */
public class AiSettingsManager {

    private static final String PREFS_NAME = "ai_settings";

    // Keys
    private static final String KEY_AI_MODE = "ai_mode";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_API_PROVIDER = "api_provider"; // 新增：保存提供商类型

    // AI 模式常量
    public static final String MODE_ON_DEVICE = "on_device";
    public static final String MODE_CLOUD_API = "cloud_api";

    // API 提供商常量
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_CUSTOM = "custom";

    private final SharedPreferences prefs;

    public AiSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 设置 AI 模式
     */
    public void setAiMode(String mode) {
        prefs.edit().putString(KEY_AI_MODE, mode).apply();
    }

    /**
     * 获取 AI 模式
     */
    public String getAiMode() {
        return prefs.getString(KEY_AI_MODE, MODE_ON_DEVICE);
    }

    /**
     * 是否使用云端 API
     */
    public boolean isCloudApiMode() {
        return MODE_CLOUD_API.equals(getAiMode());
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
        if (isCloudApiMode() && isApiConfigured()) {
            LlmConfig.setOpenAiApiKey(getApiKey());
            LlmConfig.setOpenAiBaseUrl(getBaseUrl());
            LlmConfig.setOpenAiModel(getModelName());
        }
    }
}
