package org.phw.eop.serv;

import java.util.Map;

import org.phw.eop.domain.EopActionBean;
import org.phw.eop.domain.EopSystemParam;
import org.phw.eop.support.EopActionException;
import org.phw.eop.support.EopActionSupport;
import org.phw.eop.support.EopSystemParamAware;
import org.phw.eop.utils.Clazz;
import org.phw.eop.utils.Strings;

/**
 * WEB层的EOP管理类.
 *
 * @author wanglei
 *
 * 2012-6-6
 */
public class EopMgrWeb {

    public Object doBizAction(EopActionBean eopAction, EopSystemParam eopSystemParam, Map<String, Object> params)
            throws EopActionException {
        EopActionSupport actionBean = reGetActionBean(eopAction);
        if (actionBean == null) {
            throw new RuntimeException("action bean is not well-defined");
        }
        if (actionBean instanceof EopSystemParamAware) {
            ((EopSystemParamAware) actionBean).setSystemParam(eopSystemParam);
        }
        return actionBean.doAction(params);

    }

    /**
     * 再次实例化化ActionBean。
     * @param eopAction
     * @return
     */
    private EopActionSupport reGetActionBean(EopActionBean eopAction) {
        Class<? extends EopActionSupport> actionBeanClass = null;

        String actionclass = eopAction.getActionclass();
        if (!Strings.isEmpty(actionclass)) {
            if (!Clazz.classExists(actionclass)) {
                return null;
            }
            Class<?> clz = Clazz.forClass(actionclass);
            if (!EopActionSupport.class.isAssignableFrom(clz)) {
                return null;
            }
            actionBeanClass = (Class<? extends EopActionSupport>) clz;
        }

        try {
            return Clazz.newInstance(actionBeanClass);
        }
        catch (Exception e) {
            return null;
        }
    }
}
