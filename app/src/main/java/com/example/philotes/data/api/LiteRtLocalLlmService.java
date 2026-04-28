package com.example.philotes.data.api;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local action parsing service for LiteRT models.
 *
 * Notes:
 * - Uses LiteRT runtime readiness as local capability gate.
 * - Uses deterministic local rules to output strict JSON for ActionParser.
 */
public class LiteRtLocalLlmService implements ILlmService {
    private static final String TAG = "LiteRtLocalLlmService";
    private static final int MAX_MULTI_ACTIONS = 3;

    private final File modelFile;
    private final LiteRtQwenService liteRtQwenService;
    private volatile boolean ready;

    public LiteRtLocalLlmService(File modelFile, boolean alreadyValidated) {
        this.modelFile = modelFile;
        this.liteRtQwenService = new LiteRtQwenService(modelFile);
        this.ready = alreadyValidated;
    }

    public LiteRtLocalLlmService(File modelFile) {
        this(modelFile, false);
    }

    private void ensureReady() {
        if (ready)
            return;
        try {
            String summary = liteRtQwenService.runSmokeTest();
            Log.i(TAG, "LiteRT warmup passed: " + summary);
            ready = true;
        } catch (Exception e) {
            Log.e(TAG, "LiteRT warmup failed", e);
            ready = false;
        }
    }

    @Override
    public String chatCompletion(String systemPrompt, String userMessage) {
        ensureReady();
        if (!ready) {
            return unknownJson(userMessage, 0.0);
        }

        String normalizedPrompt = systemPrompt == null ? "" : systemPrompt;
        String normalizedMessage = userMessage == null ? "" : userMessage;
        if (isMultiActionPrompt(normalizedPrompt)) {
            List<ParsedAction> actions = parseMultipleLocally(normalizedMessage, MAX_MULTI_ACTIONS);
            return toJsonArray(actions);
        }

        ParsedAction action = parseLocally(normalizedMessage);
        return toJson(action);
    }

    @Override
    public void streamChatCompletion(String systemPrompt, String userMessage, StreamListener listener) {
        try {
            String full = chatCompletion(systemPrompt, userMessage);
            if (full == null) {
                listener.onError(new IllegalStateException("Local LLM returned empty response"));
                return;
            }

            final int chunk = 16;
            for (int i = 0; i < full.length(); i += chunk) {
                if (Thread.currentThread().isInterrupted()) {
                    listener.onError(new InterruptedException("Local stream interrupted"));
                    return;
                }
                int end = Math.min(full.length(), i + chunk);
                listener.onDelta(full.substring(i, end));
                Thread.sleep(18);
            }
            listener.onComplete();
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    private ParsedAction parseLocally(String text) {
        String safeText = text == null ? "" : text;
        String lower = safeText.toLowerCase(Locale.ROOT);

        if (containsAny(lower, "导航", "去", "前往", "路线", "怎么去", "route", "navigate", "map")) {
            Map<String, String> slots = new LinkedHashMap<>();
            String location = extractLocation(safeText);
            if (!location.isEmpty()) {
                slots.put("location", location);
                slots.put("title", location);
            }
            return new ParsedAction("NAVIGATE", slots, 0.78, safeText);
        }

        if (containsAny(lower, "会议", "开会", "提醒", "日程", "明天", "今天", "周", "点", "calendar", "meeting")) {
            Map<String, String> slots = new LinkedHashMap<>();
            String title = extractTitle(safeText);
            if (!title.isEmpty()) {
                slots.put("title", title);
            }
            String iso = extractIsoTime(safeText);
            if (!iso.isEmpty()) {
                slots.put("time", iso);
            }
            return new ParsedAction("CREATE_CALENDAR", slots, 0.75, safeText);
        }

        if (containsAny(lower, "待办", "todo", "记得", "别忘", "需要", "买", "完成", "任务")) {
            Map<String, String> slots = new LinkedHashMap<>();
            String content = extractTitle(safeText);
            if (content.isEmpty()) {
                content = safeText.trim();
            }
            if (!content.isEmpty()) {
                slots.put("content", content);
                slots.put("title", content);
            }
            return new ParsedAction("ADD_TODO", slots, 0.73, safeText);
        }

        return new ParsedAction("UNKNOWN", new LinkedHashMap<>(), 0.2, safeText);
    }

    private List<ParsedAction> parseMultipleLocally(String text, int maxActions) {
        List<ParsedAction> actions = new ArrayList<>();
        if (text == null || text.trim().isEmpty() || maxActions <= 0) {
            return actions;
        }

        Set<String> dedupSignatures = new LinkedHashSet<>();
        List<String> segments = splitToSegments(text);
        if (segments.isEmpty()) {
            segments.add(text.trim());
        }

        for (String segment : segments) {
            ParsedAction action = parseLocally(segment);
            if ("UNKNOWN".equals(action.type)) {
                continue;
            }

            String signature = buildActionSignature(action);
            if (dedupSignatures.add(signature)) {
                actions.add(action);
            }
            if (actions.size() >= maxActions) {
                break;
            }
        }

        if (actions.isEmpty()) {
            ParsedAction fallback = parseLocally(text);
            if (!"UNKNOWN".equals(fallback.type)) {
                actions.add(fallback);
            }
        }

        return actions;
    }

    private static List<String> splitToSegments(String text) {
        List<String> segments = new ArrayList<>();
        String[] raw = text.split("[\\n；;。！？!?]+");
        for (String item : raw) {
            String normalized = normalizeSegment(item);
            if (!normalized.isEmpty()) {
                segments.add(normalized);
            }
        }

        if (segments.size() <= 1 && text.contains("，")) {
            raw = text.split("[，,]+");
            for (String item : raw) {
                String normalized = normalizeSegment(item);
                if (!normalized.isEmpty()) {
                    segments.add(normalized);
                }
            }
        }
        return segments;
    }

    private static String normalizeSegment(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() < 2) {
            return "";
        }
        return normalized;
    }

    private static String buildActionSignature(ParsedAction action) {
        StringBuilder sb = new StringBuilder(action.type);
        appendSignatureSlot(sb, action.slots, "time");
        appendSignatureSlot(sb, action.slots, "location");
        appendSignatureSlot(sb, action.slots, "title");
        appendSignatureSlot(sb, action.slots, "content");
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendSignatureSlot(StringBuilder sb, Map<String, String> slots, String key) {
        sb.append('|').append(key).append('=');
        if (slots != null) {
            String value = slots.get(key);
            if (value != null) {
                sb.append(value.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static String extractLocation(String text) {
        Matcher m = Pattern.compile("(?:去|前往|导航到|到)\\s*([\\p{L}\\p{N}\\u4e00-\\u9fa5]{2,30})").matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String extractTitle(String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() > 40) {
            return clean.substring(0, 40);
        }
        return clean;
    }

    private static String extractIsoTime(String text) {
        Matcher hm = Pattern.compile("(\\d{1,2})[:：点](\\d{1,2})?").matcher(text);
        if (hm.find()) {
            int h = clampInt(hm.group(1), 0, 23, 9);
            int m = hm.group(2) == null ? 0 : clampInt(hm.group(2), 0, 59, 0);
            return String.format(Locale.US, "2026-03-26T%02d:%02d:00", h, m);
        }
        Matcher ampm = Pattern.compile("(\\d{1,2})\\s*(am|pm)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (ampm.find()) {
            int h = clampInt(ampm.group(1), 0, 23, 9);
            String marker = ampm.group(2).toLowerCase(Locale.ROOT);
            if ("pm".equals(marker) && h < 12) {
                h += 12;
            } else if ("am".equals(marker) && h == 12) {
                h = 0;
            }
            return String.format(Locale.US, "2026-03-26T%02d:%02d:00", h, 0);
        }
        return "";
    }

    private static int clampInt(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k))
                return true;
        }
        return false;
    }

    private static boolean isMultiActionPrompt(String systemPrompt) {
        String prompt = systemPrompt == null ? "" : systemPrompt;
        String lowerPrompt = prompt.toLowerCase(Locale.ROOT);
        return prompt.contains("JSON 数组")
                || prompt.contains("提取所有独立的可执行动作")
                || prompt.contains("最多3个")
                || lowerPrompt.contains("json array")
                || lowerPrompt.contains("extract all independent executable actions");
    }

    private static String toJson(ParsedAction action) {
        StringBuilder slots = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : action.slots.entrySet()) {
            if (!first)
                slots.append(',');
            slots.append('"').append(escape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(escape(entry.getValue())).append('"');
            first = false;
        }
        slots.append('}');

        return "{" +
                "\"type\":\"" + escape(action.type) + "\"," +
                "\"slots\":" + slots + "," +
                "\"confidence\":" + String.format(Locale.US, "%.2f", action.confidence) + "," +
                "\"original_text\":\"" + escape(action.originalText) + "\"" +
                "}";
    }

    private static String toJsonArray(List<ParsedAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ParsedAction action : actions) {
            if (action == null || "UNKNOWN".equals(action.type)) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append(toJson(action));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private static String unknownJson(String originalText, double confidence) {
        return "{" +
                "\"type\":\"UNKNOWN\"," +
                "\"slots\":{}," +
                "\"confidence\":" + String.format(Locale.US, "%.2f", confidence) + "," +
                "\"original_text\":\"" + escape(originalText) + "\"" +
                "}";
    }

    private static String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static final class ParsedAction {
        final String type;
        final Map<String, String> slots;
        final double confidence;
        final String originalText;

        ParsedAction(String type, Map<String, String> slots, double confidence, String originalText) {
            this.type = type;
            this.slots = slots;
            this.confidence = confidence;
            this.originalText = originalText == null ? "" : originalText;
        }
    }
}
