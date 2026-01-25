package com.example.philotes.helper;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
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

        try {
            // 获取日历ID（使用第一个可用的日历）
            long calendarId = getFirstCalendarId(context);
            if (calendarId == -1) {
                Log.e(TAG, "没有找到可用的日历账户");
                return null;
            }

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

            // 创建事件内容
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            values.put(CalendarContract.Events.TITLE, title);
            values.put(CalendarContract.Events.DESCRIPTION, content);
            values.put(CalendarContract.Events.EVENT_LOCATION, location);
            values.put(CalendarContract.Events.DTSTART, startTime.getTimeInMillis());
            values.put(CalendarContract.Events.DTEND, endTime.getTimeInMillis());
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

            // 添加提醒（提前15分钟）
            values.put(CalendarContract.Events.HAS_ALARM, 1);

            // 插入事件
            ContentResolver cr = context.getContentResolver();
            Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, values);

            if (eventUri != null) {
                // 添加提醒
                long eventId = Long.parseLong(eventUri.getLastPathSegment());
                addReminder(context, eventId, 15); // 15分钟提前提醒
                Log.d(TAG, "日历事件创建成功: " + eventUri);
            }

            return eventUri;

        } catch (Exception e) {
            Log.e(TAG, "创建日历事件失败", e);
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
