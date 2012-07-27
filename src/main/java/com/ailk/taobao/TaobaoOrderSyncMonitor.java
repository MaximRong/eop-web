package com.ailk.taobao;

import org.phw.core.lang.Collections;
import org.phw.eop.support.EopAction;
import org.phw.eop.support.EopActionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.taobao.thread.OrderSyncMonitorThread;

public class TaobaoOrderSyncMonitor extends EopAction {
    private static final Logger logger = LoggerFactory.getLogger(TaobaoOrderSyncMonitor.class);

    @Override
    public Object doAction() throws EopActionException {
        logger.info("淘宝订单同步监控Start");
        try {
            Thread monitorThread = new Thread(new OrderSyncMonitorThread());
            monitorThread.start();
        }
        catch (Exception e) {
            logger.error("淘宝订单同步监控Exception", e);
            return Collections.asMap("ResultCode", "Ex", "ResultMsg", "淘宝订单同步监控线程启动异常" + e.getMessage());
        }
        logger.info("淘宝订单同步监控End");
        return Collections.asMap("ResultCode", "OK", "ResultMsg", "淘宝订单同步监控线程启动成功");
    }
}
