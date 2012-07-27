package org.phw.eop.serv;

import java.io.IOException;
import java.sql.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.phw.core.lang.Collections;
import org.phw.eop.domain.EopActionBean;
import org.phw.eop.domain.EopAppBean;
import org.phw.eop.domain.EopLogBean;
import org.phw.eop.domain.EopMockBean;
import org.phw.eop.domain.EopRoleBean;
import org.phw.eop.domain.EopSystemParam;
import org.phw.eop.support.EopActionException;
import org.phw.eop.utils.EopConst;
import org.phw.eop.utils.EopUtils;
import org.phw.eop.utils.Inets;
import org.phw.eop.utils.ServletUtils;
import org.phw.eop.utils.Strings;
import org.phw.web.scall.ServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EopServlet extends HttpServlet {
    private Logger logger = LoggerFactory.getLogger(EopServlet.class);
    private static final long serialVersionUID = -5314313547773286555L;

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        EopLogBean eopLogBean = new EopLogBean();
        EopSystemParam eopSystemParam = new EopSystemParam();
        RspBuilder rspBuilder = new RspBuilder(request, response, eopSystemParam);
        try {
            doEop(rspBuilder, eopLogBean, eopSystemParam);
        }
        catch (Exception e) {
            logger.warn("doEop error", e);
            rspBuilder.response("E500", e, null);
        }
    }

    private void doEop(RspBuilder rspBuilder, EopLogBean eopLogBean, EopSystemParam eopSystemParam) throws IOException {
        boolean ok = logFirst(rspBuilder, eopLogBean, eopSystemParam);
        if (!ok) {
            return;
        }

        if (!checkAppValid(rspBuilder)) {
            return;
        }

        try {
            if (!rspBuilder.checkParameters()) {
                return;
            }
            logger.debug("请求报文:" + eopSystemParam.getEopLog().getReqcontent());
        }
        catch (Exception e) {
            logger.warn("checkParameters error", e);
            rspBuilder.response("E306", e, null);
            return;
        }

        if (tryMockRsp(rspBuilder)) {
            return;
        }

        doAction(rspBuilder);
    }

    private void doAction(RspBuilder rspBuilder) throws IOException {
        EopSystemParam eopSystemParam = rspBuilder.getEopSystemParam();
        EopActionBean eopAction = eopSystemParam.getEopAction();
        //EopActionSupport actionBean = eopAction.getActionBean();
        //        if (actionBean == null) {
        //            rspBuilder.response("E401", "action bean is not well-defined", null);
        //            return;
        //        }

        //        if (actionBean instanceof EopSystemParamAware) {
        //            ((EopSystemParamAware) actionBean).setSystemParam(eopSystemParam);
        //        }
        try {
            Object rsp = doAction(eopSystemParam.getEopLog(), eopAction, eopSystemParam, rspBuilder.getParameters());
            rspBuilder.response(rsp);
        }
        catch (EopActionException e) {
            logger.warn("doAction error", e);
            rspBuilder.response(e.getMessageCode(), e, EopUtils.ExceptionStackAsString(e));
        }
        catch (Throwable e) {
            logger.warn("doAction error", e);
            rspBuilder.response("B500", e, EopUtils.ExceptionStackAsString(e));
        }
    }

    /**
     * 执行业务逻辑，并记录开始、结束时间.
     * 
     * @param logBean
     *            日志信息Bean.
     * @param actionBean
     *            业务逻辑Bean.
     * @param params
     *            业务参数.
     * @return 业务执行结果.
     * @throws EopActionException
     *             异常信息.
     */
    private Object doAction(EopLogBean logBean, EopActionBean eopAction, EopSystemParam eopSystemParam,
            Map<String, Object> params)
            throws EopActionException {
        logBean.setPats(System.currentTimeMillis());
        try {
            List<EopRoleBean> roles = eopAction.getRoles();
            if (!Collections.isEmpty(roles)) {
                for (EopRoleBean role : roles) {
                    if ("WEBX".equalsIgnoreCase(role.getRoleid())) {
                        EopMgrWeb mgr = new EopMgrWeb();
                        return mgr.doBizAction(eopAction, eopSystemParam, params);
                    }
                }
            }
            return new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.doBizAction",
                    eopAction, eopSystemParam, params);
            //return actionBean.doAction(params);
        }
        finally {
            logBean.setAats(System.currentTimeMillis());
        }
    }

    private boolean tryMockRsp(RspBuilder rspBuilder) throws IOException {
        EopSystemParam eopSysParam = rspBuilder.getEopSystemParam();
        EopAppBean eopApp = eopSysParam.getEopApp();
        if (!eopApp.isMock()
                && !Strings.equalsIgnoreCase(eopSysParam.getEopLog().getMock(), "true")) {
            return false;
        }

        String actionId = eopSysParam.getEopAction().getActionid();
        EopMockBean mock = (EopMockBean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.matchMockBean",
                rspBuilder.getParameters(), eopApp, actionId);

        if (mock == null) {
            return false;
        }

        rspBuilder.response(mock.getRsp());
        return true;
    }

    private boolean logFirst(RspBuilder rspBuilder, EopLogBean eopLogBean, EopSystemParam eopSystemParam)
            throws IOException {
        eopLogBean.setArrivalts(System.currentTimeMillis());
        eopSystemParam.setEopLog(eopLogBean);
        String reqTs = rspBuilder.getRequest().getParameter(EopConst.EOP_REQTS);
        eopLogBean.setReqtsStr(reqTs);
        String mock = rspBuilder.getRequest().getParameter(EopConst.EOP_MOCK);
        eopLogBean.setMock(mock);
        String fmt = rspBuilder.getRequest().getParameter(EopConst.EOP_FMT);
        eopLogBean.setFmt(fmt);
        String eopCipher = rspBuilder.getRequest().getParameter(EopConst.EOP_CIPHER);
        eopLogBean.setEopCipher(eopCipher);
        String eopSign = rspBuilder.getRequest().getParameter(EopConst.EOP_SIGN);
        eopLogBean.setEopSign(eopSign);

        String apptx = rspBuilder.getRequest().getParameter(EopConst.EOP_APPTX);
        eopLogBean.setApptx(apptx);
        eopLogBean.setClientip(ServletUtils.getRemoteAddr(rspBuilder.getRequest()));
        eopLogBean.setServerip(Inets.getIp());
        String appCode = rspBuilder.getRequest().getParameter(EopConst.EOP_APPCODE);
        eopLogBean.setAppcode(appCode);
        String actionName = rspBuilder.getRequest().getParameter(EopConst.EOP_ACTION);
        eopLogBean.setActionname(actionName);

        if (Strings.isEmpty(appCode)) {
            rspBuilder.response("E101", EopConst.EOP_APPCODE + " is required", null);
            return false;
        }

        EopAppBean eopAppBean = (EopAppBean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.getAppByCode",
                        appCode);

        if (eopAppBean == null) {
            rspBuilder.response("E102", EopConst.EOP_APPCODE + " is invalid", null);
            return false;
        }
        eopSystemParam.setEopApp(eopAppBean);
        eopLogBean.setAppid(eopAppBean.getAppid());

        if (Strings.isEmpty(actionName)) {
            rspBuilder.response("E103", EopConst.EOP_ACTION + " is required", null);
            return false;
        }

        EopActionBean eopActionBean = (EopActionBean) new ServiceCall("eop").call2(
                "org.phw.eop.srv.EopMgrSrv.getActionByName", actionName);

        if (eopActionBean == null) {
            rspBuilder.response("E104", EopConst.EOP_ACTION + " is invalid", null);
            return false;
        }
        eopSystemParam.setEopAction(eopActionBean);
        eopLogBean.setActionid(eopActionBean.getActionid());

        // 检查REQTS是否为空
        if (Strings.isEmpty(reqTs)) {
            rspBuilder.response("E105", EopConst.EOP_REQTS + " is required", null);
            return false;
        }

        // 检查REQTS是否格式正确
        try {
            eopLogBean.setReqts(Long.valueOf(reqTs));
        }
        catch (NumberFormatException e) {
            rspBuilder.response("E106", EopConst.EOP_REQTS + " is invalid", null);
            return false;
        }

        // 检查是否REQTS过期(在服务器当前时间的前后10分钟之内)
        // 后续时间都以REQTS为准。
        int reqTimeout = (Integer) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.getParamInt",
                EopConst.PARAM_REQTIMEOUTMINUTES, 10) * 60000;
        if (eopLogBean.getArrivalts() - eopLogBean.getReqts() > reqTimeout
                || eopLogBean.getReqts() - eopLogBean.getArrivalts() > reqTimeout) {
            rspBuilder.response("E107", EopConst.EOP_REQTS + " timeout", null);
            return false;
        }

        // 检查APPTX是否必须
        boolean apptxNeeded = (Boolean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.getParamBool",
                EopConst.PARAM_PREFIX_APPTX_REQUIRED + eopLogBean.getAppid(), false);
        if (Strings.isEmpty(apptx) && apptxNeeded) {
            rspBuilder.response("E108", EopConst.EOP_APPTX + " is required", null);
            return false;
        }

        // 检查SIGN是否正确
        if (eopAppBean.isSign() && Strings.isEmpty(eopSign)) {
            rspBuilder.response("E109", EopConst.EOP_SIGN + " is required", null);
            return false;
        }

        // 检查ACTION对于APP而定义的最小调用间隔（秒数）
        int actionMinInterval = (Integer) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.getParamInt",
                EopConst.PARAM_PREFIX_ACTION_MIN_INTERVAL_SECONDS + eopLogBean.getActionid(), 0);
        boolean minInterval = (Boolean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.checkMinInterval",
                eopLogBean, actionMinInterval);
        if (actionMinInterval > 0 && !minInterval) {
            rspBuilder.response("E110", eopLogBean.getActionname()
                    + " cannot be called so frequently within min interval seconds " + actionMinInterval, null);
            return false;
        }

        return true;
    }

    public boolean checkAppValid(RspBuilder rspBuilder) throws IOException {
        EopAppBean eopApp = rspBuilder.getEopSystemParam().getEopApp();
        EopLogBean eopLog = rspBuilder.getEopSystemParam().getEopLog();

        // 检查有效期和失效期
        if (eopApp.getEffective() != null && eopApp.getEffective().compareTo(new Date(eopLog.getReqts())) > 0) {
            rspBuilder.response("E201", "app is not effected", null);
            return false;
        }
        if (eopApp.getExpired() != null && eopApp.getExpired().compareTo(new Date(eopLog.getReqts())) < 0) {
            rspBuilder.response("E202", "app is expired", null);
            return false;
        }

        // 检查调用次数限制
        int timesLimit = (Integer) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.getParamInt",
                EopConst.PARAM_PREFIX_APPTIMELIMIT + eopApp.getAppid(), 0);
        boolean appTimes = (Boolean) new ServiceCall("eop").call2("org.phw.eop.srv.EopMgrSrv.increaseAppTimes",
                eopLog, eopApp, timesLimit);
        if (timesLimit > 0 && !appTimes) {
            rspBuilder.response("E203", "app call times arrived upper limit", null);
            return false;
        }

        // 检查ACTION所要求的角色
        EopActionBean eopAction = rspBuilder.getEopSystemParam().getEopAction();
        if (!isRoleAcessable(eopLog.getReqts(), eopApp.getRoles(), eopAction.getRoles())) {
            rspBuilder.response("E204", "action is not valid for the app", null);
            return false;
        }

        return true;
    }

    private boolean isRoleAcessable(long reqTs, List<EopRoleBean> appRoles, List<EopRoleBean> actionRoles) {
        if (actionRoles == null || actionRoles.size() == 0) {
            return true;
        }

        if (appRoles == null || appRoles.size() == 0) {
            return false;
        }

        java.util.Date today = new Date(reqTs);
        for (EopRoleBean actionRole : actionRoles) {
            if (!actionRole.isValid(today)) {
                continue;
            }

            for (EopRoleBean appRole : appRoles) {
                if (appRole.isValid(today) && appRole.getRoleid().equals(actionRole.getRoleid())) {
                    return true;
                }
            }
        }

        return false;
    }
}
