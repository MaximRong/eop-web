package com.ailk.alipay.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.phw.core.lang.Collections;
import org.phw.core.lang.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecs.esf.base.utils.StrUtils;
import com.ailk.ecs.esf.service.eface.engine.XmlReaderEngine;
import com.ailk.thirdservice.alipay.TradeDetailBean;

/**
 * 支付宝调用客户端。
 *
 * @author wanglei
 *
 * 2012-6-5
 */
public class AlipayClient {
    private static final Logger logger = LoggerFactory.getLogger(AlipayClient.class);
    // 帐务明细响应报文模板
    private static final String TRADE_ACCOUNT_REPORT_TEMPLATE = "com/ailk/alipay/TradeAcountReport_Rsp.xml";
    // 帐务明细CSV正则表达式
    private static final Pattern CSV_PATTERN = Pattern.compile(",");

    /**
     * 帐务明细查询接口。
     * @param params
     * @return
     * @throws Exception
     */
    public static Map exportTradeAccountReport(Map<String, String> params) throws Exception {
        params.put("service", AlipayConst.FACE_ACCOUNT_TRADE_REPORT);
        params.put("partner", AlipayConst.PARTNER_ID);
        params.put("_input_charset", AlipayConst.CHAR_SET);
        params.put("sign_type", AlipayConst.SIGN_TYPE);
        params.put("user_id", AlipayConst.USER_ID);
        params.put("no_coupon", AlipayConst.NO_COUPON);
        String rspXml = AlipaySubmit.sendPostInfo(params, AlipayConst.ALIPAY_URL);
        return parseTradeAccountReportRsp(rspXml);
    }

    /**
     * 解析明细查询接口响应报文。
     * @param rspXml
     * @return
     */
    private static Map parseTradeAccountReportRsp(String rspXml) {
        Map result = Collections.asMap("RspXml", rspXml, "CheckCode", "T", "CheckDesc", "OK");
        // xml to map
        try {
            XmlReaderEngine reader = new XmlReaderEngine();
            reader.parseTemplateFile(TRADE_ACCOUNT_REPORT_TEMPLATE);
            result.putAll((Map) reader.createMap(rspXml));
        }
        catch (Exception e) {
            logger.error("解析帐务明细响应报文异常", e);
            result.put("is_success", "F"); // 数据库必填字段
            result.put("CheckCode", "F");
            result.put("CheckDesc", StrUtils.substrb("解析帐务明细响应报文异常:" + e.getMessage(), 256));
            return result;
        }

        // 接口调用结果解析
        if ("F".equals(result.get("is_success"))) {
            result.put("CheckCode", "F");
            result.put("CheckDesc", "支付宝返回失败,is_success=F,error=" + result.get("error"));
            return result;
        }

        // 接口返回数据校验
        String csvData = ((String) result.get("csv_data")).replace("<![CDATA[", "").replace("]]>", "");
        int count = 0;
        Matcher matcher = CSV_PATTERN.matcher(csvData);
        while (matcher.find()) {
            count++;
        }
        if (count % 14 != 0) {
            result.put("CheckCode", "F");
            result.put("CheckDesc", "csv_data格式异常");
            return result;
        }
        List csvList = new ArrayList();
        List tempList = Arrays.asList(csvData.trim().split(","));
        csvList.addAll(tempList);
        for (int i = tempList.size(); i < count; i++) {
            csvList.add("");
        }

        // 解析csv_data
        result.put("CsvList", parseTradeAccountReportCsv(csvList));
        return result;
    }

    /**
     * 解析帐务明细CSV数据。
     * @param csvData
     * @return
     */
    private static List parseTradeAccountReportCsv(List<String> csvList) {
        List retList = new ArrayList();
        for (int j = 14; j < csvList.size(); j = j + 14) {
            TradeDetailBean detail = new TradeDetailBean();
            int i = j;
            detail.setOuterId(csvList.get(i).trim());
            detail.setBalance(csvList.get(++i).trim());
            detail.setTradeTime(csvList.get(++i).trim().replace("年", "-").replace("月", "-").replace("日", ""));
            detail.setAccountSeq(csvList.get(++i).trim());
            detail.setTradeId(csvList.get(++i).trim());
            detail.setOpposeEmail(csvList.get(++i).trim());
            detail.setOpposeCode(csvList.get(++i).trim());
            detail.setUserId(csvList.get(++i).trim());

            String income = csvList.get(++i).trim();
            detail.setIncome(Strings.isEmpty(income) ? "0" : income);
            String payout = csvList.get(++i).trim();
            detail.setPayout(Strings.isEmpty(payout) ? "0" : payout);

            detail.setTradeChannel(StrUtils.substrb(csvList.get(++i).trim(), 30));
            detail.setGoodsName(csvList.get(++i).trim());
            detail.setTradeType(csvList.get(++i).trim());
            detail.setRemark(csvList.get(++i).trim());
            detail.setCheckCode("T");
            detail.setCheckDesc("OK");
            // 交易付款
            if (detail.getTradeType().contains(AlipayConst.TRADETYPE_NAME_CUSTOMPAY)) {
                detail.setTradeType(AlipayConst.TRADETYPE_CUSTOMPAY);
                detail.setTaobaoId(detail.getOuterId().replace("T200P", "")); // 淘宝订单号
            }
            // 收费
            else if (detail.getTradeType().contains(AlipayConst.TRADETYPE_NAME_CREDITCARD)) {
                detail.setTradeType(AlipayConst.TRADETYPE_CREDITCARD);
            }
            // 转账
            else if (detail.getTradeType().contains(AlipayConst.TRADETYPE_NAME_TRANSFER)) {
                if (detail.getRemark().contains(AlipayConst.TRADETYPE_NAME_COMMISION)) {
                    detail.setTradeType(AlipayConst.TRADETYPE_COMMISION); // 佣金
                }
                else if (detail.getRemark().contains(AlipayConst.TRADETYPE_NAME_INTEGRAL)) {
                    detail.setTradeType(AlipayConst.TRADETYPE_INTEGRAL); // 积分
                }
                else {
                    detail.setCheckCode("F");
                    detail.setCheckDesc("帐务明细-转账-费用类型不在佣金、积分范围内");
                }
            }
            // 提现
            else if (detail.getTradeType().contains(AlipayConst.TRADETYPE_NAME_WITHDRAW)) {
                detail.setTradeType(AlipayConst.TRADETYPE_WITHDRAW);
            }
            // 异常
            else {
                detail.setCheckCode("F");
                detail.setCheckDesc("帐务明细-费用类型异常：" + detail.getTradeType());
                detail.setTradeType(StrUtils.substrb(detail.getTradeType(), 8));
            }
            retList.add(detail);
        }
        return retList;
    }
}
