package com.example.philotes.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 动态隐私防火墙：在 LLM 调用前扫描当前 App 包名与 OCR 文本，
 * 一旦命中敏感规则，系统将拒绝向云端发起任何请求，保障用户数据安全。
 */
public final class PrivacyFirewall {

    public enum PrivacyLevel {
        SAFE,
        SENSITIVE
    }

    // 已知的金融、支付、医疗类 App 包名前缀
    private static final Set<String> SENSITIVE_PACKAGES = new HashSet<>(Arrays.asList(
        "com.icbc",                      // 工商银行
        "com.ccb.smartlife",             // 建设银行
        "com.android.bankabc",           // 农业银行
        "com.bank.dcccb",                // 大城市商业银行
        "cmb.pb",                        // 招商银行
        "com.chinamobile.mobilemarket",  // 中国移动
        "com.pingan.pafa",               // 平安金融
        "com.eg.android.AlipayGphone",   // 支付宝
        "com.alipay",                    // 支付宝（其他变体）
        "com.unionpay",                  // 银联
        "com.chinapnr",                  // 民生银行
        "cn.gov.pbc.dcep",               // 数字人民币
        "com.sinolife",                  // 中国人寿
        "com.cpic.mobile",               // 中国太保
        "com.boc.bocmbci",               // 中国银行
        "com.bankcomm",                  // 交通银行
        "com.yitong.mbank",              // 北京银行
        "com.cmbchina",                  // 招商银行App
        "com.spdbankmy",                 // 浦发银行
        "com.cgbchina"                   // 广发银行
    ));

    // 银行卡号：16~19位连续数字（允许空格/横线分隔）
    private static final Pattern CREDIT_CARD_PATTERN =
        Pattern.compile("\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}(\\d{0,3})?\\b");

    // 中国居民身份证号：18位
    private static final Pattern ID_CARD_PATTERN =
        Pattern.compile("\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b");

    // 密码/验证码相关关键词
    private static final Pattern PASSWORD_KEYWORD_PATTERN =
        Pattern.compile("密\\s*码|验\\s*证\\s*码|支\\s*付\\s*密\\s*码|PIN|CVV|CVC", Pattern.CASE_INSENSITIVE);

    // 转账/交易金额关键词（配合敏感包名判定）
    private static final Pattern TRANSFER_PATTERN =
        Pattern.compile("转账|汇款|收款码|付款码|扫码支付|余额宝|提现");

    private PrivacyFirewall() {}

    /**
     * 综合检查当前情境是否触及隐私红线。
     *
     * @param currentPackageName 当前前台 App 包名
     * @param ocrText            OCR 提取的屏幕文本
     * @return SENSITIVE 时系统将强制本地推理，拒绝任何云端请求
     */
    public static PrivacyLevel check(String currentPackageName, String ocrText) {
        if (isSensitivePackage(currentPackageName)) {
            return PrivacyLevel.SENSITIVE;
        }
        if (containsSensitiveText(ocrText)) {
            return PrivacyLevel.SENSITIVE;
        }
        return PrivacyLevel.SAFE;
    }

    /**
     * 返回用于 UI 展示的隐私保护提示语。
     */
    public static String getPrivacyNotice(String packageName) {
        if (isSensitivePackage(packageName)) {
            return "隐私保护已激活：检测到金融/医疗类应用，已阻断云端请求，所有推理在本地完成。";
        }
        return "隐私保护已激活：屏幕内容包含敏感信息，已阻断云端请求，所有推理在本地完成。";
    }

    private static boolean isSensitivePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        for (String prefix : SENSITIVE_PACKAGES) {
            if (packageName.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean containsSensitiveText(String text) {
        if (text == null || text.isEmpty()) return false;
        return CREDIT_CARD_PATTERN.matcher(text).find()
            || ID_CARD_PATTERN.matcher(text).find()
            || PASSWORD_KEYWORD_PATTERN.matcher(text).find()
            || TRANSFER_PATTERN.matcher(text).find();
    }
}
