package org.phw.eop.serv;

import org.phw.eop.domain.EopLogBean;

public interface Responser {
    String response(String code, String desc, String msg, EopLogBean eopLogBean);

    String getContentType();
}
