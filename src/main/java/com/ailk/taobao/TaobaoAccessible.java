package com.ailk.taobao;

import org.phw.core.lang.Collections;
import org.phw.eop.support.EopAction;
import org.phw.eop.support.EopActionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.taobao.thread.IncGetOrderThread;
import com.ailk.taobao.thread.IncGetRefundThread;

/**
 * 淘宝接入统一访问入口。
 *
 * @author wanglei
 *
 * 2012-5-23
 */
public class TaobaoAccessible extends EopAction {
    private static final Logger logger = LoggerFactory.getLogger(TaobaoAccessible.class);

    @Override
    public Object doAction() throws EopActionException {
        logger.info("淘宝接入处理Start");
        StringBuffer sb = new StringBuffer();
        try {
            processOrder(sb);
            processRefund(sb);
        }
        catch (Exception e) {
            logger.error("淘宝接入处理Exception", e);
            return Collections.asMap("ResultCode", "Ex", "ResultMsg", sb.append("!!淘宝接入处理异常!!").append(e.getMessage())
                    .toString());
        }
        logger.info("淘宝接入处理End");
        return Collections.asMap("ResultCode", "OK", "ResultMsg", sb.toString());
    }

    /**
     * 增量订单获取。
     * @param sb
     */
    private void processOrder(StringBuffer sb) {
        logger.info("启动增量获取淘宝订单线程。");
        Thread orderThread = new Thread(new IncGetOrderThread());
        orderThread.start();
        sb.append("^_^增量获取淘宝订单线程启动成功");
    }

    /**
     * 增量退款获取。
     * @param sb
     */
    private void processRefund(StringBuffer sb) {
        logger.info("启动增量获取淘宝退款线程。");
        Thread refundThread = new Thread(new IncGetRefundThread());
        refundThread.start();
        sb.append("^_^增量获取淘宝退款线程启动成功");
    }

}
