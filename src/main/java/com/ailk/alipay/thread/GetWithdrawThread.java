package com.ailk.alipay.thread;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.phw.core.lang.Dates;
import org.phw.core.lang.Pair;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.alipay.utils.AlipayClient;
import com.ailk.alipay.utils.AlipayConst;
import com.ailk.thirdservice.alipay.AlipayService;

/**
 * 支付宝提现数据获取。
 *
 * @author wanglei
 *
 * 2012-6-4
 */
public class GetWithdrawThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GetWithdrawThread.class);

    @Override
    public void run() {
        logger.info("获取支付宝（提现）帐务明细线程Start");
        Pair datePair = createStartEndTime();
        Map params = new HashMap<String, String>();
        params.put("gmt_create_start", datePair.getFirst());
        params.put("gmt_create_end", datePair.getSecond());
        params.put("trans_code", AlipayConst.TRANSCODE_WITHDRAW);
        try {
            Map response = AlipayClient.exportTradeAccountReport(params);
            response.put("log_type", AlipayConst.LOG_TYPE_WITHDRAW); // 提现
            response.put("start_time", datePair.getFirst()); // 开始时间
            response.put("end_time", datePair.getSecond()); // 结束时间
            new ServiceCall("eop").call2(AlipayService.ALIPAY_SRV_PATH + "recordTradeReport", response);
        }
        catch (Exception e) {
            logger.error("获取支付宝（提现）帐务明细线程Exception", e);
            throw new RuntimeException(e);
        }
        logger.info("获取支付宝（提现）帐务明细线程End");
    }

    /**
     * 生成起止时间。
     * @return
     */
    private Pair createStartEndTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        String endDate = Dates.format(calendar.getTime());

        calendar.add(Calendar.DAY_OF_MONTH, -1);
        String beginDate = Dates.format(calendar.getTime());
        return new Pair<String, String>(beginDate, endDate);
        //return new Pair<String, String>("2012-05-08 00:00:00", "2012-05-09 00:00:00");
    }

}
