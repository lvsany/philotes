package com.example.philotes.data.model;

import com.google.gson.annotations.SerializedName;

public enum ActionType {
    @SerializedName("CREATE_CALENDAR")
    CREATE_CALENDAR,

    @SerializedName("NAVIGATE")
    NAVIGATE,

    @SerializedName("ADD_TODO")
    ADD_TODO,

    @SerializedName("COPY_TEXT")
    COPY_TEXT,

    @SerializedName("UNKNOWN")
    UNKNOWN
}
