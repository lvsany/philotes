package com.example.philotes.domain;

import android.util.Log;
import com.example.philotes.data.api.ILlmService;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.example.philotes.data.model.OcrResult;
import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ActionParser {
    private static final String TAG = "ActionParser";
    private final ILlmService llmService;
    private final Gson gson;

    // 自适应分组参数
    private static final int MAX_CHARS_PER_BATCH = 200;  // 每批最大字符数
    private static final int MAX_BLOCKS_PER_BATCH = 5;   // 每批最大 block 数

    // 解析动作的 prompt
    private final String parsePrompt =
        "你是一个智能助手，从文本中提取可执行的动作。\n" +
        "输出必须是有效的 JSON 对象：\n" +
        "{\n" +
        "  \"type\": \"CREATE_CALENDAR\" | \"NAVIGATE\" | \"ADD_TODO\" | \"UNKNOWN\",\n" +
        "  \"slots\": {\n" +
        "    \"title\": \"string (可选)\",\n" +
        "    \"time\": \"YYYY-MM-DDTHH:MM:SS (ISO 8601格式, 可选)\",\n" +
        "    \"location\": \"string (可选)\",\n" +
        "    \"content\": \"string (可选)\"\n" +
        "  },\n" +
        "  \"confidence\": 0.0-1.0,\n" +
        "  \"original_text\": \"string\"\n" +
        "}\n\n" +
        "规则：\n" +
        "1. 日期/时间+事件 → CREATE_CALENDAR（如\"明天3点开会\"、\"周五下午聚餐\"）\n" +
        "2. 地点+去往意图 → NAVIGATE（如\"去机场\"、\"导航到xxx\"、\"怎么去xxx\"）\n" +
        "3. 任务/待办 → ADD_TODO（如\"买牛奶\"、\"提醒我xxx\"、\"记得xxx\"）\n" +
        "4. 如果文本不包含明确的动作意图 → UNKNOWN\n" +
        "5. confidence 表示你对这个判断的信心（0-1）\n" +
        "只输出 JSON。";

    public ActionParser(ILlmService llmService) {
        this.llmService = llmService;
        this.gson = new Gson();
    }

    /**
     * 解析文本（原有方法，保持兼容）
     */
    public ActionPlan parse(String text) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        String fullPrompt = parsePrompt + "\n当前日期: " + currentDate;

        Log.d(TAG, "Calling LLM with text length: " + text.length());
        String jsonStr = llmService.chatCompletion(fullPrompt, text);
        Log.d(TAG, "LLM response: " + (jsonStr != null ? jsonStr.substring(0, Math.min(200, jsonStr.length())) : "null"));

        return parseJsonResponse(jsonStr, text);
    }

    /**
     * 处理 OCR 结果，自适应分批发给 AI 解析
     * @param ocrResult OCR识别结果
     * @return ActionPlan（置信度最高的非 UNKNOWN 结果）
     */
    public ActionPlan parseWithFilter(OcrResult ocrResult) {
        if (ocrResult == null || ocrResult.getTextBlocks().isEmpty()) {
            return createFallbackPlan("无文本内容");
        }

        // 收集有效的文本块
        List<String> validBlocks = new ArrayList<>();
        for (OcrResult.TextBlock block : ocrResult.getTextBlocks()) {
            String text = block.text.trim();
            if (!text.isEmpty() && text.length() >= 2) {
                validBlocks.add(text);
            }
        }

        if (validBlocks.isEmpty()) {
            return createFallbackPlan("无有效文本");
        }

        // 自适应分批
        List<String> batches = createBatches(validBlocks);
        Log.d(TAG, "========== 自适应分批解析 ==========");
        Log.d(TAG, "原始 blocks: " + validBlocks.size() + ", 合并为 " + batches.size() + " 批");

        ActionPlan bestPlan = null;
        double bestConfidence = 0.0;

        for (int i = 0; i < batches.size(); i++) {
            String batchText = batches.get(i);
            Log.d(TAG, "Batch " + i + ": [" + batchText.replace("\n", " | ") + "]");

            ActionPlan plan = parse(batchText);

            if (plan != null && plan.getType() != ActionType.UNKNOWN) {
                Log.d(TAG, "Batch " + i + " -> " + plan.getType() + " (confidence: " + plan.getConfidence() + ")");

                if (plan.getConfidence() > bestConfidence) {
                    bestConfidence = plan.getConfidence();
                    bestPlan = plan;
                }
            } else {
                Log.d(TAG, "Batch " + i + " -> UNKNOWN");
            }
        }

        Log.d(TAG, "========== 分批解析结束 ==========");

        if (bestPlan != null) {
            Log.d(TAG, "Best result: " + bestPlan.getType() + " (confidence: " + bestConfidence + ")");
            return bestPlan;
        }

        return createFallbackPlan(ocrResult.getPlainText());
    }

    /**
     * 自适应创建批次：根据字符数和 block 数量智能合并
     */
    private List<String> createBatches(List<String> blocks) {
        List<String> batches = new ArrayList<>();
        StringBuilder currentBatch = new StringBuilder();
        int currentBlockCount = 0;

        for (String block : blocks) {
            // 判断是否需要开始新批次
            boolean shouldStartNewBatch =
                currentBatch.length() > 0 && (
                    currentBatch.length() + block.length() > MAX_CHARS_PER_BATCH ||
                    currentBlockCount >= MAX_BLOCKS_PER_BATCH
                );

            if (shouldStartNewBatch) {
                batches.add(currentBatch.toString().trim());
                currentBatch = new StringBuilder();
                currentBlockCount = 0;
            }

            if (currentBatch.length() > 0) {
                currentBatch.append("\n");
            }
            currentBatch.append(block);
            currentBlockCount++;
        }

        // 添加最后一批
        if (currentBatch.length() > 0) {
            batches.add(currentBatch.toString().trim());
        }

        return batches;
    }

    /**
     * 解析 JSON 响应
     */
    private ActionPlan parseJsonResponse(String jsonStr, String originalText) {
        try {
            if (jsonStr != null) {
                String cleanJson = jsonStr.replace("```json", "").replace("```", "").trim();
                Log.d(TAG, "Parsed JSON: " + cleanJson);
                ActionPlan plan = gson.fromJson(cleanJson, ActionPlan.class);
                if (plan != null) {
                    Log.d(TAG, "ActionPlan type: " + plan.getType());
                    return plan;
                }
            }
            Log.e(TAG, "LLM returned null or invalid JSON");
        } catch (Exception e) {
            Log.e(TAG, "ActionParser Error: " + e.getMessage(), e);
        }

        return createFallbackPlan(originalText);
    }

    /**
     * 创建回退计划
     */
    private ActionPlan createFallbackPlan(String text) {
        ActionPlan fallback = new ActionPlan();
        fallback.setType(ActionType.UNKNOWN);
        fallback.setSlots(Collections.emptyMap());
        fallback.setOriginalText(text);
        fallback.setConfidence(0.0);
        return fallback;
    }
}
