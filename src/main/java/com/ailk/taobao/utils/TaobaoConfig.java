package com.ailk.taobao.utils;

import static org.phw.config.impl.PhwConfigMgrFactory.getConfigMgr;

/**
 * TOP平台相关配置信息。
 *
 * @author wanglei
 *
 * 2012-6-6
 */
public interface TaobaoConfig {

    /** 配置信息 */
    String TOP_URL = getConfigMgr().getString("TopUrl");
    String APP_KEY = getConfigMgr().getString("AppKey");
    String APP_SECRET = getConfigMgr().getString("AppSecret");
    String APP_SESSION = getConfigMgr().getString("AppSession");
    long ORDER_INTERVAL = getConfigMgr().getLong("OrderInterval");
    long REFUND_INTERVAL = getConfigMgr().getLong("RefundInterval");
    long NOTIFY_SEND_INTERVAL = getConfigMgr().getLong("NotifySendInterval");
    long MOD_SEND_INTERVAL = getConfigMgr().getLong("ModSendInterval");
    int PAGE = getConfigMgr().getInt("Page");
    boolean SEND_MSG = getConfigMgr().getBoolean("SendMsg");
}
