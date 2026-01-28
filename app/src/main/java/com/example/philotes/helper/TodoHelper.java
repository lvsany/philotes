package com.example.philotes.helper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.widget.Toast;

import com.example.philotes.data.model.ActionPlan;

import java.util.List;
import java.util.Map;

/**
 * 待办事项助手类
 * 复制内容到剪贴板，然后跳转到备忘录/便签应用
 */
public class TodoHelper {

    private static final String TAG = "TodoHelper";

    // 常见备忘录/便签应用包名
    private static final String[][] NOTE_APPS = {
            { "com.miui.notes", "com.miui.notes.ui.NotesListActivity" }, // 小米便签
            { "com.colornote", "com.socialnmobile.dictapps.notepad.color.note.NoteList" }, // ColorNote
            { "com.samsung.android.app.notes", null }, // 三星笔记
            { "com.google.android.keep", "com.google.android.keep.activities.BrowseActivity" }, // Google Keep
            { "com.evernote", null }, // Evernote
            { "com.microsoft.office.onenote", null }, // OneNote
    };

    /**
     * 根据 ActionPlan 创建待办事项
     * 1. 先复制内容到剪贴板
     * 2. 然后尝试打开备忘录/便签应用
     *
     * @param context    上下文
     * @param actionPlan 动作计划
     * @return 是否成功
     */
    public static boolean createTodo(Context context, ActionPlan actionPlan) {
        if (context == null || actionPlan == null || actionPlan.getSlots() == null) {
            Log.e(TAG, "参数为空");
            return false;
        }

        Map<String, String> slots = actionPlan.getSlots();
        String title = slots.getOrDefault("title", "待办事项");
        String content = slots.getOrDefault("content", "");

        // 组合完整内容
        String fullContent = title;
        if (!content.isEmpty()) {
            fullContent = title + "\n" + content;
        }

        // 1. 复制到剪贴板
        copyToClipboard(context, fullContent);

        // 2. 尝试打开备忘录应用
        if (openNoteApp(context)) {
            return true;
        }

        // 3. 如果没有专门的备忘录应用，使用分享方式
        return shareToNoteApp(context, title, fullContent);
    }

    /**
     * 复制内容到剪贴板
     */
    private static void copyToClipboard(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Philotes Todo", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "内容已复制到剪贴板: " + text);
        } catch (Exception e) {
            Log.e(TAG, "复制到剪贴板失败", e);
        }
    }

    /**
     * 尝试打开已安装的备忘录/便签应用
     */
    private static boolean openNoteApp(Context context) {
        PackageManager pm = context.getPackageManager();

        // 遍历已知的备忘录应用
        for (String[] app : NOTE_APPS) {
            String packageName = app[0];
            String activityName = app[1];

            try {
                // 检查应用是否已安装
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);

                Intent intent;
                if (activityName != null) {
                    intent = new Intent();
                    intent.setComponent(new ComponentName(packageName, activityName));
                } else {
                    intent = pm.getLaunchIntentForPackage(packageName);
                }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d(TAG, "已打开备忘录应用: " + packageName);
                    Toast.makeText(context, "已打开便签，请粘贴创建待办", Toast.LENGTH_LONG).show();
                    return true;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // 应用未安装，继续尝试下一个
            } catch (Exception e) {
                Log.e(TAG, "打开应用失败: " + packageName, e);
            }
        }

        Log.w(TAG, "未找到已安装的备忘录应用");
        return false;
    }

    /**
     * 使用分享方式打开备忘录
     */
    private static boolean shareToNoteApp(Context context, String title, String content) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
            intent.putExtra(Intent.EXTRA_TEXT, content);

            Intent chooser = Intent.createChooser(intent, "选择便签/备忘录应用");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

            Log.d(TAG, "已打开分享选择器");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "分享失败", e);
            return false;
        }
    }
}

