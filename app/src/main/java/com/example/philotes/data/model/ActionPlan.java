package com.example.philotes.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Represents an actionable plan derived from user input (text/image).
 */
public class ActionPlan {

    /**
     * The type of action to be performed.
     * e.g., CREATE_CALENDAR, NAVIGATE_TO, ADD_TODO
     */
    @SerializedName("type")
    private ActionType type;

    /**
     * Extracted parameters for the action.
     * e.g., { "title": "Review Meeting", "time": "2024-01-24T10:00", "location": "Room 505" }
     */
    @SerializedName("slots")
    private Map<String, String> slots;

    /**
     * The original text from which this plan was derived.
     */
    @SerializedName("original_text")
    private String originalText;

    /**
     * Confidence score of the extraction (0.0 - 1.0).
     */
    @SerializedName("confidence")
    private double confidence = 1.0;

    // Constructors
    public ActionPlan() {}

    public ActionPlan(ActionType type, Map<String, String> slots, String originalText, double confidence) {
        this.type = type;
        this.slots = slots;
        this.originalText = originalText;
        this.confidence = confidence;
    }

    // Getters and Setters
    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public Map<String, String> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, String> slots) {
        this.slots = slots;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
