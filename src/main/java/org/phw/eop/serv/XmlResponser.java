package org.phw.eop.serv;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.phw.eop.domain.EopLogBean;
import org.phw.eop.utils.JsonUtils;
import org.phw.eop.utils.XmlUtils;

import com.alibaba.fastjson.JSON;

public class XmlResponser implements Responser {

    @Override
    public String response(String rspCode, String desc, String rspMsg, EopLogBean eopLogBean) {
        String rsp = rspMsg == null ? "" : rspMsg;
        String code = rspCode;

        Document doc = null;
        boolean hasRspMsgTag = false;
        if ("0".equals(code)) {
            doc = XmlUtils.parseXML(rspMsg);
            if (doc == null) {
                JSON json = JsonUtils.parseJSON(rspMsg);
                if (json != null) {
                    doc = XmlUtils.jsonToDoc(json);
                    hasRspMsgTag = true;
                }
            }
        }

        Document root = DocumentHelper.createDocument();
        Element rspElement = root.addElement("rsp");
        rspElement.addElement("rspcode").setText(code);
        rspElement.addElement("rspdesc").setText(desc == null ? "" : desc);
        rspElement.addElement("trxid").setText(eopLogBean.getTrxid());
        if (doc != null) {
            if (hasRspMsgTag) {
                rspElement.add(doc.getRootElement());
            }
            else {
                rspElement.addElement("rspmsg").add(doc.getRootElement());
            }
        }
        else {
            rspElement.addElement("rspmsg").setText(rsp);
        }

        return XmlUtils.formatDoc(root);
    }

    @Override
    public String getContentType() {
        return "text/xml";
    }

}
