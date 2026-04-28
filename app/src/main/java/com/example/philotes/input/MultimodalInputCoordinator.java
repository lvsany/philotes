package com.example.philotes.input;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.OcrResult;
import com.example.philotes.domain.ActionExecutor;
import com.example.philotes.domain.ActionParser;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates text/OCR input parsing and action execution.
 * This keeps voice and screenshot inputs on the same execution contract.
 */
public class MultimodalInputCoordinator {

    public interface ParseStreamCallback {
        void onStreamingText(String partialText);
        void onPlanReady(ActionPlan plan);
        void onError(Exception error);
        void onCompleted();
    }

    private final ActionParser actionParser;
    private final ActionExecutor actionExecutor;

    public MultimodalInputCoordinator(ActionParser actionParser, ActionExecutor actionExecutor) {
        this.actionParser = actionParser;
        this.actionExecutor = actionExecutor;
    }

    public ActionPlan parseText(String text) {
        if (actionParser == null || text == null || text.trim().isEmpty()) {
            return null;
        }
        return actionParser.parse(text.trim());
    }

    public void parseTextStreaming(String text, ParseStreamCallback callback) {
        if (actionParser == null) {
            if (callback != null) {
                callback.onError(new IllegalStateException("解析器未初始化"));
            }
            return;
        }

        actionParser.parseStreaming(text, new ActionParser.ParseStreamListener() {
            @Override
            public void onStreamingText(String partialText) {
                if (callback != null) {
                    callback.onStreamingText(partialText);
                }
            }

            @Override
            public void onPlanCandidate(ActionPlan plan) {
                if (callback != null) {
                    callback.onPlanReady(plan);
                }
            }

            @Override
            public void onCompleted(ActionPlan plan) {
                if (callback != null) {
                    callback.onPlanReady(plan);
                    callback.onCompleted();
                }
            }

            @Override
            public void onError(Exception error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    public ActionPlan parseOcr(OcrResult ocrResult) {
        if (actionParser == null || ocrResult == null) {
            return null;
        }
        return actionParser.parseWithFilter(ocrResult);
    }

    public List<ActionPlan> parseTextMultiple(String text, String matchedKeyword) {
        return parseTextMultiple(text, matchedKeyword, null);
    }

    /**
     * 带情境感知的文本多意图解析。contextDescriptor 由 ContextEnricher 生成。
     */
    public List<ActionPlan> parseTextMultiple(String text, String matchedKeyword, String contextDescriptor) {
        if (actionParser == null || text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return actionParser.parseMultiple(text.trim(), matchedKeyword, contextDescriptor);
    }

    public List<ActionPlan> parseOcrMultiple(OcrResult ocrResult, String matchedKeyword) {
        return parseOcrMultiple(ocrResult, matchedKeyword, null);
    }

    /**
     * 带情境感知的 OCR 多意图解析。contextDescriptor 由 ContextEnricher 生成。
     */
    public List<ActionPlan> parseOcrMultiple(OcrResult ocrResult, String matchedKeyword,
                                             String contextDescriptor) {
        if (actionParser == null || ocrResult == null) {
            return Collections.emptyList();
        }
        return actionParser.parseMultipleWithFilter(ocrResult, matchedKeyword, contextDescriptor);
    }

    public ActionExecutor.ExecutionResult execute(ActionPlan plan) {
        if (actionExecutor == null || plan == null) {
            return new ActionExecutor.ExecutionResult(false, "执行器未就绪或动作为空");
        }
        return actionExecutor.execute(plan);
    }

    public boolean canParse() {
        return actionParser != null;
    }
}

