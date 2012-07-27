package com.ailk.alipay.utils;

import static org.phw.config.impl.PhwConfigMgrFactory.getConfigMgr;

public interface AlipayConst {

    // 接口名称
    String FACE_ACCOUNT_TRADE_REPORT = "export_trade_account_report";

    // 交易类型（提现、支付）
    String REQTYPE_WITHDRAW = "WithDraw";
    String REQTYPE_CUSTOMTRADE = "CustomTrade";

    // 交易类型（帐务明细）
    String TRADETYPE_CUSTOMPAY = "0"; // 交易付款
    String TRADETYPE_COMMISION = "1"; // 佣金
    String TRADETYPE_INTEGRAL = "2"; // 积分
    String TRADETYPE_CREDITCARD = "3"; // 信用卡手续费
    String TRADETYPE_WITHDRAW = "4"; // 提现

    // 交易类型名称（帐务明细）
    String TRADETYPE_NAME_CUSTOMPAY = "交易付款"; // 交易付款
    String TRADETYPE_NAME_TRANSFER = "转账"; // 转账
    String TRADETYPE_NAME_COMMISION = "佣金"; // 佣金
    String TRADETYPE_NAME_INTEGRAL = "积分"; // 积分
    String TRADETYPE_NAME_CREDITCARD = "收费"; // 信用卡手续费
    String TRADETYPE_NAME_WITHDRAW = "提现"; // 体现

    // 交易类型代码
    String TRANSCODE_WITHDRAW = "5004";
    String TRANSCODE_CUSTOMTRADE = "3011,3012,6001";

    // 日志类型
    String LOG_TYPE_CUSTOMTRADE = "00";
    String LOG_TYPE_WITHDRAW = "01";

    /** 配置信息 */
    String ALIPAY_URL = getConfigMgr().getString("AlipayUrl");
    String PARTNER_ID = getConfigMgr().getString("PartnerId");
    String USER_ID = getConfigMgr().getString("UserId");
    String SAFE_KEY = getConfigMgr().getString("SafeKey");
    String NO_COUPON = getConfigMgr().getString("NoCoupon");
    String CHAR_SET = getConfigMgr().getString("CharSet");
    String SIGN_TYPE = getConfigMgr().getString("SignType");
    long CUSTOMTRADE_INTERVAL = getConfigMgr().getLong("CustomTradeInterval");

}
