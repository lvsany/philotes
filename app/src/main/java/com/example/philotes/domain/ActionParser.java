package com.example.philotes.domain;

import android.util.Log;
import com.example.philotes.data.api.ILlmService;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.example.philotes.data.model.OcrResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private final String parseMultiplePrompt =
        "你是一个智能助手，从文本中提取所有独立的可执行动作，最多3个。\n" +
        "每个独立的事件/地点/任务都应作为单独一条输出，即使类型相同。\n" +
        "例如文本包含「明天开会」和「周五聚餐」，应输出两个 CREATE_CALENDAR 条目。\n" +
        "输出必须是有效的 JSON 数组：\n" +
        "[\n" +
        "  {\n" +
        "    \"type\": \"CREATE_CALENDAR\" | \"NAVIGATE\" | \"ADD_TODO\" | \"COPY_TEXT\",\n" +
        "    \"slots\": { \"title\": \"string\", \"time\": \"YYYY-MM-DDTHH:MM:SS\", \"location\": \"string\", \"content\": \"string\" },\n" +
        "    \"confidence\": 0.0-1.0,\n" +
        "    \"original_text\": \"该条目对应的原始文本片段\"\n" +
        "  }\n" +
        "]\n\n" +
        "规则：\n" +
        "1. 日期/时间+事件 → CREATE_CALENDAR\n" +
        "2. 地点+去往意图 → NAVIGATE\n" +
        "3. 任务/待办 → ADD_TODO\n" +
        "4. 需要保存的文本 → COPY_TEXT\n" +
        "5. 每个条目的 original_text 只填写对应的那句原文，不要合并多句\n" +
        "6. 相同意思的重复表述只保留一条置信度最高的\n" +
        "7. 如果文本不包含明确动作意图，返回空数组 []\n" +
        "只输出 JSON 数组。";

    public ActionParser(ILlmService llmService) {
        this.llmService = llmService;
        this.gson = new Gson();
    }

    public interface ParseStreamListener {
        void onStreamingText(String partialText);
        void onPlanCandidate(ActionPlan plan);
        void onCompleted(ActionPlan plan);
        void onError(Exception error);
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
     * Streaming parse for text input.
     */
    public void parseStreaming(String text, ParseStreamListener listener) {
        if (text == null || text.trim().isEmpty()) {
            if (listener != null) {
                listener.onError(new IllegalArgumentException("输入文本为空"));
            }
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        String fullPrompt = parsePrompt + "\n当前日期: " + currentDate;
        String normalizedText = text.trim();
        StringBuilder streamedResponse = new StringBuilder();

        llmService.streamChatCompletion(fullPrompt, normalizedText, new ILlmService.StreamListener() {
            private boolean candidateDispatched;

            @Override
            public void onDelta(String delta) {
                streamedResponse.append(delta == null ? "" : delta);
                if (listener != null) {
                    listener.onStreamingText(streamedResponse.toString());
                }

                if (!candidateDispatched) {
                    ActionPlan candidate = tryParsePlanOrNull(streamedResponse.toString());
                    if (candidate != null && candidate.getType() != ActionType.UNKNOWN) {
                        candidateDispatched = true;
                        if (listener != null) {
                            listener.onPlanCandidate(candidate);
                        }
                    }
                }
            }

            @Override
            public void onComplete() {
                ActionPlan plan = parseJsonResponse(streamedResponse.toString(), normalizedText);
                if (listener != null) {
                    listener.onCompleted(plan);
                }
            }

            @Override
            public void onError(Exception error) {
                if (listener != null) {
                    listener.onError(error);
                }
            }
        });
    }

    /**
     * 从文本中提取多个可执行动作（最多3个），结合匹配关键词作为上下文提示。
     */
    public List<ActionPlan> parseMultiple(String text, String matchedKeyword) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String keywordHint = (matchedKeyword != null && !matchedKeyword.isEmpty())
                ? "\n当前触发关键词: \"" + matchedKeyword + "\"（请优先提取与此相关的动作）"
                : "";
        String fullPrompt = parseMultiplePrompt + keywordHint + "\n当前日期: " + currentDate;

        String jsonStr = llmService.chatCompletion(fullPrompt, text);
        return parseJsonArrayResponse(jsonStr, text);
    }

    /**
     * 对 OCR 结果合并全文后调用多计划 prompt，一次 LLM 请求提取所有可执行动作。
     * 超出 MAX_CHARS_PER_BATCH*3 时截断，避免 token 过多。
     */
    public List<ActionPlan> parseMultipleWithFilter(OcrResult ocrResult, String matchedKeyword) {
        if (ocrResult == null || ocrResult.getTextBlocks().isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder merged = new StringBuilder();
        for (OcrResult.TextBlock block : ocrResult.getTextBlocks()) {
            String t = block.text.trim();
            if (t.length() >= 2) {
                if (merged.length() > 0) merged.append('\n');
                merged.append(t);
                if (merged.length() >= MAX_CHARS_PER_BATCH * 3) break;
            }
        }

        if (merged.length() == 0) return Collections.emptyList();

        return parseMultiple(merged.toString(), matchedKeyword);
    }

    private List<ActionPlan> parseJsonArrayResponse(String jsonStr, String originalText) {
        List<ActionPlan> plans = new ArrayList<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return plans;

        String cleaned = jsonStr.replace("```json", "").replace("```JSON", "").replace("```", "").trim();

        // 尝试找 JSON 数组
        int arrStart = cleaned.indexOf('[');
        int arrEnd = cleaned.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            cleaned = cleaned.substring(arrStart, arrEnd + 1);
        } else {
            // 回退：如果 LLM 返回单个对象，包成数组再解析
            int objStart = cleaned.indexOf('{');
            int objEnd = cleaned.lastIndexOf('}');
            if (objStart >= 0 && objEnd > objStart) {
                cleaned = "[" + cleaned.substring(objStart, objEnd + 1) + "]";
            } else {
                return plans;
            }
        }

        try {
            JsonArray array = JsonParser.parseString(cleaned).getAsJsonArray();
            for (JsonElement el : array) {
                try {
                    ActionPlan plan = gson.fromJson(el, ActionPlan.class);
                    if (plan == null || plan.getType() == null || plan.getType() == ActionType.UNKNOWN) continue;
                    Map<String, String> slots = plan.getSlots() != null
                            ? new HashMap<>(plan.getSlots()) : new HashMap<>();
                    plan.setSlots(Collections.unmodifiableMap(slots));
                    if (plan.getOriginalText() == null || plan.getOriginalText().isEmpty()) {
                        plan.setOriginalText(originalText);
                    }
                    plans.add(plan);
                } catch (Exception e) {
                    Log.w(TAG, "Skip invalid element in array: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "parseJsonArrayResponse failed: " + e.getMessage());
        }
        return plans;
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
            String repairedJson = sanitizeAndRepairJson(jsonStr);
            if (repairedJson != null) {
                Log.d(TAG, "Parsed JSON: " + summarizeForLog(repairedJson));
                ActionPlan plan = gson.fromJson(repairedJson, ActionPlan.class);
                if (plan != null) {
                    normalizePlan(plan, originalText);
                    Log.d(TAG, "ActionPlan type: " + plan.getType());
                    return plan;
                }
            }
            Log.w(TAG, "LLM returned empty or invalid JSON after repair");
        } catch (Exception e) {
            Log.w(TAG, "ActionParser tolerant parse failed: " + e.getClass().getSimpleName()
                    + ", msg=" + summarizeForLog(e.getMessage()));
        }

        return createFallbackPlan(originalText);
    }

    private ActionPlan tryParsePlanOrNull(String jsonStr) {
        try {
            if (jsonStr == null || jsonStr.trim().isEmpty()) {
                return null;
            }
            String cleanJson = sanitizeAndRepairJson(jsonStr);
            if (cleanJson == null) {
                return null;
            }
            ActionPlan plan = gson.fromJson(cleanJson, ActionPlan.class);
            if (plan != null && plan.getType() != null) {
                return plan;
            }
        } catch (Exception ignored) {
            // Ignore partial JSON parse errors while streaming.
        }
        return null;
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

    private void normalizePlan(ActionPlan plan, String originalText) {
        if (plan.getType() == null) {
            plan.setType(ActionType.UNKNOWN);
        }
        if (plan.getSlots() == null) {
            plan.setSlots(Collections.emptyMap());
        } else {
            plan.setSlots(Collections.unmodifiableMap(plan.getSlots()));
        }
        if (plan.getOriginalText() == null || plan.getOriginalText().trim().isEmpty()) {
            plan.setOriginalText(originalText);
        }
        if (Double.isNaN(plan.getConfidence()) || Double.isInfinite(plan.getConfidence())) {
            plan.setConfidence(0.0);
        }
        if (plan.getConfidence() < 0.0 || plan.getConfidence() > 1.0) {
            plan.setConfidence(Math.max(0.0, Math.min(1.0, plan.getConfidence())));
        }
    }

    private String sanitizeAndRepairJson(String raw) {
        if (raw == null) {
            return null;
        }

        String cleaned = raw.replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        String extracted = extractJsonObject(cleaned);
        if (extracted == null) {
            extracted = cleaned;
        }

        String repaired = repairCommonJsonIssues(extracted);
        if (repaired == null || repaired.trim().isEmpty()) {
            return null;
        }
        return repaired.trim();
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        String partial = text.substring(start);
        int missing = Math.max(1, depth);
        StringBuilder sb = new StringBuilder(partial);
        for (int i = 0; i < missing; i++) {
            sb.append('}');
        }
        return sb.toString();
    }

    private String repairCommonJsonIssues(String json) {
        String repaired = json.trim();

        repaired = repaired.replaceAll(",\\s*([}\\]])", "$1");

        int openBrace = countChar(repaired, '{');
        int closeBrace = countChar(repaired, '}');
        if (closeBrace < openBrace) {
            StringBuilder sb = new StringBuilder(repaired);
            for (int i = 0; i < openBrace - closeBrace; i++) {
                sb.append('}');
            }
            repaired = sb.toString();
        }

        int quoteCount = countChar(repaired, '"');
        if (quoteCount % 2 != 0) {
            repaired = repaired + '"';
        }

        return repaired;
    }

    private int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    private String summarizeForLog(String text) {
        if (text == null) {
            return "null";
        }
        String compact = text.replaceAll("\\s+", " ");
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 120) + "...(len=" + compact.length() + ")";
    }
}
