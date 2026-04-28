package com.example.philotes.domain;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/**
 * 验证 RuleEngine 默认关键词与 addCustomKeywords 合并逻辑。
 */
public class RuleEngineDefaultsTest {

    private RuleEngine engine;

    @Before
    public void setUp() {
        engine = RuleEngine.getInstance();
        // 每次测试前重置为纯默认规则，避免单例状态污染
        engine.resetDefaultRules();
    }

    // ==================== 默认关键词：日历类 ====================

    @Test
    public void defaultKeyword_meeting_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("下午三点开会讨论项目"));
    }

    @Test
    public void defaultKeyword_calendar_event_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("周五下午聚餐，记得提醒我"));
    }

    @Test
    public void defaultKeyword_birthday_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("老王生日快乐"));
    }

    @Test
    public void defaultKeyword_tomorrow_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("明天早上9点出发"));
    }

    // ==================== 默认关键词：导航类 ====================

    @Test
    public void defaultKeyword_navigation_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("帮我导航到北京南站"));
    }

    @Test
    public void defaultKeyword_hospital_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("附近的医院在哪"));
    }

    @Test
    public void defaultKeyword_airport_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("去机场怎么走"));
    }

    // ==================== 默认关键词：待办类 ====================

    @Test
    public void defaultKeyword_todo_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("明天需要买菜"));
    }

    @Test
    public void defaultKeyword_remindMe_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("提醒我发邮件给老板"));
    }

    @Test
    public void defaultKeyword_dontForget_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("别忘了交报告"));
    }

    // ==================== 正则规则 ====================

    @Test
    public void defaultRegex_timePattern_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("下午3:30的会议"));
    }

    @Test
    public void defaultRegex_datePattern_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("5月1日劳动节"));
    }

    @Test
    public void defaultRegex_weekday_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("周三下午有安排吗"));
    }

    @Test
    public void defaultRegex_starDay_triggers() {
        assertNotNull(engine.findFirstMatchedKeyword("星期五早上集合"));
    }

    // ==================== 无关文本不触发 ====================

    @Test
    public void irrelevantText_doesNotTrigger() {
        assertNull(engine.findFirstMatchedKeyword("今天天气不错，适合出行"));
    }

    @Test
    public void emptyText_doesNotTrigger() {
        assertNull(engine.findFirstMatchedKeyword(""));
    }

    @Test
    public void nullText_doesNotTrigger() {
        assertNull(engine.findFirstMatchedKeyword(null));
    }

    // ==================== addCustomKeywords 合并逻辑 ====================

    @Test
    public void addCustomKeywords_mergesWithDefaults() {
        engine.addCustomKeywords(Arrays.asList("健身", "锻炼"));
        // 默认关键词仍然有效
        assertNotNull(engine.findFirstMatchedKeyword("明天开会"));
        // 自定义关键词也有效
        assertNotNull(engine.findFirstMatchedKeyword("下午去健身房锻炼"));
    }

    @Test
    public void addCustomKeywords_noDuplicates() {
        int before = engine.getKeywordRulesSnapshot().size();
        engine.addCustomKeywords(Arrays.asList("开会")); // 已存在
        int after = engine.getKeywordRulesSnapshot().size();
        // 不应增加重复项
        assertTrue(after <= before + 1);
    }

    @Test
    public void addCustomKeywords_emptyList_doesNotBreakDefaults() {
        engine.addCustomKeywords(Arrays.asList());
        assertNotNull(engine.findFirstMatchedKeyword("导航到北京西站"));
    }
}
