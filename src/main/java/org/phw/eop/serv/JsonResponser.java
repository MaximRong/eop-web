package org.phw.eop.serv;

import org.phw.eop.domain.EopLogBean;
import org.phw.eop.utils.JsonUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class JsonResponser implements Responser {

    @Override
    public String response(String rspCode, String desc, String rspMsg, EopLogBean eopLogBean) {
        String rsp = rspMsg == null ? "" : rspMsg;
        String code = rspCode;

        JSON rspObj = null;
        if ("0".equals(code)) {
            rspObj = JsonUtils.parseJSON(rspMsg);
            //            if (rspObj == null) {
            //                Document doc = XmlUtils.parseXML(rspMsg);
            //                if (doc != null) {
            //                    rspObj = JsonUtils.docToJson(doc);
            //                }
            //            }
        }

        JSONObject returnObj = new JSONObject();
        returnObj.put("rspcode", code);
        returnObj.put("rspdesc", desc);
        returnObj.put("trxid", eopLogBean.getTrxid());
        returnObj.put("rspmsg", rspObj != null ? rspObj : rsp);

        return returnObj.toJSONString();
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

}
