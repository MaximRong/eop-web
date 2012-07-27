package com.ailk.taobao.utils;

import java.util.List;

import com.taobao.api.domain.Order;

public class TaobaoUtils {

    /**
     * @param str
     * @return
     * 填充数字    在前补0<br/>
     * 如：  2  -》     02<br/>
     */
    public static String fillNum(String str) {
        if (str != null && str.length() == 1) {
            return "0" + str;
        }
        return str;
    }

    /**
     * 转换开通规则<br/>
     * 淘宝：01 当月   02 次月 03 半月<br/>
     * 我们系统：01 次月  02 当月 03 半月<br/>
     * @param effect_rule
     * @return
     */
    public static String exRule(String effect_rule) {
        if ("01".equals(effect_rule)) {
            return "02";
        }
        else if ("02".equals(effect_rule)) {
            return "01";
        }
        else if ("03".equals(effect_rule)) {
            return "03";
        }
        return effect_rule;
    }

    /**
     * 淘宝的bug，下个上线版本上线
     * @param provinceCode
     * @param cityCode
     * @return
     */
    public static String uni(String provinceCode, String cityCode) {
        if ("110000".equals(provinceCode) && "110000".equals(cityCode)) { /*北京*/
            return "110100";
        }
        else if ("310000".equals(provinceCode) && "310000".equals(cityCode)) { /*上海*/
            return "310100";
        }
        else if ("120000".equals(provinceCode) && "120000".equals(cityCode)) { /*天津*/
            return "120100";
        }
        else if ("220000".equals(provinceCode) && "220000".equals(cityCode)) { /*吉林*/
            return "220200";
        }
        else if ("500000".equals(provinceCode) && "500000".equals(cityCode)) { /*重庆*/
            return "500100";
        }

        if ("610000".equals(provinceCode) && "220403".equals(cityCode)) { /*陕西省-西安市*/
            return "610100";
        }

        /*2012年04月26日16：28：32 添加新疆部分省份地市编码转换 begin*/
        if ("650000".equals(provinceCode) && "659004".equals(cityCode)) { /*五家渠市 对应 昌吉*/
            return "652300";
        }
        if ("650000".equals(provinceCode) && "659003".equals(cityCode)) { /*图木舒克市 对应 喀什*/
            return "653100";
        }
        if ("650000".equals(provinceCode) && "659002".equals(cityCode)) { /*阿拉尔市 对应 阿克苏*/
            return "652900";
        }
        /*2012年04月26日16：28：32 添加新疆部分省份地市编码转换 end*/

        /*2012年05月03日14：32：12 修改广东省-中山市编码传错的问题 begin*/
        if ("440000".equals(provinceCode) && "210202".equals(cityCode)) { /*广东省-中山市*/
            return "442000";
        }
        /*2012年05月03日14：32：12 修改广东省-中山市编码传错的问题 end*/

        /*2012年05月18日16：41：38 修改四川省-资阳市，淘宝编码传错问题 begin*/
        if ("510000".equals(provinceCode) && "430902".equals(cityCode)) { /*四川省-资阳市*/
            return "512000";
        }
        /*2012年05月18日16：41：38 修改四川省-资阳市，淘宝编码传错问题 end*/

        /*2012年06月13日20：25：32 修改辽宁省-朝阳市，淘宝编码传错 begin*/
        if ("210000".equals(provinceCode) && "110105".equals(cityCode)) { /*辽宁省-朝阳市*/
            return "211300";
        }
        /*2012年06月13日20：25：32 修改辽宁省-朝阳市，淘宝编码传错 end*/

        /*2012年06月14日19：51：58 修改四川省-眉山市 淘宝编码传错 begin*/
        if ("510000".equals(provinceCode) && "511181".equals(cityCode)) { /*四川省-眉山市*/
            return "511400";
        }
        /*2012年06月14日19：51：58 修改四川省-眉山市 淘宝编码传错 begin*/

        return cityCode;
    }

    /**
     * 部分地市转换问题
     * @return
     */
    public static String uniNext(String provinceCode, String cityCode) {
        if ("湖北省".equals(provinceCode) && "潜江市".equals(cityCode)) {
            return "天门市";
        }
        if ("湖北省".equals(provinceCode) && "仙桃市".equals(cityCode)) {
            return "天门市";
        }
        if ("海南省".equals(provinceCode) && "西沙群岛".equals(cityCode)) {
            return "海口市";
        }
        if ("海南省".equals(provinceCode) && "南沙群岛".equals(cityCode)) {
            return "海口市";
        }
        if ("海南省".equals(provinceCode) && "中沙群岛的岛礁及其海域".equals(cityCode)) {
            return "海口市";
        }
        /*2012年04月26日16：28：32 添加新疆部分省份地市编码转换 begin*/
        if ("新疆维吾尔自治区".equals(provinceCode) && "五家渠市".equals(cityCode)) {
            return "昌吉回族自治州";
        }
        if ("新疆维吾尔自治区".equals(provinceCode) && "图木舒克市".equals(cityCode)) {
            return "喀什地区";
        }
        if ("新疆维吾尔自治区".equals(provinceCode) && "阿拉尔市".equals(cityCode)) {
            return "阿克苏地区";
        }
        /*2012年04月26日16：28：32 添加新疆部分省份地市编码转换 end*/
        return cityCode;
    }

    /**
     * 通过子订单号，在子订单数组中找到这个订单
     * @param list
     * @param oid
     * @return
     */
    public static Order getChildOrder(List list, String oid) {
        for (int i = 0; i < list.size(); i++) {
            Order order = (Order) list.get(i);
            if (oid.equals(order.getOid() + "")) {
                return order;
            }
        }
        return null;
    }

}
