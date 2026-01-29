package com.example.philotes.domain;

import com.example.philotes.data.api.ILlmService;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class ActionParser {
    private final ILlmService llmService;
    private final Gson gson;

    private final String systemPrompt =
        "You are an intelligent assistant that extracts actionable plans from text.\n" +
        "Your output must be a valid JSON object matching the following structure:\n" +
        "{\n" +
        "  \"type\": \"CREATE_CALENDAR\" | \"NAVIGATE\" | \"ADD_TODO\" | \"COPY_TEXT\" | \"UNKNOWN\",\n" +
        "  \"slots\": {\n" +
        "    \"title\": \"string (optional)\",\n" +
        "    \"time\": \"YYYY-MM-DDTHH:MM:SS (ISO 8601, optional)\",\n" +
        "    \"location\": \"string (optional)\",\n" +
        "    \"content\": \"string (optional)\"\n" +
        "  },\n" +
        "  \"original_text\": \"string (the original input)\"\n" +
        "}\n" +
        "\n" +
        "Rules:\n" +
        "1. If the text implies scheduling an event (e.g., \"meeting tomorrow\"), type is CREATE_CALENDAR. Extract 'title', 'time' (convert relative time to absolute if possible), 'location'.\n" +
        "2. If the text implies going somewhere (e.g., \"go to airport\"), type is NAVIGATE. Extract 'location'.\n" +
        "3. If the text is a task (e.g., \"buy milk\"), type is ADD_TODO. Extract 'title' or 'content'.\n" +
        "4. If unsure, type is UNKNOWN.\n" +
        "5. Output ONLY JSON.";

    public ActionParser(ILlmService llmService) {
        this.llmService = llmService;
        this.gson = new Gson();
    }

    public ActionPlan parse(String text) {
        // Inject current date context
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        String fullPrompt = systemPrompt + "\nCurrent Date: " + currentDate;

        String jsonStr = llmService.chatCompletion(fullPrompt, text);

        try {
            if (jsonStr != null) {
                // LLM cleaning: sometimes model returns ```json ... ```
                String cleanJson = jsonStr.replace("```json", "").replace("```", "").trim();
                return gson.fromJson(cleanJson, ActionPlan.class);
            } else {
                throw new Exception("LLM returned null - 可能是 API Key 无效或网络错误");
            }
        } catch (Exception e) {
            System.err.println("ActionParser Error: " + e.getMessage());
            e.printStackTrace();
            // Fallback
            ActionPlan fallback = new ActionPlan();
            fallback.setType(ActionType.UNKNOWN);
            fallback.setSlots(Collections.emptyMap());
            fallback.setOriginalText(text);
            fallback.setConfidence(0.0);
            return fallback;
        }
    }
}
