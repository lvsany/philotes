package com.example.philotes.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.philotes.data.api.ILlmService;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 ActionParser.parseMultiple() 正确将情境描述符注入 LLM Prompt。
 */
public class ActionParserContextTest {

    private static final String NAVIGATE_JSON =
        "[{\"type\":\"NAVIGATE\",\"slots\":{\"location\":\"北京南站\"},\"confidence\":0.95," +
        "\"original_text\":\"去北京南站\"}]";

    /**
     * 验证情境描述符出现在发给 LLM 的 systemPrompt 中。
     */
    @Test
    public void parseMultiple_withContext_injectsDescriptorIntoPrompt() {
        String contextDescriptor = "[设备情境]\n当前时段: 下午(15:30)\n电量: 80%\n网络状态: WiFi\n";
        AtomicReference<String> capturedSystemPrompt = new AtomicReference<>("");

        ILlmService capturingService = new ILlmService() {
            @Override
            public String chatCompletion(String systemPrompt, String userMessage) {
                capturedSystemPrompt.set(systemPrompt);
                return NAVIGATE_JSON;
            }
            @Override
            public void streamChatCompletion(String systemPrompt, String userMessage, StreamListener l) {
                l.onDelta(NAVIGATE_JSON);
                l.onComplete();
            }
        };

        ActionParser parser = new ActionParser(capturingService);
        List<ActionPlan> plans = parser.parseMultiple("去北京南站", null, contextDescriptor);

        // 情境描述符应出现在 systemPrompt 中
        assertTrue("systemPrompt 应包含情境描述符",
            capturedSystemPrompt.get().contains("[设备情境]"));
        assertTrue("systemPrompt 应包含时段信息",
            capturedSystemPrompt.get().contains("下午(15:30)"));
        assertTrue("systemPrompt 应包含网络信息",
            capturedSystemPrompt.get().contains("WiFi"));

        // 解析结果正确
        assertNotNull(plans);
        assertEquals(1, plans.size());
        assertEquals(ActionType.NAVIGATE, plans.get(0).getType());
        assertEquals("北京南站", plans.get(0).getSlots().get("location"));
    }

    /**
     * null 情境描述符时退化为无情境模式，结果仍正确。
     */
    @Test
    public void parseMultiple_nullContext_stillParses() {
        ILlmService fakeService = new ILlmService() {
            @Override
            public String chatCompletion(String systemPrompt, String userMessage) {
                return NAVIGATE_JSON;
            }
            @Override
            public void streamChatCompletion(String sp, String um, StreamListener l) {
                l.onDelta(NAVIGATE_JSON);
                l.onComplete();
            }
        };

        ActionParser parser = new ActionParser(fakeService);
        List<ActionPlan> plans = parser.parseMultiple("去北京南站", null, null);
        assertNotNull(plans);
        assertEquals(1, plans.size());
        assertEquals(ActionType.NAVIGATE, plans.get(0).getType());
    }

    /**
     * 向下兼容：调用老接口（不传 contextDescriptor）结果不变。
     */
    @Test
    public void parseMultiple_oldSignature_backwardCompatible() {
        ILlmService fakeService = new ILlmService() {
            @Override
            public String chatCompletion(String sp, String um) {
                return "[{\"type\":\"ADD_TODO\",\"slots\":{\"title\":\"买牛奶\"},\"confidence\":0.9," +
                    "\"original_text\":\"买牛奶\"}]";
            }
            @Override
            public void streamChatCompletion(String sp, String um, StreamListener l) {
                l.onComplete();
            }
        };

        ActionParser parser = new ActionParser(fakeService);
        List<ActionPlan> plans = parser.parseMultiple("买牛奶", null);
        assertNotNull(plans);
        assertEquals(1, plans.size());
        assertEquals(ActionType.ADD_TODO, plans.get(0).getType());
    }

    /**
     * 验证关键词提示与情境描述符可同时注入。
     */
    @Test
    public void parseMultiple_keywordAndContext_bothInjected() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>("");

        ILlmService capturingService = new ILlmService() {
            @Override
            public String chatCompletion(String systemPrompt, String userMessage) {
                capturedPrompt.set(systemPrompt);
                return NAVIGATE_JSON;
            }
            @Override
            public void streamChatCompletion(String sp, String um, StreamListener l) {
                l.onComplete();
            }
        };

        ActionParser parser = new ActionParser(capturingService);
        parser.parseMultiple("去北京南站", "导航", "[设备情境]\n当前时段: 晚上(20:00)\n");

        String prompt = capturedPrompt.get();
        assertTrue("应包含关键词提示", prompt.contains("导航"));
        assertTrue("应包含情境描述符", prompt.contains("[设备情境]"));
        assertTrue("应包含当前日期", prompt.contains("当前日期"));
    }
}
