package com.ailk.taobao.utils;

import java.util.List;

import com.ailk.thirdservice.taobao.utils.TaobaoConst;
import com.taobao.api.ApiException;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.domain.Order;
import com.taobao.api.domain.Trade;
import com.taobao.api.domain.WtExtResult;
import com.taobao.api.request.TradeFullinfoGetRequest;
import com.taobao.api.request.TradeWtverticalGetRequest;
import com.taobao.api.response.TradeFullinfoGetResponse;
import com.taobao.api.response.TradeWtverticalGetResponse;

/**
 * @author      易晗
 * @since       2012-2-3,上午10:10:42
 * @company     Asiainfo-Linkage
 * @description 从淘宝获取订单<br/>
 * 两个部分：<br/>
 * 1.基础订单信息获取<br/>
 * 2.扩展信息获取<br/>
 */
public class TaobaoOrderUtils {

    /**
     * 获取订单基本信息<br/>
     * @param tid
     * @return Map<String, Trade> -> Map<"trade", new Trade()><br/>
     */
    public static Trade getBaseOrderInfo(Long tid) {
        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                TaobaoConfig.APP_SECRET);//实例化TopClient类
        TradeFullinfoGetRequest req = new TradeFullinfoGetRequest();//实例化具体API对应的Request类
        req.setTid(tid);
        req
                .setFields("tid,num_iid,num,price,payment,total_fee,commission_fee,status,created,pay_time," +
                        "buyer_message,buyer_nick,alipay_no,receiver_name,receiver_state,receiver_city," +
                        "receiver_district,receiver_address,receiver_zip,receiver_mobile,receiver_phone," +
                        /*2012年04月23日11：28：25 添加字段 begin*/
                        "orders.oid,orders.price,orders.payment,orders.num," +
                        /*2012年04月23日11：28：25 添加字段 end*/
                        "orders.outer_sku_id,orders.outer_iid,orders.cid,invoice_name,orders.refund_id  "); // API:http://api.taobao.com/apidoc/api.htm?path=cid:5-apiId:54
        TradeFullinfoGetResponse rsp;
        try {
            rsp = client.execute(req, TaobaoConfig.APP_SESSION);
            System.out.println("params:" + rsp.getParams());
            System.out.println("result:" + rsp.getBody());
            return rsp.getTrade();
        }
        catch (ApiException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static WtExtResult getExtOrderInfo(Long tid) {
        TaobaoClient client = new DefaultTaobaoClient(TaobaoConfig.TOP_URL, TaobaoConfig.APP_KEY,
                TaobaoConfig.APP_SECRET);//实例化TopClient类
        TradeWtverticalGetRequest req = new TradeWtverticalGetRequest();//实例化具体API对应的Request类
        req.setTids(tid + "");
        TradeWtverticalGetResponse rsp;
        try {
            rsp = client.execute(req, TaobaoConfig.APP_SESSION); // 执行API请求并打印结果
            System.out.println("params:" + rsp.getParams());
            System.out.println("result:" + rsp.getBody());
            List<WtExtResult> wrs = rsp.getWtextResults();
            if (wrs != null && wrs.size() != 0) {
                return wrs.get(0);
            }
            /*2012年06月08日 9：49：41 添加非网厅订单特殊处理 begin*/
            if ("15".equals(rsp.getErrorCode())) {
                WtExtResult tmp = new WtExtResult();
                tmp.setTid(TaobaoConst.SPEC_RANDOM);
                return tmp;
            }
            /*2012年06月08日 9：49：41 添加非网厅订单特殊处理 begin*/
            return null;
        }
        catch (ApiException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 判断是否是网厅交易
     * @param baseOrderInfo
     * @return true：是网厅交易
     */
    public static boolean isWTOrder(Trade baseOrderInfo) {
        List<Order> orders = baseOrderInfo.getOrders();
        if (orders != null) {
            if (orders.size() > 1) { // 多个子订单，不是网厅交易规则，排除
                return false;
            }
            // 添加类目1
            if (TaobaoConst.CID_SWK.equals(orders.get(0).getCid() + "")) { // 上网卡
                return false;
            }
        }
        return true;
    }

}
