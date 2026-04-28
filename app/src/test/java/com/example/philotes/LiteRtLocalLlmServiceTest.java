package com.example.philotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.philotes.data.api.LiteRtLocalLlmService;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;

public class LiteRtLocalLlmServiceTest {

    @Test
    public void chatCompletion_multiPrompt_returnsJsonArrayWithMultipleActions() {
        LiteRtLocalLlmService service = new LiteRtLocalLlmService(new File("dummy.tflite"), true);
        String systemPrompt = "输出必须是有效的 JSON 数组";
        String userMessage = "明天9点开会\n记得买牛奶";

        String response = service.chatCompletion(systemPrompt, userMessage);
        JsonArray array = JsonParser.parseString(response).getAsJsonArray();

        assertEquals(2, array.size());
        assertEquals("CREATE_CALENDAR", array.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("ADD_TODO", array.get(1).getAsJsonObject().get("type").getAsString());
    }

    @Test
    public void chatCompletion_singlePrompt_keepsSingleObjectContract() {
        LiteRtLocalLlmService service = new LiteRtLocalLlmService(new File("dummy.tflite"), true);
        String systemPrompt = "输出必须是有效的 JSON 对象";
        String userMessage = "买牛奶";

        String response = service.chatCompletion(systemPrompt, userMessage);

        assertTrue(response.trim().startsWith("{"));
        assertTrue(response.contains("\"type\":\"ADD_TODO\""));
    }

    @Test
    public void chatCompletion_multiPrompt_capsAtThreeAndSkipsUnknown() {
        LiteRtLocalLlmService service = new LiteRtLocalLlmService(new File("dummy.tflite"), true);
        String systemPrompt = "提取所有独立的可执行动作，最多3个，输出 JSON 数组";
        String userMessage = "明天9点开会\n明天9点开会\n导航到机场\n记得买牛奶\n完成实验报告";

        String response = service.chatCompletion(systemPrompt, userMessage);
        JsonArray array = JsonParser.parseString(response).getAsJsonArray();

        assertTrue(array.size() <= 3);
        for (int i = 0; i < array.size(); i++) {
            String type = array.get(i).getAsJsonObject().get("type").getAsString();
            assertTrue(!"UNKNOWN".equals(type));
        }
    }
}
