package com.ailk.alipay;

import org.phw.core.lang.Collections;
import org.phw.eop.support.EopAction;
import org.phw.eop.support.EopActionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.alipay.thread.GetCustomTradeThread;
import com.ailk.alipay.thread.GetWithdrawThread;
import com.ailk.alipay.utils.AlipayConst;

public class AlipayAccessible extends EopAction {
    private static final Logger logger = LoggerFactory.getLogger(AlipayAccessible.class);

    @Override
    public Object doAction() throws EopActionException {
        // 交易类型
        String requestType = getStr("RequestType");
        logger.info("支付宝接入处理Start-{}", requestType);

        StringBuffer sb = new StringBuffer();
        try {
            if (AlipayConst.REQTYPE_WITHDRAW.equalsIgnoreCase(requestType)) {
                processWithdraw(sb);
            }
            else {
                processCustomTrade(sb);
            }
        }
        catch (Exception e) {
            logger.error("支付宝接入处理Exception-" + requestType, e);
            return Collections.asMap("ResultCode", "Ex", "ResultMsg", sb.append("!!支付宝接入处理异常-").append(requestType)
                    .append("!!").append(e.getMessage()).toString());
        }
        logger.info("支付宝接入处理End-{}", requestType);
        return Collections.asMap("ResultCode", "OK", "ResultMsg", sb.toString());
    }

    /**
     * 客户支付帐务明细处理。
     * @param sb
     */
    private void processCustomTrade(StringBuffer sb) {
        logger.info("启动获取支付宝（客户支付）帐务明细线程。");
        Thread customTradeThread = new Thread(new GetCustomTradeThread());
        customTradeThread.start();
        sb.append("^_^获取支付宝（客户支付）帐务明细线程启动成功");
    }

    /**
     * 支付宝提现处理。
     * @param sb
     */
    private void processWithdraw(StringBuffer sb) {
        logger.info("启动获取支付宝提现数据线程。");
        Thread withdrawThread = new Thread(new GetWithdrawThread());
        withdrawThread.start();
        sb.append("^_^获取支付宝提现数据线程启动成功");
    }

}
