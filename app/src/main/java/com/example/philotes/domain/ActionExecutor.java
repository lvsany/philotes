package com.example.philotes.domain;

import android.content.Context;
import com.example.philotes.data.model.ActionPlan;
import com.example.philotes.data.model.ActionType;
import com.example.philotes.helper.CalendarHelper;
import com.example.philotes.helper.NavigationHelper;
import com.example.philotes.helper.TodoHelper;

/**
 * ActionExecutor - 执行器
 * 根据 ActionPlan 执行相应的操作
 */
public class ActionExecutor {

    /**
     * 执行结果
     */
    public static class ExecutionResult {
        public final boolean success;
        public final String message;
        public final Object data; // 可以返回 Uri 或其他数据

        public ExecutionResult(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public ExecutionResult(boolean success, String message) {
            this(success, message, null);
        }
    }

    private final Context context;

    public ActionExecutor(Context context) {
        this.context = context;
    }

    /**
     * 执行 ActionPlan
     */
    public ExecutionResult execute(ActionPlan plan) {
        if (plan == null) {
            return new ExecutionResult(false, "ActionPlan 为空");
        }

        ActionType type = plan.getType();
        if (type == null || type == ActionType.UNKNOWN) {
            return new ExecutionResult(false, "无法识别的动作类型");
        }

        try {
            switch (type) {
                case CREATE_CALENDAR:
                    return executeCreateCalendar(plan);
                
                case NAVIGATE:
                    return executeNavigate(plan);
                
                case ADD_TODO:
                    return executeAddTodo(plan);
                
                case COPY_TEXT:
                    return executeCopyText(plan);
                
                default:
                    return new ExecutionResult(false, "不支持的动作类型: " + type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ExecutionResult(false, "执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行创建日历事件
     */
    private ExecutionResult executeCreateCalendar(ActionPlan plan) {
        try {
            android.net.Uri eventUri = CalendarHelper.createCalendarEvent(context, plan);
            if (eventUri != null) {
                String title = plan.getSlots().getOrDefault("title", "事件");
                return new ExecutionResult(true, "日历事件创建成功: " + title, eventUri);
            } else {
                return new ExecutionResult(false, "日历事件创建失败，请确保设备已登录日历账户");
            }
        } catch (Exception e) {
            return new ExecutionResult(false, "创建日历事件时出错: " + e.getMessage());
        }
    }

    /**
     * 执行导航
     */
    private ExecutionResult executeNavigate(ActionPlan plan) {
        try {
            String location = plan.getSlots().get("location");
            if (location == null || location.isEmpty()) {
                return new ExecutionResult(false, "未找到目的地信息");
            }

            boolean success = NavigationHelper.startNavigation(context, location);
            if (success) {
                return new ExecutionResult(true, "已打开导航到: " + location);
            } else {
                return new ExecutionResult(false, "无法打开导航");
            }
        } catch (Exception e) {
            return new ExecutionResult(false, "打开导航时出错: " + e.getMessage());
        }
    }

    /**
     * 执行添加待办
     */
    private ExecutionResult executeAddTodo(ActionPlan plan) {
        try {
            boolean success = TodoHelper.createTodo(context, plan);
            if (success) {
                String title = plan.getSlots().getOrDefault("title", "待办事项");
                return new ExecutionResult(true, "待办事项已创建: " + title);
            } else {
                return new ExecutionResult(false, "创建待办失败，请手动添加");
            }
        } catch (Exception e) {
            return new ExecutionResult(false, "创建待办时出错: " + e.getMessage());
        }
    }

    /**
     * 执行复制文本
     */
    private ExecutionResult executeCopyText(ActionPlan plan) {
        try {
            String text = plan.getSlots().get("content");
            if (text == null || text.isEmpty()) {
                text = plan.getOriginalText();
            }

            if (text == null || text.isEmpty()) {
                return new ExecutionResult(false, "未找到要复制的文本");
            }

            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Philotes", text);
            clipboard.setPrimaryClip(clip);

            return new ExecutionResult(true, "文本已复制到剪贴板");
        } catch (Exception e) {
            return new ExecutionResult(false, "复制文本时出错: " + e.getMessage());
        }
    }

    /**
     * 获取动作摘要信息（用于 UI 显示）
     */
    public static String getActionSummary(ActionPlan plan) {
        if (plan == null || plan.getSlots() == null) {
            return "无效的动作计划";
        }

        StringBuilder summary = new StringBuilder();
        ActionType type = plan.getType();

        switch (type) {
            case CREATE_CALENDAR:
                summary.append("📅 创建日历事件\n");
                summary.append("标题: ").append(plan.getSlots().getOrDefault("title", "未指定")).append("\n");
                summary.append("时间: ").append(plan.getSlots().getOrDefault("time", "未指定")).append("\n");
                String location = plan.getSlots().get("location");
                if (location != null && !location.isEmpty()) {
                    summary.append("地点: ").append(location).append("\n");
                }
                break;

            case NAVIGATE:
                summary.append("🗺️ 开始导航\n");
                summary.append("目的地: ").append(plan.getSlots().getOrDefault("location", "未指定"));
                break;

            case ADD_TODO:
                summary.append("✅ 添加待办事项\n");
                summary.append("内容: ").append(plan.getSlots().getOrDefault("title", "未指定"));
                break;

            case COPY_TEXT:
                summary.append("📋 复制文本\n");
                String text = plan.getSlots().getOrDefault("content", plan.getOriginalText());
                if (text != null && text.length() > 50) {
                    text = text.substring(0, 47) + "...";
                }
                summary.append("内容: ").append(text);
                break;

            default:
                summary.append("未知操作");
        }

        return summary.toString();
    }
}
