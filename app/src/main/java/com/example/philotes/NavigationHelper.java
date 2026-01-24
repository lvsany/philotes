package com.example.philotes;

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

    // 硬编码的目的地数据
    public static final String DESTINATION_NAME = "望京SOHO";
    public static final String DESTINATION_ADDRESS = "北京市朝阳区望京街道望京SOHO";
    public static final double DESTINATION_LAT = 39.9959;
    public static final double DESTINATION_LNG = 116.4774;

    // 地图应用包名
    private static final String AMAP_PACKAGE = "com.autonavi.minimap";       // 高德地图
    private static final String BAIDU_MAP_PACKAGE = "com.baidu.BaiduMap";    // 百度地图
    private static final String GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"; // Google Maps

    /**
     * 打开导航
     * 优先级：高德地图 > 百度地图 > Google Maps > 网页版高德
     *
     * @param context 上下文
     * @return 是否成功打开导航
     */
    public static boolean startNavigation(Context context) {
        // 尝试高德地图
        if (isAppInstalled(context, AMAP_PACKAGE)) {
            return openAmap(context);
        }

        // 尝试百度地图
        if (isAppInstalled(context, BAIDU_MAP_PACKAGE)) {
            return openBaiduMap(context);
        }

        // 尝试 Google Maps
        if (isAppInstalled(context, GOOGLE_MAPS_PACKAGE)) {
            return openGoogleMaps(context);
        }

        // 都没有安装，使用网页版高德地图
        return openAmapWeb(context);
    }

    /**
     * 打开高德地图导航
     */
    private static boolean openAmap(Context context) {
        try {
            // 高德地图导航URI格式
            // amapuri://route/plan/?sourceApplication=appname&slat=&slon=&sname=&dlat=&dlon=&dname=&dev=0&t=0
            String uri = String.format(
                    "amapuri://route/plan/?sourceApplication=Philotes&dlat=%f&dlon=%f&dname=%s&dev=0&t=0",
                    DESTINATION_LAT, DESTINATION_LNG, Uri.encode(DESTINATION_NAME)
            );

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(AMAP_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开高德地图导航成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开高德地图失败", e);
            return false;
        }
    }

    /**
     * 打开百度地图导航
     */
    private static boolean openBaiduMap(Context context) {
        try {
            // 百度地图导航URI格式
            // baidumap://map/direction?destination=latlng:lat,lng|name:name&coord_type=gcj02&mode=driving
            String uri = String.format(
                    "baidumap://map/direction?destination=latlng:%f,%f|name:%s&coord_type=gcj02&mode=driving&src=Philotes",
                    DESTINATION_LAT, DESTINATION_LNG, Uri.encode(DESTINATION_NAME)
            );

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(BAIDU_MAP_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开百度地图导航成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开百度地图失败", e);
            return false;
        }
    }

    /**
     * 打开 Google Maps 导航
     */
    private static boolean openGoogleMaps(Context context) {
        try {
            // Google Maps 导航URI格式
            // google.navigation:q=lat,lng
            String uri = String.format(
                    "google.navigation:q=%f,%f",
                    DESTINATION_LAT, DESTINATION_LNG
            );

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(GOOGLE_MAPS_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Log.d(TAG, "打开Google Maps导航成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开Google Maps失败", e);
            return false;
        }
    }

    /**
     * 打开网页版高德地图
     */
    private static boolean openAmapWeb(Context context) {
        try {
            String url = String.format(
                    "https://uri.amap.com/navigation?to=%f,%f,%s&mode=car&src=Philotes",
                    DESTINATION_LNG, DESTINATION_LAT, Uri.encode(DESTINATION_NAME)
            );

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Toast.makeText(context, "未安装地图应用，正在使用网页版", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "打开网页版高德地图成功");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "打开网页版地图失败", e);
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

    /**
     * 获取目的地信息的格式化字符串
     */
    public static String getDestinationSummary() {
        return String.format("🗺️ 目的地: %s\n📍 地址: %s\n🌐 坐标: %.4f, %.4f",
                DESTINATION_NAME,
                DESTINATION_ADDRESS,
                DESTINATION_LAT,
                DESTINATION_LNG);
    }
}
