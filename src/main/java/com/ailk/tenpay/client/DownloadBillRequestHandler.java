package com.ailk.tenpay.client;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ailk.tenpay.util.MD5Util;

public class DownloadBillRequestHandler extends RequestHandler {

    public DownloadBillRequestHandler(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);

    }

    /**
     * 创建md5摘要,规则是:按参数固定顺序组串,遇到空值的参数不参加签名。
     */
    @Override
    protected void createSign() {
        StringBuffer sb = new StringBuffer();
        sb.append("spid=" + getParameter("spid") + "&");
        sb.append("trans_time=" + getParameter("trans_time") + "&");
        sb.append("stamp=" + getParameter("stamp") + "&");
        sb.append("cft_signtype=" + getParameter("cft_signtype") + "&");
        sb.append("mchtype=" + getParameter("mchtype") + "&");
        sb.append("key=" + getKey());

        String enc = "";
        String sign = MD5Util.MD5Encode(sb.toString(), enc).toLowerCase();

        setParameter("sign", sign);

        // debug信息
        setDebugInfo(sb.toString() + " => sign:" + sign);

    }
}
