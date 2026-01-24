package com.example.philotes;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.util.Log;

/**
 * 待办事项助手类
 * 使用系统 Intent 创建提醒/备忘录
 */
public class TodoHelper {

    private static final String TAG = "TodoHelper";

    // 硬编码的待办数据
    public static final String TODO_TITLE = "准备会议材料";
    public static final String TODO_DESCRIPTION = "整理PPT和数据报告，准备项目会议";

    // 提醒时间设置
    public static final int REMINDER_HOUR = 10;
    public static final int REMINDER_MINUTE = 0;

    /**
     * 创建待办事项/提醒
     * 使用系统闹钟/提醒Intent
     *
     * @param context 上下文
     * @return 是否成功创建
     */
    public static boolean createTodo(Context context) {
        // 首先尝试创建系统提醒（使用AlarmClock）
        if (createAlarmReminder(context)) {
            return true;
        }

        // 如果失败，尝试使用备忘录Intent
        return createNote(context);
    }

    /**
     * 使用系统闹钟创建提醒
     */
    private static boolean createAlarmReminder(Context context) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, TODO_TITLE);
            intent.putExtra(AlarmClock.EXTRA_HOUR, REMINDER_HOUR);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, REMINDER_MINUTE);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false); // 显示UI让用户确认

            // 检查是否有应用可以处理
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "系统提醒创建成功");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "创建系统提醒失败", e);
        }
        return false;
    }

    /**
     * 使用备忘录/笔记应用创建笔记
     */
    private static boolean createNote(Context context) {
        try {
            // 使用通用的发送Intent创建备忘
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, TODO_TITLE);
            intent.putExtra(Intent.EXTRA_TEXT, TODO_DESCRIPTION);

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
     *
     * @param context 上下文
     * @param minutes 倒计时分钟数
     * @return 是否成功
     */
    public static boolean createTimer(Context context, int minutes) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, TODO_TITLE);
            intent.putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60); // 秒数
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "定时器创建成功");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "创建定时器失败", e);
        }
        return false;
    }

    /**
     * 获取待办信息的格式化字符串
     */
    public static String getTodoSummary() {
        return String.format("✅ 待办: %s\n📝 详情: %s\n⏰ 提醒时间: %02d:%02d",
                TODO_TITLE,
                TODO_DESCRIPTION,
                REMINDER_HOUR,
                REMINDER_MINUTE);
    }
}
