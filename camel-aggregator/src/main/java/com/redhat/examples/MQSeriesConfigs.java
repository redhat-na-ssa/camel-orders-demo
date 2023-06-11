package com.redhat.examples;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "ibm.mq", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface MQSeriesConfigs {
    String queueManager();
    String channel();
    String connName();
    String user();
    String password();
    
}
