package com.ailk.tenpay.keys;

import static org.phw.config.impl.PhwConfigMgrFactory.getConfigMgr;

public interface TenpayConst {

    /** 配置信息 */
    String GATE_URL        = getConfigMgr().getString("TenpayGateUrl");
    String PARTNER_ID      = getConfigMgr().getString("TenpayPartner");
    String USER_ID         = getConfigMgr().getString("TenpayUserId");
    String SAFE_KEY        = getConfigMgr().getString("TenpayKey");
    String OP_USER_ID_VAL  = getConfigMgr().getString("TenpayOpUserId");
    String OP_USER_PWD     = getConfigMgr().getString("TenpayOpUserPwd");
    String METHOD          = getConfigMgr().getString("TenpayMethod");
    int    TIME_OUT        = getConfigMgr().getInt("TenpayTimeOut");
    String JKS_FILE_PATH   = getConfigMgr().getString("JksFilePath");
    String CERT_FILE_PATH  = getConfigMgr().getString("CertFilePath");
    String CHAR_SET        = getConfigMgr().getString("Charset");
    String PROXY_HOST_NAME = getConfigMgr().getString("TenpayProxyHostName");
    int    PROXY_PORT      = getConfigMgr().getInt("TenpayProxyPort");

    /** 接口参数 */
    String INPUT_CHARSET   = "input_charset";
    String PARTNER         = "partner";
    String OUT_TRADE_NO    = "out_trade_no";
    String TRANSACTION_ID  = "transaction_id";
    String OUT_REFUND_NO   = "out_refund_no";
    String TOTAL_FEE       = "total_fee";
    String REFUND_FEE      = "refund_fee";
    String OP_USER_ID      = "op_user_id";
    String OP_USER_PASSWD  = "op_user_passwd";
    String RECV_USER_ID    = "recv_user_id";
    String RECCV_USER_NAME = "reccv_user_name";

    /** eop返回 */
    String EOP_CODE        = "EopCode";
    String EOP_MSG         = "EopMsg";

}
