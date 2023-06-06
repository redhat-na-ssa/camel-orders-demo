package com.redhat.examples;

import org.apache.camel.Exchange;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

import com.redhat.examples.utils.BaseUtils;

public class ProjectConventionLogger {

    Logger log = Logger.getLogger(ProjectConventionLogger.class);

    public void log(Exchange e, Level level){
        String xCorrelationId = (String) e.getIn().getHeader(BaseUtils.X_CORRELATION_ID);
        String exchangeId = e.getExchangeId();
        String routeId = e.getFromRouteId();
        Exception ex = e.getException();

        log.logf(level, routeId);

    }
    
}
