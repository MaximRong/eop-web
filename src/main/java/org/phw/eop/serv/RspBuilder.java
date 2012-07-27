package org.phw.eop.serv;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.phw.eop.domain.EopActionBean;
import org.phw.eop.domain.EopActionParamBean;
import org.phw.eop.domain.EopAppBean;
import org.phw.eop.domain.EopAppSecurityBean;
import org.phw.eop.domain.EopLogBean;
import org.phw.eop.domain.EopSystemParam;
import org.phw.eop.domain.RspFormat;
import org.phw.eop.sec.support.SecurityCipherSupport;
import org.phw.eop.sec.support.SecuritySignSupport;
import org.phw.eop.utils.EopConst;
import org.phw.eop.utils.EopUtils;
import org.phw.eop.utils.FnMatch;
import org.phw.eop.utils.Strings;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

public class RspBuilder {
    private Logger logger = LoggerFactory.getLogger(RspBuilder.class);
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Map<String, Object> parameters;

    private EopSystemParam eopSystemParam;

    public EopSystemParam getEopSystemParam() {
        return eopSystemParam;
    }

    public RspBuilder(HttpServletRequest request, HttpServletResponse response, EopSystemParam eopSystemParam) {
        this.request = request;
        this.response = response;
        this.eopSystemParam = eopSystemParam;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void response(String rspCode, Throwable e, String rspMsg) throws IOException {
        String message = e.getMessage();
        response(rspCode, Strings.isEmpty(message) ? e.toString() : message, rspMsg);
    }

    public void response(String rspCode, String rspDesc, String rspMsg) throws IOException {
        EopLogBean eopLog = eopSystemParam.getEopLog();
        Responser responser = eopLog.getRspFmt() == RspFormat.XML ? new XmlResponser() : new JsonResponser();
        response.setContentType(responser.getContentType() + ";charset=UTF-8");
        eopLog.setRspcode(rspCode);
        eopLog.setRspdesc(rspDesc);
        eopLog = (EopLogBean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.newLogTrxid", eopLog);
        String rspStr = responser.response(rspCode, rspDesc, rspMsg, eopLog);
        eopLog.setRspmsg(rspStr);
        eopLog.setRspts(System.currentTimeMillis());
        eopLog = (EopLogBean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.newLog",
                eopLog, rspCode, eopSystemParam);

        PrintWriter out = response.getWriter();
        out.print(rspStr);
        out.close();
    }

    private SecurityCipherSupport cipher = null;

    private SecurityCipherSupport getCipher(EopAppBean eopApp, EopLogBean eopLog) {
        if (cipher != null) {
            return cipher;
        }

        EopAppSecurityBean paramSec = eopApp.getParamSec(eopLog.getReqts());
        if (paramSec != null && paramSec.getSecurityBean() instanceof SecurityCipherSupport) {
            cipher = (SecurityCipherSupport) paramSec.getSecurityBean();
        }

        if (cipher == null) {
            throw new RuntimeException("parameter encrypt algorithem is defined for the app");
        }

        return cipher;
    }

    public boolean checkParameters() throws IOException {
        EopAppBean eopApp = eopSystemParam.getEopApp();
        EopLogBean eopLog = eopSystemParam.getEopLog();
        EopActionBean eopAction = eopSystemParam.getEopAction();

        parameters = new HashMap<String, Object>(eopAction.getParams().size());

        Map<String, String> eopCipherMap = null;
        if (!Strings.isEmpty(eopLog.getEopCipher())) {
            try {
                String eopUncipher = getCipher(eopApp, eopLog).decrypt(eopLog.getEopCipher());
                eopCipherMap = EopUtils.parseQueryString(eopUncipher);
            }
            catch (Exception e) {
                logger.warn("eop_cipher decrypt error", e);
                response("E301", "eop_cipher decrypt error", e.getMessage());
                return false;
            }
        }

        TreeMap<String, String> treeMap = eopApp.isSign() ? new TreeMap<String, String>() : null;
        for (EopActionParamBean param : eopAction.getParams()) {
            boolean exists = false;
            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String name = parameterNames.nextElement();
                if (FnMatch.fnmatch(param.getName(), name) && !parameters.containsKey(name)) {
                    String[] parameterValues = request.getParameterValues(name);
                    if (parameterValues.length > 1) {
                        response("E302", "parameter should be single", null);
                        return false;
                    }

                    String paramValue = decryptParamValue(eopApp, eopLog, param, parameterValues);
                    Object convertedParam = param.isValid(paramValue);
                    parameters.put(name, convertedParam);

                    if (treeMap != null) {
                        treeMap.put(name, parameterValues[0]);
                    }
                    exists = true;
                }
            }

            if (eopCipherMap != null) {
                for (Entry<String, String> eopCipherEntry : eopCipherMap.entrySet()) {
                    String name = eopCipherEntry.getKey();
                    if (FnMatch.fnmatch(param.getName(), name) && !parameters.containsKey(name)) {
                        String parameterValue = eopCipherEntry.getValue();
                        Object convertedParam = param.isValid(parameterValue);
                        parameters.put(name, convertedParam);

                        exists = true;
                    }
                }
            }

            if (!exists && !param.isOptional()) {
                response("E303", param.getName() + " is required", null);
                return false;
            }
        }

        if (treeMap != null) {
            EopAppSecurityBean signSec = eopApp.getSignSec(eopLog.getReqts());
            if (signSec == null || signSec.getSecurityBean() instanceof SecuritySignSupport == false) {
                response("E304", "sign algorithem is not defined for the app", null);
                return false;
            }

            String signStr = compositSignString(treeMap);
            SecuritySignSupport secSupport = (SecuritySignSupport) signSec.getSecurityBean();
            if (!secSupport.verify(signStr, eopLog.getEopSign())) {
                response("E305", "sign is invalid", null);
                return false;
            }
        }
        eopLog.setReqcontent(JSON.toJSONString(parameters));

        return true;
    }

    private String decryptParamValue(EopAppBean eopApp, EopLogBean eopLog, EopActionParamBean param,
            String[] parameterValues) {
        String paramValue = parameterValues[0];
        if (param.isEncrypted()) {
            try {
                paramValue = getCipher(eopApp, eopLog).decrypt(paramValue);
            }
            catch (Exception e) {
                throw new RuntimeException(param.getName() + "'s is corrupted ");
            }
        }
        return paramValue;
    }

    public void response(Object rsp) throws IOException {
        this.response("0", "OK", rsp instanceof String ? (String) rsp : JSON.toJSONString(rsp));
    }

    /**
     * 添加参数的封装方法
     * @return 
     */
    private String compositSignString(TreeMap<String, String> params) {
        EopLogBean eopLog = eopSystemParam.getEopLog();
        params.put(EopConst.EOP_APPCODE, eopLog.getAppcode());
        params.put(EopConst.EOP_ACTION, eopLog.getActionname());
        params.put(EopConst.EOP_REQTS, eopLog.getReqtsStr());
        if (!Strings.isEmpty(eopLog.getFmt())) {
            params.put(EopConst.EOP_FMT, eopLog.getFmt());
        }
        if (!Strings.isEmpty(eopLog.getMock())) {
            params.put(EopConst.EOP_MOCK, eopLog.getMock());
        }
        if (!Strings.isEmpty(eopLog.getEopCipher())) {
            params.put(EopConst.EOP_CIPHER, eopLog.getEopCipher());
        }
        if (!Strings.isEmpty(eopLog.getApptx())) {
            params.put(EopConst.EOP_APPTX, eopLog.getApptx());
        }

        StringBuilder sb = new StringBuilder();
        for (Entry<String, String> entry : params.entrySet()) {
            sb.append('$').append(entry.getKey()).append('$').append(entry.getValue());
        }

        return sb.toString();
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

}
