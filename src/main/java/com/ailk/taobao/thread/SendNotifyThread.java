package com.ailk.taobao.thread;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.phw.core.lang.Collections;
import org.phw.core.lang.Dates;
import org.phw.core.lang.Strings;
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

public class SendNotifyThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SendNotifyThread.class);
    private String tid;
    private String lgtsId;
    private String lgtsOrder;
    private String validLgtsOrder;

    public SendNotifyThread(Map params) {
        tid = (String) params.get("Tid");
        lgtsId = (String) params.get("LgtsId");
        lgtsOrder = (String) params.get("LgtsOrder");
        validLgtsOrder = (String) params.get("ValidLgtsOrder");
    }

    @Override
    public void run() {
        logger.info("发货通知淘宝线程Start");
        LogisticsOfflineSendRequest req = new LogisticsOfflineSendRequest();
        Map retMap = new HashMap();
        if (!validLgtsInfo(req, retMap)) {
            return;
        }
        doNotify(req);
        logger.info("发货通知淘宝线程End");
    }

    private boolean validLgtsInfo(LogisticsOfflineSendRequest req, Map retMap) {
        // 淘宝订单号
        req.setTid(Long.parseLong(tid));
        // 货运单号
        req.setOutSid(lgtsOrder);
        // 转换淘宝物流公司编码
        Map lgtsMap = (Map) new ServiceCall("eop").call2(TaobaoService.PARSE2_TAOBAO_LGTS, lgtsId);
        req.setCompanyCode(Collections.isEmpty(lgtsMap) ? "其他" : (String) lgtsMap.get("THIRD_LGTS_CODE"));
        // 校验货运单号
        String regex = Collections.isEmpty(lgtsMap) ? "" : (String) lgtsMap.get("THIRD_LGTS_EXPRESSION");
        if (!"T".equals(validLgtsOrder) || Strings.isEmpty(regex) || lgtsOrder.matches(regex)) {
            return true;
        }

        // 校验失败处理
        logger.warn("货运单号格式错误: TID=" + tid);
        retMap = Collections.asMap("BizCode", "F", "BizDesc", "货运单号格式错误");
        LogBean logBean = new LogBean();
        logBean.setStatus("ex"); // 自定义异常信息
        logBean.setDesc("物流信息校验失败: LgtsOrder=" + lgtsOrder);
        log(false, false, logBean);
        return false;
    }

    private Map doNotify(LogisticsOfflineSendRequest req) {
        Map retMap = new HashMap();
        LogBean logBean = new LogBean();
        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                TaobaoConfig.APP_SECRET);
        LogisticsOfflineSendResponse rsp;
        try {
            rsp = client.execute(req, TaobaoConfig.APP_SESSION);
            Shipping shipping = rsp.getShipping();
            if (shipping != null && shipping.getIsSuccess()) {
                logger.info("发货通知淘宝成功#Tid={}, #LgtsOrder={}", tid, lgtsOrder);
                int count = (Integer) new ServiceCall("eop").call2(TaobaoService.REFRESH_SENDSTATUS, tid, "01");
                logBean.setParam(TaobaoConst.SEND, count > 0 ? TaobaoConst.SENDSUCC : TaobaoConst.SENDPROCFAIL);
                retMap = Collections.asMap("BizCode", "T", "BizDesc", "成功");
                // 接口成功，淘宝处理成功，忽略本地更新异常
                log(true, true, logBean);
            }
            else {
                logger.info("发货通知淘宝失败#Tid={}, #LgtsOrder={}", tid, lgtsOrder);
                logBean.setParam(TaobaoConst.SEND, TaobaoConst.SENDRETFAIL);
                retMap = Collections.asMap("BizCode", "F", "BizDesc", rsp.getMsg() + ": " + rsp.getSubMsg());
                // 接口成功，淘宝处理失败
                log(true, false, logBean);
            }
        }
        catch (Exception e) {
            logger.error("淘宝发货处理接口调用异常:" + tid, e);
            logBean.setParam(TaobaoConst.SEND, TaobaoConst.SENDFAIL);
            retMap = Collections.asMap("BizCode", "F", "BizDesc", "接口调用异常:" + e.getMessage());
            // 接口失败
            log(false, false, logBean);
        }
        return retMap;
    }

    private void log(boolean isFaceOK, boolean isSendOk, LogBean logBean) {
        List logBeanList = new ArrayList<LogBean>();
        logBean.setTid(tid);
        logBeanList.add(logBean);

        String now = Dates.format(Calendar.getInstance().getTime(), TaobaoConst.DATE_FORMAT);
        String faceCode = isFaceOK ? TaobaoConst.ISUCC : TaobaoConst.IFAIL;
        long succCount = isSendOk ? 1l : 0l;
        long failCount = isSendOk ? 0l : 1l;

        new ServiceCall("eop").call2(TaobaoService.LOG, TaobaoConst.SEND, now, now,
                faceCode, 1l, succCount, failCount, logBeanList, 1l);
    }

}
