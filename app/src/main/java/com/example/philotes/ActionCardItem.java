package com.example.philotes;

import androidx.annotation.NonNull;

import com.example.philotes.data.model.ActionPlan;

import java.util.Objects;

/**
 * UI model for card rendering, supporting both streaming and finalized cards.
 */
public class ActionCardItem {
    private final String requestId;
    private final boolean streaming;
    private final ActionPlan plan;
    private final String streamingText;

    private ActionCardItem(String requestId, boolean streaming, ActionPlan plan, String streamingText) {
        this.requestId = requestId;
        this.streaming = streaming;
        this.plan = plan;
        this.streamingText = streamingText;
    }

    public static ActionCardItem streaming(String requestId, String text) {
        return new ActionCardItem(requestId, true, null, text == null ? "" : text);
    }

    public static ActionCardItem result(String requestId, ActionPlan plan) {
        return new ActionCardItem(requestId, false, plan, "");
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public ActionPlan getPlan() {
        return plan;
    }

    public String getStreamingText() {
        return streamingText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionCardItem)) return false;
        ActionCardItem that = (ActionCardItem) o;
        return streaming == that.streaming &&
                Objects.equals(requestId, that.requestId) &&
                Objects.equals(streamingText, that.streamingText) &&
                Objects.equals(planSummary(plan), planSummary(that.plan));
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, streaming, streamingText, planSummary(plan));
    }

    @NonNull
    private static String planSummary(ActionPlan plan) {
        if (plan == null) {
            return "null";
        }
        return String.valueOf(plan.getType()) + "|" + plan.getConfidence() + "|" + plan.getOriginalText() + "|" + plan.getSlots();
    }
}

