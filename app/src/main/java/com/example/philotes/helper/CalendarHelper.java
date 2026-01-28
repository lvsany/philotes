package com.example.philotes.helper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import com.example.philotes.data.model.ActionPlan;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 日历事件创建助手类
 * 根据 ActionPlan 创建日历事件
 */
public class CalendarHelper {

    private static final String TAG = "CalendarHelper";

    /**
     * 根据 ActionPlan 创建日历事件
     *
     * @param context 上下文
     * @param actionPlan 动作计划
     * @return 创建成功返回事件URI，失败返回null
     */
    public static Uri createCalendarEvent(Context context, ActionPlan actionPlan) {
        if (actionPlan == null || actionPlan.getSlots() == null) {
            Log.e(TAG, "ActionPlan 或 slots 为空");
            return null;
        }

        Map<String, String> slots = actionPlan.getSlots();
        String title = slots.getOrDefault("title", "新事件");
        String timeStr = slots.get("time");
        String location = slots.getOrDefault("location", "");
        String content = slots.getOrDefault("content", "");

        // 解析时间
        Calendar startTime = parseTime(timeStr);
        if (startTime == null) {
            // 如果没有时间，使用当前时间+1小时
            startTime = Calendar.getInstance();
            startTime.add(Calendar.HOUR_OF_DAY, 1);
        }

        // 结束时间默认为开始时间+1小时
        Calendar endTime = (Calendar) startTime.clone();
        endTime.add(Calendar.HOUR_OF_DAY, 1);

        // 统一使用 Intent 方式创建日历事件（更可靠）
        return createCalendarEventViaIntent(context, title, location, content,
                startTime.getTimeInMillis(), endTime.getTimeInMillis());
    }

    /**
     * 使用 Intent 方式创建日历事件（调起系统日历UI）
     * 这种方式不需要设备登录日历账户
     * 返回特殊 URI "philotes://calendar/intent" 表示是通过 Intent 方式打开
     */
    private static Uri createCalendarEventViaIntent(Context context, String title,
            String location, String description, long startTimeMillis, long endTimeMillis) {
        try {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(CalendarContract.Events.CONTENT_URI);
            intent.putExtra(CalendarContract.Events.TITLE, title);
            intent.putExtra(CalendarContract.Events.EVENT_LOCATION, location);
            intent.putExtra(CalendarContract.Events.DESCRIPTION, description);
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis);
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis);

            // 检查是否有应用可以处理此 Intent
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "已打开系统日历创建事件");
                // 返回特殊标记 URI，表示这是 Intent 方式
                return Uri.parse("philotes://calendar/intent");
            } else {
                // 没有日历应用，尝试使用 chooser
                Intent chooser = Intent.createChooser(intent, "选择日历应用创建事件");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
                Log.d(TAG, "使用 Chooser 打开日历");
                return Uri.parse("philotes://calendar/intent");
            }
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "设备上没有安装日历应用", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "无法打开系统日历", e);
            return null;
        }
    }

    /**
     * 获取第一个可用的日历ID
     */
    private static long getFirstCalendarId(Context context) {
        String[] projection = {CalendarContract.Calendars._ID};
        android.database.Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            long calendarId = cursor.getLong(0);
            cursor.close();
            return calendarId;
        }

        if (cursor != null) {
            cursor.close();
        }
        return -1;
    }

    /**
     * 为事件添加提醒
     *
     * @param context     上下文
     * @param eventId     事件ID
     * @param minutesBefore 提前多少分钟提醒
     */
    private static void addReminder(Context context, long eventId, int minutesBefore) {
        try {
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Reminders.EVENT_ID, eventId);
            values.put(CalendarContract.Reminders.MINUTES, minutesBefore);
            values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);

            context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, values);
            Log.d(TAG, "提醒添加成功: 提前" + minutesBefore + "分钟");
        } catch (Exception e) {
            Log.e(TAG, "添加提醒失败", e);
        }
    }

    /**
     * 解析时间字符串为 Calendar 对象
     * 支持 ISO 8601 格式: YYYY-MM-DDTHH:MM:SS
     */
    private static Calendar parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }

        try {
            // 尝试解析 ISO 8601 格式
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(timeStr);
            if (date != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                return calendar;
            }
        } catch (Exception e) {
            Log.e(TAG, "解析时间失败: " + timeStr, e);
        }

        return null;
    }
}
