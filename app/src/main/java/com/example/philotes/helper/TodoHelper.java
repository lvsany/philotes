package com.example.philotes.helper;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

import com.example.philotes.data.model.ActionPlan;

import java.util.Map;

/**
 * 待办事项助手类
 * 使用系统 Intent 创建提醒/备忘录
 */
public class TodoHelper {

    private static final String TAG = "TodoHelper";

    /**
     * 根据 ActionPlan 创建待办事项/提醒
     *
     * @param context 上下文
     * @param actionPlan 动作计划
     * @return 是否成功创建
     */
    public static boolean createTodo(Context context, ActionPlan actionPlan) {
        if (context == null || actionPlan == null || actionPlan.getSlots() == null) {
            Log.e(TAG, "参数为空");
            return false;
        }

        Map<String, String> slots = actionPlan.getSlots();
        String title = slots.getOrDefault("title", "待办事项");
        String content = slots.getOrDefault("content", "");

        // 首先尝试创建系统提醒
        if (createAlarmReminder(context, title)) {
            return true;
        }

        // 如果失败，尝试使用备忘录Intent
        return createNote(context, title, content);
    }

    /**
     * 使用系统闹钟创建提醒
     */
    private static boolean createAlarmReminder(Context context, String title) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, title);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
            Log.d(TAG, "系统提醒创建成功");
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "未找到时钟应用", e);
        } catch (SecurityException e) {
            Log.e(TAG, "缺少设置闹钟的权限", e);
        } catch (Exception e) {
            Log.e(TAG, "创建系统提醒失败", e);
        }
        return false;
    }

    /**
     * 使用备忘录/笔记应用创建笔记
     */
    private static boolean createNote(Context context, String title, String content) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
            intent.putExtra(Intent.EXTRA_TEXT, content.isEmpty() ? title : content);

            Intent chooser = Intent.createChooser(intent, "选择应用创建待办");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

            Log.d(TAG, "备忘录创建Intent已发送");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "创建备忘录失败", e);
            return false;
        }
    }

    /**
     * 创建定时器（倒计时）作为替代方案
     */
    public static boolean createTimer(Context context, String title, int minutes) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, title);
            intent.putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60); // 秒数
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
            Log.d(TAG, "定时器创建成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "创建定时器失败", e);
        }
        return false;
    }
}
