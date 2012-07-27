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
import com.taobao.api.domain.Refund;
import com.taobao.api.domain.Trade;
import com.taobao.api.domain.WtExtResult;
import com.taobao.api.request.RefundsReceiveGetRequest;
import com.taobao.api.response.RefundsReceiveGetResponse;

/**
 * 增量获取淘宝退款线程类。
 *
 * @author wanglei
 *
 * 2012-5-23
 */
public class IncGetRefundThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(IncGetRefundThread.class);

    @Override
    public void run() {
        logger.info("增量获取淘宝退款线程Start");
        doGetRefund();
        logger.info("增量获取淘宝退款线程End");
    }

    private void doGetRefund() {
        logger.info("IncGetRefund.doGetRefund#Begin");
        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                TaobaoConfig.APP_SECRET);// 实例化TopClient类
        RefundsReceiveGetRequest req = new RefundsReceiveGetRequest();// 实例化具体API对应的Request类
        req.setFields("refund_id, tid, title, buyer_nick, seller_nick, total_fee, status, created, refund_fee, oid, " +
                /*2012年04月23日16：33：20 添加部分信息 begin*/
                "price, num, " +
                /*2012年04月23日16：33：20 添加部分信息 end*/
                "good_status, company_name, sid, payment, reason, desc, has_good_return, modified, order_status");
        req.setStatus("SUCCESS");

        java.util.Date endCreated = new java.util.Date();
        long time = endCreated.getTime();
        time -= TaobaoConfig.REFUND_INTERVAL;
        java.util.Date startCreated = new java.util.Date(time);

        /*java.util.Date endCreated = null;
        java.util.Date startCreated = null;
        try {
            startCreated = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").parse("2012/4/24 14:20:00");
            endCreated = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").parse("2012/4/24 14:25:00");
        }
        catch (Exception e) {
            e.printStackTrace();
        }*/

        req.setStartModified(startCreated);
        req.setEndModified(endCreated);

        RefundsReceiveGetResponse rsp;
        Long totalResults = 0L;
        Long succCount = 0L;
        Long failCount = 0L;
        LogBean ld = null;
        ArrayList al = new ArrayList();
        long page_num = 1L;
        logger.info("IncGetRefund.doGetRefund#param#endCreated=" + endCreated + "#endCreated=" + endCreated);

        while (true) {
            try {
                req.setPageNo(page_num);
                rsp = client.execute(req, TaobaoConfig.APP_SESSION);
                totalResults = rsp.getTotalResults();
                List<Refund> refunds = rsp.getRefunds();
                refunds = refunds == null ? new ArrayList<Refund>() : refunds;
                logger.info("IncGetRefund.doGetRefund#获取到的退款订单总数：" + totalResults);
                for (Refund rf : refunds) {
                    //                    if (136308312001046L == rf.getTid() || 126997053365177L == rf.getTid()) { // 特殊处理订单
                    //                        continue;
                    //                    }
                    ld = new LogBean();
                    ld.setTid("" + rf.getTid());
                    ld.setOrder_status(rf.getOrderStatus());
                    String res = "";
                    try {
                        res = deal(rf, refunds.size());
                        ld.setParam(TaobaoConst.RETORDER, res);
                    }
                    catch (Exception e) {
                        ld.setParam(TaobaoConst.RETORDER, TaobaoConst.SQLFAIL + ":" + e.getMessage());
                        continue;
                    }
                    if (TaobaoConst.PROCSUCC.equals(res) || TaobaoConst.PROCSPECSUCC.equals(res)) {
                        succCount++;
                    }
                    else if (TaobaoConst.PROCFAIL.equals(res) || TaobaoConst.PROCSPECFAIL.equals(res)
                            || TaobaoConst.ORDERCHGFAIL.equals(res)) {
                        failCount++;
                    }
                    al.add(ld);
                }
                logger.info("IncGetRefund.doGetRefund#res#all=" + totalResults + "#succ=" + succCount + "#end");

                new ServiceCall("eop").call2(TaobaoService.TAOBAO_SRV_PATH + "log", TaobaoConst.RETORDER,
                        Dates.format(startCreated, TaobaoConst.DATE_FORMAT),
                        Dates.format(endCreated, TaobaoConst.DATE_FORMAT), TaobaoConst.ISUCC, totalResults, succCount,
                        failCount, al, page_num);
                if (rsp.getTotalResults() == 0 || rsp.getTotalResults() / TaobaoConfig.PAGE - page_num + 1 == 0) {
                    return;
                }
                page_num++;
            }
            catch (Exception e) {
                new ServiceCall("eop").call2(TaobaoService.TAOBAO_SRV_PATH + "log", TaobaoConst.RETORDER,
                        Dates.format(startCreated, TaobaoConst.DATE_FORMAT),
                        Dates.format(endCreated, TaobaoConst.DATE_FORMAT), TaobaoConst.IFAIL, totalResults, succCount,
                        failCount, al, page_num);
                return;
            }
        }
    }

    /**
     * 将获取的参数组装调用存储过程<br/>
     *
     * @param map1
     * @param map2
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws DAOException
     */
    private String deal(Refund rf, int count_num) throws Exception {
        Long tid = rf.getTid();
        logger.info("IncGetRefund.doGetRefund.deal#param#tid=" + tid);
        String create_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(rf.getCreated());
        String ret_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(rf.getModified());
        String reason = rf.getReason();
        String desc = rf.getDesc();

        Trade trade = TaobaoOrderUtils.getBaseOrderInfo(tid); // 保存订单基础信息
        /*2012年04月23日15：40：08 添加上网卡信息判断 begin*/
        /*2012年04月26日10：25：16 上网卡需求延迟 begin*/
        if (!TaobaoOrderUtils.isWTOrder(trade)) {
            try {
                dealNotWTRet(rf, trade, count_num);
                return TaobaoConst.SPEC;
            }
            catch (Exception e) {
                e.printStackTrace();
                return TaobaoConst.SPECFAIL + ":" + e.getMessage();
            }
        }
        /*2012年04月26日10：25：16 上网卡需求延迟 end*/
        /*2012年04月23日15：40：08 添加上网卡信息判断 end*/
        WtExtResult wr = TaobaoOrderUtils.getExtOrderInfo(tid); // 保存订单基础信息
        /* Trade信息-基础信息 */
        logger.info("IncGetRefund.doGetRefund.deal#param#tid=" + tid);
        Long num_iid = trade.getNumIid();
        Long num = trade.getNum();
        String price = trade.getPrice();
        String payment = trade.getPayment();
        String total_fee = trade.getTotalFee();
        String commission_fee = trade.getCommissionFee();
        String trade_status = trade.getStatus(); // 等待卖家发货,即:买家已付款
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
        //            String out_plan_id = "9000035455A100033"; // 测试使用
        //            String out_plan_id = "9000035455A9000318554"; // 测试使用
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

        //            phone_deposit = 100L; // 测试
        //            price = "100"; // 测试
        //            payment = "100"; // 测试
        //            total_fee = "100"; // 测试

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

        params.put("create_time", create_time); // 淘宝退款申请时间（买家）
        params.put("ret_time", ret_time); // 淘宝退款确认时间（卖家）
        params.put("reason", reason); // 退款理由（用户选择）
        params.put("desc", desc); // 退款理由（用户自己填写）

        // 执行入库
        return (String) new ServiceCall("eop").call2(TaobaoService.TAOBAO_SRV_PATH + "recordRefund",
                params);
    }

    /**
     * 处理不是网厅的订单
     * @param baseOrderInfo
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private void dealNotWTRet(Refund rf, Trade baseOrderInfo, int count_num) throws SQLException,
            ClassNotFoundException {
        List<Order> orders = baseOrderInfo.getOrders();
        for (int i = 0; i < count_num; i++) {
            Order order = orders.get(i);
            Long cid = order.getCid();
            if (TaobaoConst.CID_SWK.equals(cid + "")) {
                dealSWKRet(baseOrderInfo, rf, count_num);
            }
            // 添加类目2
        }
    }

    /**
     * 处理上网卡退款订单
     * @param trade
     * @param order
     * @param count_sum 子订单序号
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    private String dealSWKRet(Trade trade, Refund rf, int count_num) throws SQLException, ClassNotFoundException {
        // Trade trade = (Trade) map1.get("trade");
        /* Trade信息-基础信息 */
        Long tid = trade.getTid();
        Long num_iid = trade.getNumIid() == null ? 0L : trade.getNumIid();
        Long num = trade.getNum() == null ? 0L : trade.getNum();
        String price = trade.getPrice() == null ? "0" : trade.getPrice();
        String payment = trade.getPayment();
        String total_fee = trade.getTotalFee();
        String commission_fee = trade.getCommissionFee();
        String trade_status = trade.getStatus();
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

        // 获取目标子订单
        Order childOrder = TaobaoUtils.getChildOrder(trade.getOrders(), "" + rf.getOid());

        /*2012年04月23日16：24：29 添加退款信息 begin*/
        String phonecode = ""; // 置空
        String outer_iid = childOrder.getOuterIid(); // 置空
        Long refund_id = rf.getRefundId();
        Long oid = rf.getOid();
        String cid = TaobaoConst.CID_SWK;
        String create_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(rf.getCreated());
        String ret_time = new SimpleDateFormat("yyyyMMddHHmmdd").format(rf.getModified());
        String child_single_price = childOrder.getPrice(); /*目前不支持字段，退款时候，可以废弃*/
        String refundFee = rf.getRefundFee();
        Long child_num = rf.getNum();
        String reason = rf.getReason();
        String desc = rf.getDesc();
        /*2012年04月23日16：24：29 添加退款信息 end*/

        String invoice_name = trade.getInvoiceName();
        invoice_name = invoice_name == null ? "" : invoice_name;

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
        params.put("oid", oid + ""); // 子订单号
        params.put("cid", cid + ""); // 类目编码
        params.put("create_time", create_time); // 淘宝退款申请时间（买家）
        params.put("ret_time", ret_time); // 淘宝退款确认时间（卖家）
        params.put("child_single_price", child_single_price); // 子订单单价
        params.put("refundFee", refundFee); // 子订单总价，注：这里特指退款金额
        params.put("child_num", child_num + ""); // 子订单数量
        params.put("count_num", count_num + ""); // 淘宝退款确认时间（卖家）
        params.put("reason", reason); // 退款理由（用户选择）
        params.put("desc", desc); // 退款理由（用户自己填写）

        // 执行入库
        return (String) new ServiceCall("eop").call2(TaobaoService.TAOBAO_SRV_PATH + "recordSwkRefund", params);
    }

}
