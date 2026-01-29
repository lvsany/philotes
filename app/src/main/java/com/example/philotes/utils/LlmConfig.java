package com.example.philotes.utils;

/**
 * LLM 配置工具
 * 用于管理 AI 模型的选择和配置
 */
public class LlmConfig {

    // LLM 类型枚举
    public enum LlmType {
        ON_DEVICE,      // 端侧模型（MediaPipe）- 仅支持真实设备
        OPENAI_API,     // OpenAI API（可在模拟器运行）
        AUTO            // 自动选择（真机用端侧，模拟器用 API）
    }

    // 默认使用自动模式
    private static LlmType currentType = LlmType.AUTO;

    // OpenAI API 配置
    private static String openaiApiKey = "";  // 在此填入您的 API Key
    private static String openaiBaseUrl = "https://api.openai.com/v1";
    private static String openaiModel = "gpt-3.5-turbo";

    /**
     * 设置 LLM 类型
     */
    public static void setLlmType(LlmType type) {
        currentType = type;
    }

    /**
     * 获取当前 LLM 类型
     */
    public static LlmType getLlmType() {
        return currentType;
    }

    /**
     * 设置 OpenAI API Key
     */
    public static void setOpenAiApiKey(String apiKey) {
        openaiApiKey = apiKey;
    }

    /**
     * 获取 OpenAI API Key
     */
    public static String getOpenAiApiKey() {
        return openaiApiKey;
    }

    /**
     * 设置 OpenAI Base URL（用于兼容 OpenAI API 的其他服务）
     */
    public static void setOpenAiBaseUrl(String baseUrl) {
        openaiBaseUrl = baseUrl;
    }

    /**
     * 获取 OpenAI Base URL
     */
    public static String getOpenAiBaseUrl() {
        return openaiBaseUrl;
    }

    /**
     * 设置 OpenAI 模型名称
     */
    public static void setOpenAiModel(String model) {
        openaiModel = model;
    }

    /**
     * 获取 OpenAI 模型名称
     */
    public static String getOpenAiModel() {
        return openaiModel;
    }

    /**
     * 检查 OpenAI API 是否已配置
     */
    public static boolean isOpenAiConfigured() {
        return openaiApiKey != null && !openaiApiKey.isEmpty();
    }

    /**
     * 检查是否运行在模拟器上
     */
    public static boolean isEmulator() {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(android.os.Build.PRODUCT));
    }

    /**
     * 根据当前配置决定应该使用哪种 LLM
     */
    public static LlmType getResolvedLlmType() {
        if (currentType == LlmType.AUTO) {
            // 自动模式：模拟器使用 API，真机尝试使用端侧
            if (isEmulator()) {
                return isOpenAiConfigured() ? LlmType.OPENAI_API : LlmType.ON_DEVICE;
            } else {
                return LlmType.ON_DEVICE;
            }
        }
        return currentType;
    }
}
