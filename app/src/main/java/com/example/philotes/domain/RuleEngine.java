package com.example.philotes.domain;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Lightweight local pre-filter for proactive intent detection.
 * It can be updated at runtime to support future remote-config/hot-update keywords.
 */
public final class RuleEngine {
    private static final String TAG = "RuleEngine";

    private static volatile RuleEngine instance;

    private final CopyOnWriteArrayList<String> keywordRules = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Pattern> regexRules = new CopyOnWriteArrayList<>();

    private RuleEngine() {
        resetDefaultRules();
    }

    public static RuleEngine getInstance() {
        if (instance == null) {
            synchronized (RuleEngine.class) {
                if (instance == null) {
                    instance = new RuleEngine();
                }
            }
        }
        return instance;
    }

    public String findFirstMatchedKeyword(String mergedText) {
        if (mergedText == null || mergedText.trim().isEmpty()) return null;
        String normalized = mergedText.toLowerCase(Locale.ROOT);
        for (String keyword : keywordRules) {
            if (normalized.contains(keyword)) return keyword;
        }
        for (Pattern pattern : regexRules) {
            if (pattern.matcher(normalized).find()) return pattern.pattern();
        }
        return null;
    }

    public boolean shouldTrigger(String mergedText) {
        if (mergedText == null || mergedText.trim().isEmpty()) {
            return false;
        }

        String normalized = mergedText.toLowerCase(Locale.ROOT);

        for (String keyword : keywordRules) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }

        for (Pattern pattern : regexRules) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }

        return false;
    }

    public void updateRules(List<String> keywords, List<String> regexes) {
        keywordRules.clear();
        regexRules.clear();

        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    keywordRules.add(keyword.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        if (regexes != null) {
            for (String regex : regexes) {
                if (regex == null || regex.trim().isEmpty()) {
                    continue;
                }
                try {
                    regexRules.add(Pattern.compile(regex.trim(), Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    Log.w(TAG, "Ignore invalid regex rule: " + regex);
                }
            }
        }
    }

    public void resetDefaultRules() {
        List<String> defaultKeywords = new ArrayList<>(java.util.Arrays.asList(
            // 日历/日程类
            "开会", "会议", "面试", "约", "预约", "约定", "约好", "聚餐", "聚会",
            "生日", "纪念日", "培训", "讲座", "研讨", "汇报", "演讲", "答辩",
            "明天", "后天", "下周", "下个月", "下午", "上午", "晚上", "早上",
            "点钟", "半", "提醒我", "别忘了", "记得",
            // 导航类
            "导航", "去哪", "怎么去", "如何去", "去哪里", "路线", "附近",
            "在哪", "地址", "位置", "到达", "出发", "到xx", "去xx",
            "机场", "火车站", "高铁站", "地铁站", "医院", "学校", "公司",
            // 待办类
            "待办", "任务", "to-do", "todo", "清单", "列表",
            "买", "购买", "采购", "需要", "还没", "忘了",
            "提交", "完成", "处理", "回复", "联系", "发邮件", "发消息",
            // 复制/保存类
            "保存", "记下", "记录", "摘录", "收藏", "复制"
        ));

        List<String> defaultRegexes = new ArrayList<>(java.util.Arrays.asList(
            // 时间模式：xx点/xx:xx
            "\\d{1,2}[点:：]\\d{0,2}",
            // 日期模式：x月x日、x/x
            "\\d{1,2}月\\d{1,2}[日号]",
            // 周几
            "周[一二三四五六七日天]|星期[一二三四五六七日天]",
            // 导航目的地模式
            "去.{1,10}(路|街|区|市|省|县|镇|村|楼|广场|中心|大厦|酒店|餐厅|公园)"
        ));

        updateRules(defaultKeywords, defaultRegexes);
    }

    /**
     * 在默认规则基础上追加用户自定义关键词，不清空已有规则。
     */
    public void addCustomKeywords(List<String> keywords) {
        if (keywords == null) return;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.trim().isEmpty()) {
                String normalized = keyword.trim().toLowerCase(Locale.ROOT);
                if (!keywordRules.contains(normalized)) {
                    keywordRules.add(normalized);
                }
            }
        }
    }

    public List<String> getKeywordRulesSnapshot() {
        return new ArrayList<>(keywordRules);
    }

    public List<String> getRegexRulesSnapshot() {
        List<String> list = new ArrayList<>();
        for (Pattern p : regexRules) {
            list.add(p.pattern());
        }
        return list;
    }
}
