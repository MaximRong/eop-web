package com.ailk.tenpay;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import org.apache.commons.lang.time.FastDateFormat;
import org.phw.core.lang.Collections;
import org.phw.eop.support.EopAction;
import org.phw.eop.support.EopActionException;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.ailk.tenpay.client.ClientResponseHandler;
import com.ailk.tenpay.client.RequestHandler;
import com.ailk.tenpay.client.TenpayHttpClient;
import com.ailk.tenpay.keys.TenpayConst;
import com.ailk.tenpay.util.XMLUtil;
import com.ailk.thirdservice.tenpay.TenpayService;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TenpayAccessible extends EopAction {
    private static final Logger logger = LoggerFactory.getLogger(TenpayAccessible.class);

    @Override
    public Object doAction() throws EopActionException {

        String beginTime = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").format(new Date());

        // 通信对象
        TenpayHttpClient httpClient = new TenpayHttpClient();
        // 请求对象
        RequestHandler reqHandler = new RequestHandler(null, null);
        // 应答对象
        ClientResponseHandler resHandler = new ClientResponseHandler();

        /** 设置请求参数 */
        reqHandler.setKey(TenpayConst.SAFE_KEY);
        reqHandler.setGateUrl(TenpayConst.GATE_URL);

        /** 设置接口参数 */
        reqHandler.setParameter(TenpayConst.INPUT_CHARSET, TenpayConst.CHAR_SET);
        reqHandler.setParameter(TenpayConst.PARTNER, TenpayConst.PARTNER_ID);
        reqHandler.setParameter(TenpayConst.OUT_TRADE_NO, getStr(TenpayConst.OUT_TRADE_NO));
        reqHandler.setParameter(TenpayConst.TRANSACTION_ID, getStr(TenpayConst.TRANSACTION_ID));
        reqHandler.setParameter(TenpayConst.OUT_REFUND_NO, getStr(TenpayConst.OUT_REFUND_NO));
        reqHandler.setParameter(TenpayConst.TOTAL_FEE, getStr(TenpayConst.TOTAL_FEE));
        reqHandler.setParameter(TenpayConst.REFUND_FEE, getStr(TenpayConst.REFUND_FEE));
        reqHandler.setParameter(TenpayConst.OP_USER_ID, TenpayConst.OP_USER_ID_VAL);
        reqHandler.setParameter(TenpayConst.OP_USER_PASSWD, TenpayConst.OP_USER_PWD);
        reqHandler.setParameter(TenpayConst.RECV_USER_ID, getStr(TenpayConst.RECV_USER_ID));
        reqHandler.setParameter(TenpayConst.RECCV_USER_NAME, getStr(TenpayConst.RECCV_USER_NAME));

        /** 设置通信参数 */
        // 设置请求返回的等待时间
        httpClient.setTimeOut(TenpayConst.TIME_OUT);
        InputStream caJksStream = null;
        InputStream certStream = null;

        try {
            caJksStream = new ClassPathResource(TenpayConst.JKS_FILE_PATH).getInputStream();
            certStream = new ClassPathResource(TenpayConst.CERT_FILE_PATH).getInputStream();

        } catch (Exception e) {
            logger.error("调用财付通退款接口异常:" + e.getMessage());
            return Collections.asMap(TenpayConst.EOP_CODE, "9999", TenpayConst.EOP_MSG, e.getMessage());
        }

        // 设置CA证书
        httpClient.setCaInfo(caJksStream);
        // 设置个人(商户)证书
        httpClient.setCertInfo(certStream, TenpayConst.PARTNER_ID);
        // 设置发送类型POST
        httpClient.setMethod(TenpayConst.METHOD);

        try {
            // 设置请求内容
            String requestUrl = reqHandler.getRequestURL();
            httpClient.setReqContent(requestUrl);
            String rescontent = "";
            String resultMsg = "";

            // 后台调用
            if (httpClient.execute()) {
                // 设置结果参数
                rescontent = httpClient.getResContent();
                resHandler.setContent(rescontent);
                resHandler.setKey(TenpayConst.SAFE_KEY);

                Map retMap = XMLUtil.doXMLParse(rescontent);

                // 获取返回参数
                String retcode = resHandler.getParameter("retcode");

                // 判断签名及结果
                if (!"0".equals(retcode)) {
                    resultMsg = resHandler.getParameter("retmsg");
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0001", TenpayConst.EOP_MSG, resultMsg));

                } else if (!resHandler.isTenpaySign()) {
                    resultMsg = "接口返回验证签名失败";
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0002", TenpayConst.EOP_MSG, resultMsg));

                } else if ("8,9,11".contains(resHandler.getParameter("refund_status"))) {
                    resultMsg = "退款处理中";
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0004", TenpayConst.EOP_MSG, resultMsg));

                } else if ("1,2".contains(resHandler.getParameter("refund_status"))) {
                    resultMsg = "退款出现异常，请使用原退款单重新发起";
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0005", TenpayConst.EOP_MSG, resultMsg));

                } else if ("7".equals(resHandler.getParameter("refund_status"))) {
                    resultMsg = "退款接收人账户异常，请联系账户管理员人工退款";
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0006", TenpayConst.EOP_MSG, resultMsg));

                } else if ("4,10".contains(resHandler.getParameter("refund_status"))) {
                    resultMsg = "退款成功";
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0000", TenpayConst.EOP_MSG, resultMsg));

                } else {
                    resultMsg = "退款失败，请联系维护人员处理";
                    retMap.putAll(Collections.asMap(TenpayConst.EOP_CODE, "0007", TenpayConst.EOP_MSG, resultMsg));
                }

                retMap.put("start_time", beginTime); // 开始时间
                retMap.put("end_time", FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").format(new Date())); // 结束时间
                retMap.put(TenpayConst.TOTAL_FEE, getStr(TenpayConst.TOTAL_FEE));
                new ServiceCall("eop").call2(TenpayService.RECORD_REFUND_LOG, retMap);

                return retMap;
            } else { // 有可能因为网络原因，请求已经处理，但未收到应答。

                resultMsg = "调用财付通退款接口无应答：状态码[" + httpClient.getResponseCode() + "]，错误信息[" + httpClient.getErrInfo()
                    + "]";

                Map paramMap = Collections.newHashMap();
                paramMap.put(TenpayConst.EOP_CODE, "0003");
                paramMap.put(TenpayConst.EOP_MSG, resultMsg.length() > 500 ? resultMsg.substring(0, 500) : resultMsg);
                paramMap.put("start_time", beginTime); // 开始时间
                paramMap.put("end_time", FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").format(new Date())); // 结束时间

                new ServiceCall("eop").call2(TenpayService.RECORD_REFUND_LOG, paramMap);

                return paramMap;
            }

        } catch (Exception e) {
            logger.error("调用财付通退款接口异常:" + e.getMessage());
            return Collections.asMap(TenpayConst.EOP_CODE, "9999", TenpayConst.EOP_MSG, e.getMessage());
        }
    }
}
