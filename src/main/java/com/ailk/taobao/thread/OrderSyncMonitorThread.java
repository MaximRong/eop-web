package com.ailk.taobao.thread;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.phw.core.lang.Collections;
import org.phw.core.lang.Dates;
import org.phw.core.lang.Pair;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.taobao.utils.TaobaoConfig;
import com.ailk.thirdservice.taobao.TaobaoService;
import com.ailk.thirdservice.taobao.utils.TaobaoConst;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.domain.Order;
import com.taobao.api.domain.Trade;
import com.taobao.api.request.TradeWtverticalGetRequest;
import com.taobao.api.request.TradesSoldGetRequest;
import com.taobao.api.response.TradeWtverticalGetResponse;
import com.taobao.api.response.TradesSoldGetResponse;

public class OrderSyncMonitorThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(OrderSyncMonitorThread.class);

    @Override
    public void run() {
        // 获取淘宝隔日订单列表
        Map tidMap = getTaobaoTids();
        // 解析处理淘宝订单列表
        processTaobaoTids(tidMap);
    }

    private Map getTaobaoTids() {
        List tidList = new ArrayList();
        long invalidRecord = 0l;
        String logId = null;
        Pair<Date, Date> datePair = createStartEndTime();
        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                TaobaoConfig.APP_SECRET);
        TradesSoldGetRequest req = new TradesSoldGetRequest();
        req.setFields("tid, orders.oid");
        req.setStartCreated(datePair.getFirst());
        req.setEndCreated(datePair.getSecond());
        req.setStatus("WAIT_SELLER_SEND_GOODS");
        TradesSoldGetResponse rsp;
        long page_num = 1L;
        try {
            while (true) {
                req.setPageNo(page_num);
                rsp = client.execute(req, TaobaoConfig.APP_SESSION);
                List<Trade> trades = rsp.getTrades();
                for (Trade trade : trades) {
                    Long tid = trade.getTid();
                    List<Order> orders = trade.getOrders();
                    if (orders == null) {
                        continue;
                    }
                    TradeWtverticalGetRequest req2 = new TradeWtverticalGetRequest();
                    req2.setTids(tid + "");
                    TradeWtverticalGetResponse rsp2 = client.execute(req2, TaobaoConfig.APP_SESSION);
                    // 非网厅订单时
                    if ("15".equals(rsp2.getErrorCode())) {
                        if (!TaobaoConst.CID_SWK.equals(orders.get(0).getCid() + "")) {
                            invalidRecord++;
                            continue;
                        }
                    }
                    tidList.add(Long.toString(trade.getTid()));
                }
                if (rsp.getTotalResults() == 0 || rsp.getTotalResults() / TaobaoConfig.PAGE - page_num + 1 == 0) {
                    break;
                }
                page_num++;
            }
            // 记录日志
            logId = (String) new ServiceCall("eop").call2(TaobaoService.LOG, TaobaoConst.MONITORORDER,
                    Dates.format(datePair.getFirst(), TaobaoConst.DATE_FORMAT),
                    Dates.format(datePair.getSecond(), TaobaoConst.DATE_FORMAT), TaobaoConst.ISUCC,
                    rsp.getTotalResults(), rsp.getTotalResults() - invalidRecord, 0l, new ArrayList(), page_num);
        }
        catch (Exception e) {
            logger.error("淘宝订单同步监控-获取淘宝隔日订单异常", e);
            tidList.clear();
            invalidRecord = 0l;
            logId = (String) new ServiceCall("eop").call2(TaobaoService.LOG, TaobaoConst.MONITORORDER,
                    Dates.format(datePair.getFirst(), TaobaoConst.DATE_FORMAT),
                    Dates.format(datePair.getSecond(), TaobaoConst.DATE_FORMAT), TaobaoConst.IFAIL, 0l, 0l, 0l,
                    new ArrayList(), page_num);
        }

        return Collections.asMap("tidList", tidList, "invalidRecord", invalidRecord, "logId", logId);
    }

    private void processTaobaoTids(Map tidMap) {
        List tidList = (List) tidMap.get("tidList");
        if (Collections.isEmpty(tidList)) {
            logger.info("淘宝订单同步监控-获取淘宝隔日订单数0");
            return;
        }

        Map inMap = Collections.asMap("logid", tidMap.get("logId"));
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        String batchId = Dates.format(calendar.getTime(), "yyyyMMdd");
        inMap.put("batch_id", batchId);

        new ServiceCall("eop").call2(TaobaoService.RECORD_SYNCEX_ORDER, tidList, inMap);
    }

    /**
     * 生成起止时间。
     * @return
     */
    private Pair<Date, Date> createStartEndTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date endDate = calendar.getTime();

        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date beginDate = calendar.getTime();
        return new Pair<Date, Date>(beginDate, endDate);
    }

}
