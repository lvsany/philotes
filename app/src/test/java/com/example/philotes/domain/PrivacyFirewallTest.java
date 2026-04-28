package com.example.philotes.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PrivacyFirewallTest {

    // ==================== 包名检测 ====================

    @Test
    public void sensitivePackage_alipay_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.eg.android.AlipayGphone", "正常文本");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    @Test
    public void sensitivePackage_cmb_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("cmb.pb", "正常文本");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    @Test
    public void sensitivePackage_icbc_prefix_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.icbc.android", "正常文本");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    @Test
    public void normalPackage_wechat_withCleanText_returnsSafe() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.tencent.mm", "下午三点开会");
        assertEquals(PrivacyFirewall.PrivacyLevel.SAFE, level);
    }

    @Test
    public void nullPackage_withCleanText_returnsSafe() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check(null, "明天导航去机场");
        assertEquals(PrivacyFirewall.PrivacyLevel.SAFE, level);
    }

    // ==================== OCR 文本检测：银行卡号 ====================

    @Test
    public void creditCard_16digits_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.tencent.mm",
            "卡号 6222 0203 1000 1234");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    @Test
    public void creditCard_noSpaces_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.example.notes",
            "付款账户：6228480402564890123");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    // ==================== OCR 文本检测：身份证 ====================

    @Test
    public void idCard_valid18digit_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.example.notes",
            "身份证：110101199001011234");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    @Test
    public void idCard_withX_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.example.notes",
            "证件号码 44010519900307261X");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    // ==================== OCR 文本检测：密码关键词 ====================

    @Test
    public void passwordKeyword_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.tencent.mm",
            "请输入支付密码");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    @Test
    public void verificationCode_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.tencent.mm",
            "您的验证码是 358291，5分钟内有效");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    // ==================== OCR 文本检测：转账关键词 ====================

    @Test
    public void transferKeyword_returnsSensitive() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.tencent.mm",
            "确认转账金额：¥500.00");
        assertEquals(PrivacyFirewall.PrivacyLevel.SENSITIVE, level);
    }

    // ==================== 正常文本应通过 ====================

    @Test
    public void normalCalendarText_returnsSafe() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.example.app",
            "明天下午3点在第三会议室开会，记得提前准备PPT");
        assertEquals(PrivacyFirewall.PrivacyLevel.SAFE, level);
    }

    @Test
    public void normalNavigationText_returnsSafe() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("com.tencent.mm",
            "你在哪？我去北京南站接你");
        assertEquals(PrivacyFirewall.PrivacyLevel.SAFE, level);
    }

    @Test
    public void emptyText_returnsSafe() {
        PrivacyFirewall.PrivacyLevel level = PrivacyFirewall.check("", "");
        assertEquals(PrivacyFirewall.PrivacyLevel.SAFE, level);
    }
}
