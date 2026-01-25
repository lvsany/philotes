package com.example.philotes.domain;

import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mock ActionParser - 用于测试
 * 不需要模型，直接返回固定的 ActionPlan
 */
public class MockActionParser {

    /**
     * 根据输入返回不同的 Mock ActionPlan
     * 1 = 创建日历事件
     * 2 = 导航
     * 3 = 添加待办
     * 其他 = 默认日历事件
     */
    public static ActionPlan parse(String text) {
        if (text == null || text.isEmpty()) {
            text = "1";
        }

        String trimmed = text.trim();
        
        // 根据输入返回不同类型
        if (trimmed.equals("1")) {
            return createCalendarPlan(text);
        } else if (trimmed.equals("2")) {
            return createNavigatePlan(text);
        } else if (trimmed.equals("3")) {
            return createTodoPlan(text);
        } else {
            // 默认返回日历事件
            return createCalendarPlan(text);
        }
    }

    /**
     * 创建日历事件 ActionPlan
     */
    private static ActionPlan createCalendarPlan(String text) {
        ActionPlan plan = new ActionPlan();
        plan.setType(ActionType.CREATE_CALENDAR);
        plan.setOriginalText(text);
        plan.setConfidence(0.85);

        Map<String, String> slots = new HashMap<>();
        
        // 固定的日历事件数据
        slots.put("title", "项目评审会议");
        
        // 时间设置为明天下午2点
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 14);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        slots.put("time", sdf.format(cal.getTime()));
        
        slots.put("location", "会议室A");
        slots.put("content", "讨论项目进度和下一步计划");

        plan.setSlots(slots);
        return plan;
    }

    /**
     * 创建导航 ActionPlan
     */
    private static ActionPlan createNavigatePlan(String text) {
        ActionPlan plan = new ActionPlan();
        plan.setType(ActionType.NAVIGATE);
        plan.setOriginalText(text);
        plan.setConfidence(0.90);

        Map<String, String> slots = new HashMap<>();
        slots.put("location", "北京天安门");

        plan.setSlots(slots);
        return plan;
    }

    /**
     * 创建待办 ActionPlan
     */
    private static ActionPlan createTodoPlan(String text) {
        ActionPlan plan = new ActionPlan();
        plan.setType(ActionType.ADD_TODO);
        plan.setOriginalText(text);
        plan.setConfidence(0.80);

        Map<String, String> slots = new HashMap<>();
        slots.put("title", "准备项目材料");
        slots.put("content", "整理PPT和数据报告");

        plan.setSlots(slots);
        return plan;
    }
}
