/*
 * Copyright 2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.examples;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.opentelemetry.OpenTelemetryTracer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.AggregationStrategies;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

import com.ibm.mq.jakarta.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;

@ApplicationScoped
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(CamelConfiguration.class);
  
  @Inject
  AggregatorProperties props;

  @Inject
  CamelContext context;

  OpenTelemetryTracer otelTracer;

  @Inject
  MQSeriesConfigs mqSeriesConfigs;

  MQQueueConnectionFactory mqFactory;

  @PostConstruct
  void start() throws JMSException {
    log.info("start() setting camel breadcrumb configs");
    context.setUseMDCLogging(true);
    context.setUseBreadcrumb(true);

    otelTracer = new OpenTelemetryTracer();
    otelTracer.init(context);

    mqFactory = createWMQConnectionFactory();
    context.getRegistry().bind("mqConnectionFactory", mqFactory);
    log.info("connectionFactory registered in camel context: "+context.getRegistry().findByType(ConnectionFactory.class));

  }

  private AggregationStrategy orderAggregationStrategy() {
    return AggregationStrategies
            .flexible()
            .accumulateInCollection(ArrayList.class)
            .storeInBody()
            .pick(new SimpleExpression("${body}"))
          ;
  }
  
  @Override
  public void configure() throws Exception {
    
    from("jms:queue:{{com.redhat.example.processedQueueName}}?connectionFactory=#mqConnectionFactory")
      .log(LoggingLevel.DEBUG, "[${headers}]")
      .log(LoggingLevel.INFO, "${headers.X-CORRELATION-ID} : Picked up processed order: [${body}]")
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
      .aggregate()
        .simple("${body[customer]}")
        .aggregationStrategy(this.orderAggregationStrategy())
        .completionTimeout(5000L)
        .completionSize(10)
          .log(LoggingLevel.INFO, "${headers.X-CORRELATION-ID} : Completing aggregate order: [${exchangeProperty.CamelAggregatedCorrelationKey}]")
          .marshal().json(JsonLibrary.Jackson, true)
          .setHeader("CurrentTimeMillis", method(System.class, "currentTimeMillis"))
          .to(ExchangePattern.InOnly, String.format("file:%s?fileName=order-${exchangeProperty.CamelAggregatedCorrelationKey}-${header.CurrentTimeMillis}.json", props.dir()))
      .end()
    ;
  }


  // https://github.com/ibm-messaging/mq-jms-spring/blob/master/mq-jms-spring-boot-starter/src/main/java/com/ibm/mq/spring/boot/MQConnectionFactoryFactory.java#L112-L269
  private MQQueueConnectionFactory createWMQConnectionFactory() throws JMSException {
    MQQueueConnectionFactory cf = new MQQueueConnectionFactory();
    cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
    cf.setStringProperty(WMQConstants.WMQ_CONNECTION_NAME_LIST, mqSeriesConfigs.connName());
    cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
    cf.setQueueManager(mqSeriesConfigs.queueManager());
    cf.setStringProperty(WMQConstants.WMQ_CHANNEL, mqSeriesConfigs.channel());
    cf.setStringProperty(WMQConstants.USERID, mqSeriesConfigs.user());
    cf.setStringProperty(WMQConstants.PASSWORD, mqSeriesConfigs.password());

     return cf;
 }

  @PreDestroy
  void stop() throws IOException {
    if(otelTracer != null)
      otelTracer.close();
    
    if(mqFactory != null)
          mqFactory.clear();
  }
}
