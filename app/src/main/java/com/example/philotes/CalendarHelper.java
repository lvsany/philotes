package com.example.philotes;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 日历事件创建助手类
 * 使用硬编码数据创建日历事件
 */
public class CalendarHelper {

    private static final String TAG = "CalendarHelper";

    // 硬编码的事件数据
    public static final String EVENT_TITLE = "项目会议";
    public static final String EVENT_LOCATION = "望京SOHO 会议室A";
    public static final String EVENT_DESCRIPTION = "讨论项目进度，准备会议材料";

    // 事件时间: 2026-01-25 14:00 - 15:00
    public static final int EVENT_YEAR = 2026;
    public static final int EVENT_MONTH = Calendar.JANUARY; // 0-indexed
    public static final int EVENT_DAY = 25;
    public static final int EVENT_START_HOUR = 14;
    public static final int EVENT_START_MINUTE = 0;
    public static final int EVENT_END_HOUR = 15;
    public static final int EVENT_END_MINUTE = 0;

    /**
     * 创建日历事件
     *
     * @param context 上下文
     * @return 创建成功返回事件URI，失败返回null
     */
    public static Uri createCalendarEvent(Context context) {
        try {
            // 获取日历ID（使用第一个可用的日历）
            long calendarId = getFirstCalendarId(context);
            if (calendarId == -1) {
                Log.e(TAG, "没有找到可用的日历账户");
                return null;
            }

            // 计算事件开始和结束时间
            Calendar startTime = Calendar.getInstance();
            startTime.set(EVENT_YEAR, EVENT_MONTH, EVENT_DAY, EVENT_START_HOUR, EVENT_START_MINUTE, 0);
            startTime.set(Calendar.MILLISECOND, 0);

            Calendar endTime = Calendar.getInstance();
            endTime.set(EVENT_YEAR, EVENT_MONTH, EVENT_DAY, EVENT_END_HOUR, EVENT_END_MINUTE, 0);
            endTime.set(Calendar.MILLISECOND, 0);

            // 创建事件内容
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            values.put(CalendarContract.Events.TITLE, EVENT_TITLE);
            values.put(CalendarContract.Events.DESCRIPTION, EVENT_DESCRIPTION);
            values.put(CalendarContract.Events.EVENT_LOCATION, EVENT_LOCATION);
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
     * 获取事件信息的格式化字符串
     */
    public static String getEventSummary() {
        return String.format("📅 %s\n⏰ %d年%d月%d日 %02d:%02d-%02d:%02d\n📍 %s\n📝 %s",
                EVENT_TITLE,
                EVENT_YEAR, EVENT_MONTH + 1, EVENT_DAY,
                EVENT_START_HOUR, EVENT_START_MINUTE,
                EVENT_END_HOUR, EVENT_END_MINUTE,
                EVENT_LOCATION,
                EVENT_DESCRIPTION);
    }
}
