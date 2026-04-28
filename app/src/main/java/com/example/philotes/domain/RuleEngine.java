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
        updateRules(Collections.emptyList(), Collections.emptyList());
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
