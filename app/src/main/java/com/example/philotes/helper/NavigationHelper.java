package com.example.philotes.helper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

/**
 * 导航跳转助手类
 * 支持高德地图、百度地图、Google Maps
 */
public class NavigationHelper {

    private static final String TAG = "NavigationHelper";

    // 地图应用包名
    private static final String AMAP_PACKAGE = "com.autonavi.minimap";       // 高德地图
    private static final String BAIDU_MAP_PACKAGE = "com.baidu.BaiduMap";    // 百度地图
    private static final String GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"; // Google Maps

    /**
     * 根据地点名称打开导航
     * 优先级：高德地图 > 百度地图 > Google Maps > 通用地图
     *
     * @param context 上下文
     * @param location 目的地名称
     * @return 是否成功打开导航
     */
    public static boolean startNavigation(Context context, String location) {
        if (location == null || location.isEmpty()) {
            Log.e(TAG, "目的地为空");
            return false;
        }

        // 尝试高德地图
        if (isAppInstalled(context, AMAP_PACKAGE)) {
            return openAmap(context, location);
        }

        // 尝试百度地图
        if (isAppInstalled(context, BAIDU_MAP_PACKAGE)) {
            return openBaiduMap(context, location);
        }

        // 尝试 Google Maps
        if (isAppInstalled(context, GOOGLE_MAPS_PACKAGE)) {
            return openGoogleMaps(context, location);
        }

        // 都没有安装，使用通用 geo intent
        return openGenericMap(context, location);
    }

    /**
     * 打开高德地图导航
     */
    private static boolean openAmap(Context context, String location) {
        try {
            // 使用关键字搜索模式
            String uri = String.format(
                    "amapuri://route/plan/?sourceApplication=Philotes&dname=%s&dev=0&t=0",
                    Uri.encode(location)
            );

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(AMAP_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开高德地图导航成功: " + location);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开高德地图失败", e);
            return false;
        }
    }

    /**
     * 打开百度地图导航
     */
    private static boolean openBaiduMap(Context context, String location) {
        try {
            // 使用地点名称搜索
            String uri = String.format(
                    "baidumap://map/direction?destination=name:%s&mode=driving&src=Philotes",
                    Uri.encode(location)
            );

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(BAIDU_MAP_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开百度地图导航成功: " + location);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开百度地图失败", e);
            return false;
        }
    }

    /**
     * 打开 Google Maps 导航
     */
    private static boolean openGoogleMaps(Context context, String location) {
        try {
            // 使用地点名称搜索
            String uri = "google.navigation:q=" + Uri.encode(location);

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(GOOGLE_MAPS_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开Google Maps导航成功: " + location);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开Google Maps失败", e);
            return false;
        }
    }

    /**
     * 打开通用地图应用（使用 geo: URI）
     */
    private static boolean openGenericMap(Context context, String location) {
        try {
            // 使用 geo: URI，会由系统选择可用的地图应用
            String uri = "geo:0,0?q=" + Uri.encode(location);

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开系统地图应用成功: " + location);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开地图失败", e);
            Toast.makeText(context, "无法打开地图应用", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 检查应用是否已安装
     */
    private static boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
