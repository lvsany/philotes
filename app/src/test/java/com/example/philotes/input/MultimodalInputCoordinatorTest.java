package com.example.philotes.input;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.example.philotes.data.model.OcrResult;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.domain.ActionParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 感知模块 - MultimodalInputCoordinator 单元测试
 * 测试多模态数据的并发处理与预处理
 */
@RunWith(MockitoJUnitRunner.class)
public class MultimodalInputCoordinatorTest {

    @Mock
    private ActionParser mockActionParser;

    @Mock
    private ActionExecutor mockActionExecutor;

    @Mock
    private ActionPlan mockActionPlan;

    @Mock
    private OcrResult mockOcrResult;

    private MultimodalInputCoordinator coordinator;

    @Before
    public void setUp() {
        coordinator = new MultimodalInputCoordinator(mockActionParser, mockActionExecutor);
    }

    /**
     * TC-018: 验证文本解析的基本功能
     * 输入：正常的文本字符串
     * 预期：正确调用ActionParser.parse() 并返回ActionPlan
     */
    @Test
    public void testParseText() {
        String inputText = "明天下午开会";
        
        when(mockActionParser.parse(inputText)).thenReturn(mockActionPlan);
        when(mockActionPlan.getType()).thenReturn(ActionType.CREATE_CALENDAR);

        ActionPlan result = coordinator.parseText(inputText);

        assertNotNull("结果不应为null", result);
        assertEquals("应返回CREATE_CALENDAR类型", ActionType.CREATE_CALENDAR, result.getType());
        verify(mockActionParser).parse(inputText);
    }

    /**
     * TC-019: 验证文本流式解析的回调机制
     * 输入：正常文本，带有流式回调
     * 预期：正确触发onStreamingText和onCompleted回调
     */
    @Test
    public void testParseTextStreaming() {
        String inputText = "导航到机场";
        MultimodalInputCoordinator.ParseStreamCallback mockCallback = 
                mock(MultimodalInputCoordinator.ParseStreamCallback.class);

        ArgumentCaptor<ActionParser.ParseStreamListener> listenerCaptor = 
                ArgumentCaptor.forClass(ActionParser.ParseStreamListener.class);

        coordinator.parseTextStreaming(inputText, mockCallback);

        verify(mockActionParser).parseStreaming(eq(inputText), listenerCaptor.capture());
        
        // 模拟流式回调
        ActionParser.ParseStreamListener capturedListener = listenerCaptor.getValue();
        capturedListener.onStreamingText("部分响应");
        
        verify(mockCallback).onStreamingText("部分响应");
    }

    /**
     * TC-020: 验证OCR结果的解析
     * 输入：从OCR识别获得的OcrResult对象
     * 预期：正确调用ActionParser.parseWithFilter() 并返回ActionPlan
     */
    @Test
    public void testParseOcr() {
        when(mockActionParser.parseWithFilter(mockOcrResult)).thenReturn(mockActionPlan);
        when(mockActionPlan.getType()).thenReturn(ActionType.CREATE_CALENDAR);

        ActionPlan result = coordinator.parseOcr(mockOcrResult);

        assertNotNull("结果不应为null", result);
        assertEquals("应返回CREATE_CALENDAR类型", ActionType.CREATE_CALENDAR, result.getType());
        verify(mockActionParser).parseWithFilter(mockOcrResult);
    }

    /**
     * TC-021: 验证多模态输入中null文本的处理
     * 输入：null或空文本字符串
     * 预期：返回null或处理gracefully，不抛异常
     */
    @Test
    public void testParseNullText() {
        ActionPlan result = coordinator.parseText(null);

        assertNull("结果应为null", result);
        verify(mockActionParser, never()).parse(anyString());
    }

    /**
     * TC-022: 验证多模态输入中空文本的处理
     * 输入：空字符串
     * 预期：返回null或处理gracefully
     */
    @Test
    public void testParseEmptyText() {
        ActionPlan result = coordinator.parseText("   ");

        assertTrue("解析器未初始化或返回null", result == null);
    }

    /**
     * TC-023: 验证执行流程的完整性
     * 输入：ActionPlan对象
     * 预期：正确调用ActionExecutor.execute() 并返回执行结果
     */
    @Test
    public void testExecute() {
        ActionExecutor.ExecutionResult mockResult = 
                new ActionExecutor.ExecutionResult(true, "执行成功");

        when(mockActionExecutor.execute(mockActionPlan)).thenReturn(mockResult);

        ActionExecutor.ExecutionResult result = coordinator.execute(mockActionPlan);

        assertNotNull("结果不应为null", result);
        assertTrue("执行应成功", result.success);
        verify(mockActionExecutor).execute(mockActionPlan);
    }

    /**
     * TC-024: 验证null ActionPlan的执行处理
     * 输入：null的ActionPlan
     * 预期：返回失败结果，不抛异常
     */
    @Test
    public void testExecuteNullPlan() {
        ActionExecutor.ExecutionResult result = coordinator.execute(null);

        assertNotNull("结果不应为null", result);
        assertFalse("执行应失败", result.success);
        assertTrue("返回消息应提示原因", result.message.contains("为空"));
    }

    /**
     * TC-025: 验证Coordinator在Parser未初始化时的处理
     * 输入：创建没有Parser的Coordinator，调用parseText
     * 预期：返回null或处理gracefully
     */
    @Test
    public void testParseWithoutParser() {
        MultimodalInputCoordinator coordinatorNoParser = 
                new MultimodalInputCoordinator(null, mockActionExecutor);

        ActionPlan result = coordinatorNoParser.parseText("测试文本");

        assertNull("Parser不存在时应返回null", result);
    }

    /**
     * TC-026: 验证Coordinator在Executor未初始化时的处理
     * 输入：创建没有Executor的Coordinator，调用execute
     * 预期：返回失败结果
     */
    @Test
    public void testExecuteWithoutExecutor() {
        MultimodalInputCoordinator coordinatorNoExecutor = 
                new MultimodalInputCoordinator(mockActionParser, null);

        ActionExecutor.ExecutionResult result = coordinatorNoExecutor.execute(mockActionPlan);

        assertNotNull("结果不应为null", result);
        assertFalse("执行应失败", result.success);
    }

    /**
     * TC-027: 验证canParse()方法的准确性
     * 输入：有Parser和无Parser的Coordinator
     * 预期：正确返回boolean状态
     */
    @Test
    public void testCanParse() {
        assertTrue("有Parser时应返回true", coordinator.canParse());

        MultimodalInputCoordinator coordinatorNoParser = 
                new MultimodalInputCoordinator(null, mockActionExecutor);
        assertFalse("无Parser时应返回false", coordinatorNoParser.canParse());
    }

    /**
     * TC-028: 验证流式解析中错误回调的触发
     * 输入：Parser抛出异常时的流式解析
     * 预期：正确调用onError回调
     */
    @Test  
    public void testParseStreamingWithError() {
        String inputText = "测试";
        MultimodalInputCoordinator.ParseStreamCallback mockCallback = 
                mock(MultimodalInputCoordinator.ParseStreamCallback.class);

        ArgumentCaptor<ActionParser.ParseStreamListener> listenerCaptor = 
                ArgumentCaptor.forClass(ActionParser.ParseStreamListener.class);

        coordinator.parseTextStreaming(inputText, mockCallback);

        verify(mockActionParser).parseStreaming(eq(inputText), listenerCaptor.capture());
        
        // 模拟错误回调
        ActionParser.ParseStreamListener capturedListener = listenerCaptor.getValue();
        Exception testException = new Exception("测试异常");
        capturedListener.onError(testException);
        
        verify(mockCallback).onError(testException);
    }

    /**
     * TC-029: 验证多模态数据的并发处理能力
     * 输入：快速连续的多个parseText调用
     * 预期：所有解析请求都被正确处理，互不干扰
     */
    @Test
    public void testConcurrentMultimodalParsing() {
        String text1 = "打开日历";
        String text2 = "导航到机场";
        
        when(mockActionParser.parse(text1)).thenReturn(mockActionPlan);
        when(mockActionParser.parse(text2)).thenReturn(mockActionPlan);

        // 快速连续调用
        ActionPlan result1 = coordinator.parseText(text1);
        ActionPlan result2 = coordinator.parseText(text2);

        assertNotNull("结果1应不为null", result1);
        assertNotNull("结果2应不为null", result2);
        verify(mockActionParser).parse(text1);
        verify(mockActionParser).parse(text2);
    }

    /**
     * TC-030: 验证OcrResult为null时的处理
     * 输入：null的OcrResult
     * 预期：返回null或处理gracefully
     */
    @Test
    public void testParseNullOcr() {
        ActionPlan result = coordinator.parseOcr(null);

        assertNull("OCR为null时应返回null", result);
        verify(mockActionParser, never()).parseWithFilter(any());
    }
}
