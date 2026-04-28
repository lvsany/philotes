package com.example.philotes.render;

import com.example.philotes.data.model.ActionPlan;

/**
 * Event stream contract for asynchronous card rendering.
 */
public class CardRenderEvent {
    public enum Type {
        LOADING,
        STREAMING,
        CARD_READY,
        ERROR,
        COMPLETED
    }

    private final Type type;
    private final String requestId;
    private final String text;
    private final ActionPlan actionPlan;

    private CardRenderEvent(Type type, String requestId, String text, ActionPlan actionPlan) {
        this.type = type;
        this.requestId = requestId;
        this.text = text;
        this.actionPlan = actionPlan;
    }

    public static CardRenderEvent loading(String requestId) {
        return new CardRenderEvent(Type.LOADING, requestId, null, null);
    }

    public static CardRenderEvent streaming(String requestId, String text) {
        return new CardRenderEvent(Type.STREAMING, requestId, text, null);
    }

    public static CardRenderEvent cardReady(String requestId, ActionPlan actionPlan) {
        return new CardRenderEvent(Type.CARD_READY, requestId, null, actionPlan);
    }

    public static CardRenderEvent error(String requestId, String errorText) {
        return new CardRenderEvent(Type.ERROR, requestId, errorText, null);
    }

    public static CardRenderEvent completed(String requestId) {
        return new CardRenderEvent(Type.COMPLETED, requestId, null, null);
    }

    public Type getType() {
        return type;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getText() {
        return text;
    }

    public ActionPlan getActionPlan() {
        return actionPlan;
    }
}

