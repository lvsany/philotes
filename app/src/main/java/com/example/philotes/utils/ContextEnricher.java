package com.example.philotes.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;

import java.util.Calendar;

/**
 * 情境感知模块：采集设备当前状态（时段、电量、网络类型、前台应用），
 * 生成结构化描述符注入 LLM Prompt，使大模型能够感知物理世界上下文进行决策。
 */
public final class ContextEnricher {

    private ContextEnricher() {}

    /**
     * 构建当前设备的情境描述符字符串。
     *
     * @param context            Android Context
     * @param frontPackageName   当前前台应用包名（可为空）
     * @return 结构化情境字符串，供拼入 LLM Prompt
     */
    public static String buildContextDescriptor(Context context, String frontPackageName) {
        StringBuilder sb = new StringBuilder();
        sb.append("[设备情境]\n");
        sb.append("当前时段: ").append(getTimeSlot()).append("\n");

        int battery = getBatteryLevel(context);
        if (battery >= 0) {
            sb.append("设备电量: ").append(battery).append("%");
            if (battery <= 15) sb.append("（低电量，请优先执行简单操作）");
            sb.append("\n");
        }

        sb.append("网络状态: ").append(getNetworkType(context)).append("\n");

        if (frontPackageName != null && !frontPackageName.isEmpty()) {
            String appHint = resolveAppHint(frontPackageName);
            sb.append("当前应用: ").append(frontPackageName);
            if (appHint != null) sb.append("（").append(appHint).append("）");
            sb.append("\n");
        }

        sb.append("\n请结合以上情境对用户意图进行最合理的解析。");
        return sb.toString();
    }

    private static String getTimeSlot() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int minute = Calendar.getInstance().get(Calendar.MINUTE);
        String timeStr = String.format("%02d:%02d", hour, minute);
        if (hour >= 6 && hour < 9)   return "清晨(" + timeStr + ")";
        if (hour >= 9 && hour < 12)  return "上午(" + timeStr + ")";
        if (hour >= 12 && hour < 14) return "中午(" + timeStr + ")";
        if (hour >= 14 && hour < 18) return "下午(" + timeStr + ")";
        if (hour >= 18 && hour < 22) return "晚上(" + timeStr + ")";
        return "深夜(" + timeStr + ")，用户可能不便操作，建议静默执行";
    }

    private static int getBatteryLevel(Context context) {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus == null) return -1;
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            return (int) (level * 100f / scale);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String getNetworkType(Context context) {
        try {
            ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "未知";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return "无网络（建议仅使用本地模型）";
                NetworkCapabilities cap = cm.getNetworkCapabilities(network);
                if (cap == null) return "无网络（建议仅使用本地模型）";
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "移动数据";
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "以太网";
            }
            return "已连接";
        } catch (Exception e) {
            return "未知";
        }
    }

    private static String resolveAppHint(String packageName) {
        if (packageName == null) return null;
        if (packageName.contains("wechat") || packageName.equals("com.tencent.mm")) return "微信聊天";
        if (packageName.contains("calendar") || packageName.contains("calendar")) return "日历应用";
        if (packageName.contains("map") || packageName.contains("amap") || packageName.contains("baidu.mapapp")) return "地图导航";
        if (packageName.contains("mail") || packageName.contains("email")) return "邮件";
        if (packageName.contains("note") || packageName.contains("todo") || packageName.contains("memo")) return "笔记/待办";
        if (packageName.contains("browser") || packageName.contains("chrome") || packageName.contains("miui.browser")) return "浏览器";
        if (packageName.contains("dingding") || packageName.contains("dingtalk")) return "钉钉办公";
        if (packageName.contains("feishu") || packageName.contains("lark")) return "飞书办公";
        return null;
    }
}
