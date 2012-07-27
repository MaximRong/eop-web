package com.ailk.alipay.thread;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.phw.core.lang.Collections;
import org.phw.core.lang.Dates;
import org.phw.core.lang.Pair;
import org.phw.core.lang.Strings;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.alipay.utils.AlipayClient;
import com.ailk.alipay.utils.AlipayConst;
import com.ailk.thirdservice.alipay.AlipayService;
import com.ailk.thirdservice.alipay.TradeDetailBean;

/**
 * 支付宝（客户支付）帐务明细获取。
 *
 * @author wanglei
 *
 * 2012-6-4
 */
public class GetCustomTradeThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GetCustomTradeThread.class);
    private static final Pattern COMMISION_PATTERN = Pattern.compile("\\{\\d+\\}");
    private static final Pattern INTEGRAL_PATTERN = Pattern.compile("返点积分\\d+");

    @Override
    public void run() {
        logger.info("获取支付宝（客户支付）帐务明细线程Start");
        Pair datePair = createStartEndTime();
        Map params = new HashMap<String, String>();
        params.put("gmt_create_start", datePair.getFirst());
        params.put("gmt_create_end", datePair.getSecond());
        params.put("trans_code", AlipayConst.TRANSCODE_CUSTOMTRADE);
        try {
            Map response = AlipayClient.exportTradeAccountReport(params);
            if ("T".equals(response.get("CheckCode"))) {
                parseTradeDetail(response);
            }
            response.put("log_type", AlipayConst.LOG_TYPE_CUSTOMTRADE); // 帐务明细
            response.put("start_time", datePair.getFirst()); // 开始时间
            response.put("end_time", datePair.getSecond()); // 结束时间
            new ServiceCall("eop").call2(AlipayService.ALIPAY_SRV_PATH + "recordTradeReport", response);
        }
        catch (Exception e) {
            logger.error("获取支付宝（客户支付）帐务明细线程Exception", e);
            throw new RuntimeException(e);
        }
        logger.info("获取支付宝（客户支付）帐务明细线程End");
    }

    /**
     * 生成起止时间。
     * @return
     */
    private Pair createStartEndTime() {
        Date endDate = Calendar.getInstance().getTime();
        long endMillis = endDate.getTime();
        long startMillis = endMillis - AlipayConst.CUSTOMTRADE_INTERVAL;
        Date startDate = new Date(startMillis);
        return new Pair<String, String>(Dates.format(startDate), Dates.format(endDate));
    }

    /**
     * 解析帐务明细CSV数据。
     * @param response
     */
    private void parseTradeDetail(Map response) {
        List<TradeDetailBean> csvList = (List<TradeDetailBean>) response.get("CsvList");
        if (Collections.isEmpty(csvList)) {
            return;
        }

        // 客户支付明细列表
        List<TradeDetailBean> customPay = new ArrayList<TradeDetailBean>();
        for (TradeDetailBean detail : csvList) {
            if (detail.getTradeType().equals(AlipayConst.TRADETYPE_CUSTOMPAY)) {
                customPay.add(detail);
            }
        }

        // 其他明细与淘宝订单号做映射
        for (TradeDetailBean detail : csvList) {
            if (detail.getTradeType().equals(AlipayConst.TRADETYPE_CUSTOMPAY) || !"T".equals(detail.getCheckCode())) {
                continue;
            }
            // 信用卡手续费
            if (detail.getTradeType().equals(AlipayConst.TRADETYPE_CREDITCARD)) {
                String outerId = detail.getOuterId();
                for (TradeDetailBean bean : customPay) {
                    if (outerId.equals(bean.getTradeId())) {
                        detail.setTaobaoId(bean.getTaobaoId());
                        continue;
                    }
                }
            }
            // 佣金
            else if (detail.getTradeType().equals(AlipayConst.TRADETYPE_COMMISION)) {
                String remark = detail.getRemark();
                if (!Strings.isEmpty(remark)) {
                    Matcher matcher = COMMISION_PATTERN.matcher(remark);
                    if (matcher.find()) {
                        String group = matcher.group();
                        String outerId = group.substring(1, group.length() - 1);
                        for (TradeDetailBean bean : customPay) {
                            if (outerId.equals(bean.getTaobaoId())) {
                                detail.setTaobaoId(bean.getTaobaoId());
                                continue;
                            }
                        }
                    }
                }
            }
            // 积分
            else if (detail.getTradeType().equals(AlipayConst.TRADETYPE_INTEGRAL)) {
                String remark = detail.getRemark();
                if (!Strings.isEmpty(remark)) {
                    Matcher matcher = INTEGRAL_PATTERN.matcher(remark);
                    if (matcher.find()) {
                        String group = matcher.group();
                        String outerId = group.replace("返点积分", "");
                        for (TradeDetailBean bean : customPay) {
                            if (outerId.equals(bean.getTaobaoId())) {
                                detail.setTaobaoId(bean.getTaobaoId());
                                continue;
                            }
                        }
                    }
                }
            }

            if (Strings.isEmpty(detail.getTaobaoId())) {
                detail.setCheckCode("F");
                detail.setCheckDesc("未匹配到对应的淘宝订单号。");
            }
        }
    }
}
