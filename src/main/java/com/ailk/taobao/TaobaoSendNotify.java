package com.ailk.taobao;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.phw.core.lang.Collections;
import org.phw.core.lang.Dates;
import org.phw.core.lang.Strings;
import org.phw.eop.support.EopAction;
import org.phw.eop.support.EopActionException;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.taobao.utils.TaobaoConfig;
import com.ailk.thirdservice.taobao.TaobaoService;
import com.ailk.thirdservice.taobao.log.LogBean;
import com.ailk.thirdservice.taobao.utils.TaobaoConst;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.domain.Shipping;
import com.taobao.api.request.LogisticsOfflineSendRequest;
import com.taobao.api.response.LogisticsOfflineSendResponse;

public class TaobaoSendNotify extends EopAction {
    private static final Logger logger = LoggerFactory.getLogger(TaobaoSendNotify.class);
    // http://110.75.1.12/router/rest
    private static final String topUrlIp = "http://110.75.24.82/router/rest";

    @Override
    public Object doAction() throws EopActionException {
        LogisticsOfflineSendRequest req = new LogisticsOfflineSendRequest();
        if (!validLgtsInfo(req)) {
            return Collections.asMap("BizCode", "F", "BizDesc", "货运单号格式错误");
        }
        return doNotify(req, TaobaoConfig.TOP_URL, true);
    }

    private boolean validLgtsInfo(LogisticsOfflineSendRequest req) {
        // 淘宝订单号
        req.setTid(Long.parseLong(getStr("Tid")));
        // 货运单号
        req.setOutSid(getStr("LgtsOrder"));
        // 商城物流公司标识
        String lgtsId = getStr("LgtsId");
        // 转换淘宝物流公司编码
        Map lgtsMap = (Map) new ServiceCall("eop").call2(TaobaoService.PARSE2_TAOBAO_LGTS, lgtsId);
        req.setCompanyCode(Collections.isEmpty(lgtsMap) ? "其他" : (String) lgtsMap.get("THIRD_LGTS_CODE"));
        // 校验货运单号
        String regex = Collections.isEmpty(lgtsMap) ? "" : (String) lgtsMap.get("THIRD_LGTS_EXPRESSION");
        if (!"T".equals(getStr("ValidLgtsOrder")) || Strings.isEmpty(regex) || getStr("LgtsOrder").matches(regex)) {
            return true;
        }

        // 校验失败处理
        LogBean logBean = new LogBean();
        logBean.setStatus("ex"); // 自定义异常信息
        logBean.setDesc("物流信息校验失败：LgtsOrder=" + getStr("LgtsOrder"));
        log(false, false, logBean);
        return false;
    }

    private Map doNotify(LogisticsOfflineSendRequest req, String url, boolean tryAgain) {
        logger.error("发货通知淘宝接口URL: " + url);
        Map retMap = new HashMap();
        LogBean logBean = new LogBean();
        //TaobaoConfig.TOP_URL
        TaobaoClient client = new DefaultTaobaoClient(url, TaobaoConfig.APP_KEY, TaobaoConfig.APP_SECRET);
        LogisticsOfflineSendResponse rsp;
        try {
            rsp = client.execute(req, TaobaoConfig.APP_SESSION);
            Shipping shipping = rsp.getShipping();
            if (shipping != null && shipping.getIsSuccess()) {
                logger.info("发货通知淘宝成功#Tid={}, #LgtsOrder={}", getStr("Tid"), getStr("LgtsOrder"));
                int count = (Integer) new ServiceCall("eop").call2(TaobaoService.REFRESH_SENDSTATUS, getStr("Tid"),
                        "01");
                logBean.setParam(TaobaoConst.SEND, count > 0 ? TaobaoConst.SENDSUCC : TaobaoConst.SENDPROCFAIL);
                retMap = Collections.asMap("BizCode", "T", "BizDesc", "成功");
                // 接口成功，淘宝处理成功，忽略本地更新异常
                log(true, true, logBean);
            }
            else {
                logger.info("发货通知淘宝失败#Tid={}, #LgtsOrder={}", getStr("Tid"), getStr("LgtsOrder"));
                logBean.setParam(TaobaoConst.SEND, TaobaoConst.SENDRETFAIL);
                retMap = Collections.asMap("BizCode", "F", "BizDesc", rsp.getSubMsg());
                // 接口成功，淘宝处理失败
                log(true, false, logBean);
            }
        }
        catch (Exception e) {
            logger.error("淘宝发货处理接口调用异常: " + getStr("Tid"), e);
            logger.error("e.getMessage(): " + e.getMessage());
            if (tryAgain && !Strings.isEmpty(e.getMessage()) && e.getMessage().contains("UnknownHostException")) {
                logger.error("淘宝发货处理接口调用Try Again With IP !");
                return doNotify(req, topUrlIp, false);
            }
            logBean.setParam(TaobaoConst.SEND, TaobaoConst.SENDFAIL);
            retMap = Collections.asMap("BizCode", "F", "BizDesc", "接口调用异常:" + e.getMessage());
            // 接口失败
            log(false, false, logBean);
        }
        return retMap;
    }

    private void log(boolean isFaceOK, boolean isSendOk, LogBean logBean) {
        try {
            List logBeanList = new ArrayList<LogBean>();
            logBean.setTid(getStr("Tid"));
            logBeanList.add(logBean);

            String now = Dates.format(Calendar.getInstance().getTime(), TaobaoConst.DATE_FORMAT);
            String faceCode = isFaceOK ? TaobaoConst.ISUCC : TaobaoConst.IFAIL;
            long succCount = isSendOk ? 1l : 0l;
            long failCount = isSendOk ? 0l : 1l;

            new ServiceCall("eop").call2(TaobaoService.LOG, TaobaoConst.SEND, now, now,
                    faceCode, 1l, succCount, failCount, logBeanList, 1l);
        }
        catch (Exception e) {
            // 忽略入库异常
            logger.error("TaobaoSendNotify log method Error" + getStr("Tid"), e);
        }
    }
}
