package com.ailk.taobao.thread;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.phw.core.lang.Dates;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.taobao.utils.TaobaoConfig;
import com.ailk.taobao.utils.TaobaoOrderUtils;
import com.ailk.taobao.utils.TaobaoUtils;
import com.ailk.thirdservice.taobao.TaobaoService;
import com.ailk.thirdservice.taobao.log.LogBean;
import com.ailk.thirdservice.taobao.utils.TaobaoConst;
import com.linkage.util.exception.DAOException;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.domain.Order;
import com.taobao.api.domain.Trade;
import com.taobao.api.domain.WtExtResult;
import com.taobao.api.request.TradesSoldIncrementGetRequest;
import com.taobao.api.response.TradesSoldIncrementGetResponse;

/**
 * 增量获取淘宝订单线程类。
 *
 * @author wanglei
 *
 * 2012-5-23
 */
public class IncGetOrderThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(IncGetOrderThread.class);

    @Override
    public void run() {
        logger.info("增量获取淘宝订单线程Start");
        doGetOrder(true);
        logger.info("增量获取淘宝订单线程End");
    }

    /**
     * 执行订单获取。
     */
    private void doGetOrder(boolean first) {
        logger.info("IncGetOrder.doGetOrder#{}#Begin", first);

        java.util.Date endCreated = new java.util.Date();
        long time = endCreated.getTime();
        time -= TaobaoConfig.ORDER_INTERVAL;
        java.util.Date startCreated = new java.util.Date(time);

        // 测试时间
        //        java.util.Date endCreated = null;
        //        java.util.Date startCreated = null;
        //        try {
        //            startCreated = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse("2012/05/02 00:50:21");
        //            endCreated = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse("2012/05/02 01:00:21");
        //        } catch (Exception e) {
        //            e.printStackTrace();
        //        }

        // 搜索历史订单
        /*        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                        TaobaoConfig.APP_SECRET);// 实例化TopClient类
                TradesSoldGetRequest req = new TradesSoldGetRequest(); // 获取订单信息 http://api.taobao.com/apidoc/api.htm?path=cid:39-apiId:347
                req.setFields("tid, status");
                req.setStartCreated(startCreated);
                req.setEndCreated(endCreated);
                req.setStatus("WAIT_SELLER_SEND_GOODS"); 2012年03月29日14：03：28 添加获取订单状态
                TradesSoldGetResponse rsp;*/

        //搜索已支付订单
        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                TaobaoConfig.APP_SECRET);// 实例化TopClient类
        TradesSoldIncrementGetRequest req = new TradesSoldIncrementGetRequest(); // 获取订单信息 http://api.taobao.com/apidoc/api.htm?path=cid:39-apiId:347
        req.setFields("tid, status");
        req.setStartModified(startCreated);
        req.setEndModified(endCreated);
        // TRADE_CLOSED  WAIT_SELLER_SEND_GOODS  WAIT_BUYER_CONFIRM_GOODS
        req.setStatus("WAIT_SELLER_SEND_GOODS"); /*2012年03月29日14：03：28 添加获取订单状态*/
        TradesSoldIncrementGetResponse rsp;

        long page_num = 1L;
        logger.info("IncGetOrder.doGetOrder#{}#param#startCreated=" + startCreated + "#endCreated=" + endCreated, first);
        try {
            while (true) {
                req.setPageNo(page_num);
                rsp = client.execute(req, TaobaoConfig.APP_SESSION);
                /*2012年06月08日  应先判断接口级是否成功,先打个补丁*/
                if (rsp.getTotalResults() == null) {
                    rsp.setTotalResults(0l);
                }
                boolean dealRsp = dealRsp(rsp.getTrades(), rsp.getTotalResults(),
                        Dates.format(startCreated, TaobaoConst.DATE_FORMAT),
                        Dates.format(endCreated, TaobaoConst.DATE_FORMAT), page_num);
                if (!dealRsp || rsp.getTotalResults() / TaobaoConfig.PAGE - page_num + 1 == 0) {
                    return;
                }
                page_num++;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            if (first) {
                logger.info("IncGetOrder.doGetOrder#异常，再次调用");
                doGetOrder(false);
            }
        }
    }

    /**
     * 调用2次订单获取  A.基础订单信息   B.扩展订单信息
     * @param rsp
     * @param begin
     * @param end
     * @param logFlag
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private boolean dealRsp(List<Trade> trades, Long totalResults, String begin, String end, long page_num) {
        logger.info("一共获取到订单数：" + totalResults);
        Long succCount = 0L;
        Long failCount = 0L;
        Long tid = 0L;
        ArrayList al = new ArrayList();
        LogBean ld = null;
        String res = null;
        try {
            trades = trades == null ? new ArrayList<Trade>() : trades;
            for (Trade nt : trades) {
                tid = nt.getTid();
                ld = new LogBean();
                ld.setTid("" + tid);
                ld.setOrder_status(nt.getStatus());
                if ("WAIT_SELLER_SEND_GOODS".equals(nt.getStatus()) || "TRADE_CLOSED".equals(nt.getStatus())) {
                    try { // 是否订单获取是否有异常，记录日志
                        Trade baseOrderInfo = TaobaoOrderUtils.getBaseOrderInfo(tid); // 保存订单基础信息
                        // 判断是否是网厅的订单，不是就走其他流程，跳过下面垂直接口信息
                        /*2012年04月26日10：11：14 上网卡上线推迟 begin*/
                        if (!TaobaoOrderUtils.isWTOrder(baseOrderInfo)) {
                            try { // 判断是否是网厅订单中的异常，记录日志
                                dealNotWTOrder(baseOrderInfo);
                                ld.setParam(TaobaoConst.GETORDER, TaobaoConst.SPEC);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                ld.setParam(TaobaoConst.GETORDER, TaobaoConst.SPECFAIL + ":" + e.getMessage());
                            }
                            al.add(ld);
                            continue;
                        }
                        /*2012年04月26日10：11：14 上网卡上线推迟 end*/
                        WtExtResult extOrderInfo = TaobaoOrderUtils.getExtOrderInfo(tid); // 保存订单基础信息
                        /*2012年06月08日 9：43：00 添加非网厅订单判断和入库*/
                        if (extOrderInfo != null && extOrderInfo.getTid() == TaobaoConst.SPEC_RANDOM) {
                            res = TaobaoConst.ORDERNOTWT;
                        }
                        else {
                            res = deal(baseOrderInfo, extOrderInfo);
                        }
                        ld.setParam(TaobaoConst.GETORDER, res);
                        if (TaobaoConst.PROCSUCC.equals(res) || TaobaoConst.PROCSPECSUCC.equals(res)) {
                            succCount++;
                        }
                        else if (TaobaoConst.PROCFAIL.equals(res) || TaobaoConst.PROCSPECFAIL.equals(res)
                                || TaobaoConst.ORDERCHGFAIL.equals(res)) {
                            failCount++;
                        }
                    }
                    catch (Exception e) {
                        ld.setParam(TaobaoConst.GETORDER, TaobaoConst.SQLFAIL);
                    }
                }
                else {
                    ld.setParam(TaobaoConst.GETORDER, TaobaoConst.ORDERINVALIDSTATUE);
                }
                al.add(ld);
            }
            new ServiceCall("eop").call2(TaobaoService.TAOBAO_SRV_PATH + "log", TaobaoConst.GETORDER, begin, end,
                    TaobaoConst.ISUCC, totalResults, succCount, failCount, al, page_num);
            return true;
        }
        catch (Exception e) {
            logger.info("error:" + e.getMessage());
            new ServiceCall("eop").call2(TaobaoService.TAOBAO_SRV_PATH + "log", TaobaoConst.GETORDER, begin, end,
                    TaobaoConst.IFAIL, totalResults, succCount, failCount, al, page_num);
            return false;
        }
    }

    /**
     * 将获取的参数组装调用存储过程<br/>
     * @param map1
     * @param map2
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws DAOException
     */
    private String deal(Trade trade, WtExtResult wr) throws SQLException, ClassNotFoundException {
        // Trade trade = (Trade) map1.get("trade");
        /* Trade信息-基础信息 */
        Long tid = trade.getTid();
        logger.info("IncGetOrder.deal#param#tid=" + tid);
        Long num_iid = trade.getNumIid();
        Long num = trade.getNum();
        String price = trade.getPrice();
        String payment = trade.getPayment();
        String total_fee = trade.getTotalFee();
        String commission_fee = trade.getCommissionFee();
        //        String trade_status = trade.getStatus(); // 等待卖家发货,即:买家已付款
        String trade_status = "WAIT_SELLER_SEND_GOODS"; /*2012年03月22日20：40：27 修改订单状态*/
        String trade_create_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(trade.getCreated());
        String pay_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(trade.getPayTime());
        String buyer_message = trade.getBuyerMessage();
        String buyer_area = trade.getBuyerArea();
        String buyer_nick = trade.getBuyerNick();
        String alipay_no = trade.getAlipayNo();
        String receiver_name = trade.getReceiverName();
        String receiver_province = trade.getReceiverState();
        String receiver_city = TaobaoUtils.uniNext(receiver_province, trade.getReceiverCity());
        String receiver_district = trade.getReceiverDistrict();
        String receiver_address = trade.getReceiverAddress();
        String receiver_post = trade.getReceiverZip();
        String receiver_mobile = trade.getReceiverMobile();
        String receiver_phone = trade.getReceiverPhone();
        List<Order> orders = trade.getOrders();
        String phonecode = null;
        String outer_iid = null;
        Long refund_id = null;
        if (orders != null && orders.size() > 0) {
            phonecode = trade.getOrders().get(0).getOuterSkuId();
            outer_iid = trade.getOrders().get(0).getOuterIid();
            refund_id = trade.getOrders().get(0).getRefundId();
        }
        String invoice_name = trade.getInvoiceName();
        invoice_name = invoice_name == null ? "" : invoice_name;

        /* 扩展信息 */
        String phone_num = wr.getPhoneNum();
        String province_code = wr.getPhoneProvinceCode();
        String city_code = wr.getPhoneCityCode();
        city_code = TaobaoUtils.uni(province_code, city_code); /*现阶段使用，用于转换地市编码与省份编码相同问题*/
        String plan_title = wr.getPlanTitle();
        //        String out_plan_id = "9000035455A100033"; // 测试使用
        //        String out_plan_id = "9000035455A9000318554"; // 测试使用
        String out_plan_id = wr.getOutPlanId();
        //        String out_package_id = "2"; // 测试使用
        String out_package_id = TaobaoUtils.fillNum(wr.getOutPackageId());
        String effect_rule = TaobaoUtils.fillNum(wr.getEffectRule() + ""); // 付费类型：首页资费收取
        effect_rule = TaobaoUtils.exRule(effect_rule);
        Long agreement_id = wr.getAgreementId();
        String phone_owner_name = wr.getPhoneOwnerName();
        String cert_type = TaobaoUtils.fillNum(wr.getCertType() + "");
        String cert_card_num = wr.getCertCardNum();
        Long phone_deposit = wr.getPhoneDeposit();
        Long phone_free_deposit = wr.getPhoneFreeDeposit();

        // 调用存储过程参数准备
        Map params = new HashMap();
        params.put("tid", tid.toString()); // 淘宝订单号
        params.put("num_iid", num_iid.toString()); // 商品数字编号
        params.put("num", num.toString()); // 商品数量
        params.put("price", price.toString()); // 商品价格
        params.put("payment", payment.toString()); // 实付价格
        params.put("total_fee", total_fee.toString()); // 商品金额
        params.put("commission_fee", commission_fee.toString()); // 交易佣金
        params.put("trade_status", trade_status); // 交易状态
        params.put("trade_create_time", trade_create_time); // 交易创建时间
        params.put("pay_time", pay_time); // 交易支付时间
        params.put("buyer_message", buyer_message); // 买家留言
        params.put("buyer_area", buyer_area); // 买家下单地区
        params.put("buyer_nick", buyer_nick); // 买家昵称
        params.put("alipay_no", alipay_no); // 支付宝订单号
        params.put("receiver_name", receiver_name); // 收货人姓名
        params.put("receiver_province", receiver_province); // 收货人所在省份
        params.put("receiver_city", receiver_city); // 收货人所在地市
        params.put("receiver_district", receiver_district); // 收货人所在地区
        params.put("receiver_address", receiver_address); // 收货人详细地址
        params.put("receiver_post", receiver_post); // 收货人邮编
        params.put("receiver_mobile", receiver_mobile); // 收货人手机号码
        params.put("receiver_phone", receiver_phone); // 收货人电话号码
        params.put("phonecode", phonecode); // 优惠购机手机编码
        params.put("outer_iid", outer_iid); // 商品商户编码
        params.put("invoice_name", invoice_name); // 发票抬头
        params.put("refund_id", refund_id + ""); // 退款ID

        params.put("phone_num", phone_num); // 号码
        params.put("province_code", province_code); // 手机号码所在省份的区位码
        params.put("city_code", city_code); // 手机号码所在城市的区位码
        params.put("plan_title", plan_title); // 套餐名称
        params.put("out_plan_id", out_plan_id); // 套餐商家编码
        params.put("out_package_id", out_package_id); // 合约计划商家编码
        params.put("effect_rule", effect_rule); // 套餐开通规则
        params.put("agreement_id", agreement_id + ""); // 协议商家编码
        params.put("phone_owner_name", phone_owner_name); // 机主姓名
        params.put("cert_type", cert_type); // 证件类型
        params.put("cert_card_num", cert_card_num); // 证件号
        params.put("phone_deposit", phone_deposit + ""); // 号码预存款（单位分）
        params.put("phone_free_deposit", phone_free_deposit + ""); // 号码预存款（单位分）

        // 执行入库
        String returnCode = (String) new ServiceCall("eop")
                .call2(TaobaoService.TAOBAO_SRV_PATH + "recordOrder", params);
        logger.info("status={}", returnCode);

        /*String orderID = (String) params.get("p_orderId");
        String provinceID = (String) params.get("p_provinceId");
        String cityID = (String) params.get("p_cityId");*/

        // 非测试 且 淘宝订单入库成功后发送短信
        if (TaobaoConfig.SEND_MSG && TaobaoConst.PROCSUCC.equals(returnCode)) {
            logger.warn("短信发送开关打开，淘宝订单入库成功，发送短信");
        }

        return returnCode;
    }

    /**
     * 处理不是网厅的订单
     * @param baseOrderInfo
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private void dealNotWTOrder(Trade baseOrderInfo) throws Exception {
        List<Order> orders = baseOrderInfo.getOrders();
        int count_num = orders.size();
        for (int i = 0; i < count_num; i++) {
            Order order = orders.get(i);
            Long cid = order.getCid();
            if (TaobaoConst.CID_SWK.equals(cid + "")) {
                dealSWKOrder(baseOrderInfo, order, count_num);
            }
            // 添加类目2
        }
    }

    /**
     * 处理上网卡订单
     * @param trade
     * @param order
     * @param count_sum 子订单序号
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private String dealSWKOrder(Trade trade, Order order, int count_num) throws Exception {

        // Trade trade = (Trade) map1.get("trade");
        /* Trade信息-基础信息 */
        Long tid = trade.getTid();
        Long num_iid = trade.getNumIid() == null ? 0L : trade.getNumIid();
        Long num = trade.getNum() == null ? 0L : trade.getNum();
        String price = trade.getPrice() == null ? "0" : trade.getPrice();
        String payment = trade.getPayment();
        String total_fee = trade.getTotalFee();
        String commission_fee = trade.getCommissionFee();
        //        String trade_status = trade.getStatus(); // 等待卖家发货,即:买家已付款
        String trade_status = "WAIT_SELLER_SEND_GOODS"; /*2012年03月22日20：40：27 修改订单状态*/
        String trade_create_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(trade.getCreated());
        String pay_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(trade.getPayTime());
        String buyer_message = trade.getBuyerMessage();
        String buyer_area = trade.getBuyerArea();
        String buyer_nick = trade.getBuyerNick();
        String alipay_no = trade.getAlipayNo();
        String receiver_name = trade.getReceiverName();
        String receiver_province = trade.getReceiverState();
        String receiver_city = TaobaoUtils.uniNext(receiver_province, trade.getReceiverCity());
        String receiver_district = trade.getReceiverDistrict();
        String receiver_address = trade.getReceiverAddress();
        String receiver_post = trade.getReceiverZip();
        String receiver_mobile = trade.getReceiverMobile();
        String receiver_phone = trade.getReceiverPhone();

        /*2012年04月23日10：47：48 修改自订单信息 begin*/
        //        List<Order> orders = trade.getOrders();
        //        String phonecode = null;
        //        String outer_iid = null;
        //        Long refund_id = null;
        //        if (orders != null && orders.size() > 0) {
        //            phonecode = trade.getOrders().get(0).getOuterSkuId();
        //            outer_iid = trade.getOrders().get(0).getOuterIid();
        //            refund_id = trade.getOrders().get(0).getRefundId();
        //        }
        String phonecode = order.getOuterSkuId();
        String outer_iid = order.getOuterIid();
        Long refund_id = order.getRefundId();
        Long oid = order.getOid();
        Long cid = order.getCid();
        String child_single_price = order.getPrice();
        String child_all_price = order.getPayment();
        Long child_num = order.getNum();

        /*2012年04月23日10：47：48 修改自订单信息 end*/

        String invoice_name = trade.getInvoiceName();
        invoice_name = invoice_name == null ? "" : invoice_name;

        // 调用存储过程参数准备
        Map params = new HashMap();
        params.put("tid", tid.toString()); // 淘宝订单号
        params.put("num_iid", num_iid + ""); // 商品数字编号
        params.put("num", num.toString()); // 商品数量
        params.put("price", price.toString()); // 商品价格
        params.put("payment", payment.toString()); // 实付价格
        params.put("total_fee", total_fee.toString()); // 商品金额
        params.put("commission_fee", commission_fee.toString()); // 交易佣金
        params.put("trade_status", trade_status); // 交易状态
        params.put("trade_create_time", trade_create_time); // 交易创建时间
        params.put("pay_time", pay_time); // 交易支付时间
        params.put("buyer_message", buyer_message); // 买家留言
        params.put("buyer_area", buyer_area); // 买家下单地区
        params.put("buyer_nick", buyer_nick); // 买家昵称
        params.put("alipay_no", alipay_no); // 支付宝订单号
        params.put("receiver_name", receiver_name); // 收货人姓名
        params.put("receiver_province", receiver_province); // 收货人所在省份
        params.put("receiver_city", receiver_city); // 收货人所在地市
        params.put("receiver_district", receiver_district); // 收货人所在地区
        params.put("receiver_address", receiver_address); // 收货人详细地址
        params.put("receiver_post", receiver_post); // 收货人邮编
        params.put("receiver_mobile", receiver_mobile); // 收货人手机号码
        params.put("receiver_phone", receiver_phone); // 收货人电话号码
        params.put("phonecode", phonecode); // 优惠购机手机编码
        params.put("outer_iid", outer_iid); // 商品商户编码
        params.put("invoice_name", invoice_name); // 发票抬头
        params.put("refund_id", refund_id + ""); // 退款ID
        params.put("oid", oid + ""); // 子订单订单号
        params.put("cid", cid + ""); // 商品类目号
        params.put("child_single_price", child_single_price + ""); // 子订单商品单价
        params.put("child_all_price", child_all_price + ""); // 子订单总价
        params.put("child_num", child_num + ""); // 子订单商品数量
        params.put("count_num", count_num + ""); // 子订单页码

        // 执行入库
        String returnCode = (String) new ServiceCall("eop")
                .call2(TaobaoService.TAOBAO_SRV_PATH + "recordSwkOrder", params);

        /*String orderID = (String) params.get("p_orderId");
        String provinceID = (String) params.get("p_provinceId");
        String cityID = (String) params.get("p_cityId");*/

        // 非测试 且 淘宝订单入库成功后发送短信
        if (TaobaoConfig.SEND_MSG && TaobaoConst.PROCSUCC.equals(returnCode)) {
            logger.warn("短信发送开关打开，淘宝订单入库成功，发送短信");
        }

        return returnCode;
    }
}
