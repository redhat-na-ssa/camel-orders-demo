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
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.opentelemetry.OpenTelemetryTracer;
import org.jboss.logging.Logger;

import com.redhat.examples.utils.BaseUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;

@ApplicationScoped
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(CamelConfiguration.class);

  @Inject
  private SplitterProperties props;

  @Inject
  CamelContext context;

  @Inject
  ConnectionFactory cFactory;

  OpenTelemetryTracer otelTracer;

  @PostConstruct
  void start() throws JMSException {
    log.info("start() setting camel breadcrumb configs");
    context.setUseMDCLogging(true);
    context.setUseBreadcrumb(true);

    otelTracer = new OpenTelemetryTracer();
    otelTracer.init(context);

    testConnection();

  }
  
  private void testConnection() throws JMSException {

    /*  TO-DO: wait until the following is seen in the log file of the mqseries container:
     *      AMQ5041I: The queue manager task 'AUTOCONFIG' has ended. [CommentInsert1(AUTOCONFIG)]
     */
    Connection connection = cFactory.createConnection();
    Session session = connection.createSession();
    TemporaryQueue queue = session.createTemporaryQueue();
    log.info("testConnection() just created tempQueue = "+queue.getQueueName());
    queue.delete();
    if(connection != null)
      connection.close();
  }

  @Override
  public void configure() throws Exception {

    onException((jakarta.jms.JMSSecurityException.class)).process(new Processor() {

      @Override
      public void process(Exchange exchange) throws Exception {
        log.error("${headers.X-CORRELATION-ID} : App is throwing jakarta.jms.JMSSecurityException");
        Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        e.printStackTrace();
      }

    }).handled(true);

    fromF("file:%s?delete=true&readLock=fileLock", props.dir())

      .process( e -> {
        /*
         * Example using Camel's breadcrumbId 
         * 
         * 
         * https://people.apache.org/~dkulp/camel/mdc-logging.html
         *    When enabled Camel will enrich the Camel Message by adding a header to it with the key breadcrumbId containing the id. 
         *    Camel will use the messageId if no existing breadcrumbId was found in the message.
         * 
         * https://camel.apache.org/components/3.20.x/eips/message.html
         *    By default, the message uses the same id as Exchange.getExchangeId as messages are associated with the Exchange
         * 
         * Subsequently, the following generates a new random Id as the breadcrumb that will propogate to all downstream camel routes
         */
        e.getIn().setHeader(Exchange.BREADCRUMB_ID, UUID.randomUUID().toString());


        /* Example using Mulesoft's CorrelationId component
         *   https://docs.mulesoft.com/mule-runtime/4.4/correlation-id
         */
        String xCorrelationId = e.getIn().getHeader(BaseUtils.X_CORRELATION_ID, String.class);
        if(xCorrelationId == null){
          e.getIn().setHeader(BaseUtils.X_CORRELATION_ID, UUID.randomUUID().toString());
        }

      })

      .log(LoggingLevel.INFO, "${headers.X-CORRELATION-ID} : Picked up orders file: [${headers.CamelFileName}]")
      .split(xpath("/orders/order"))
        .log(LoggingLevel.INFO, "Sending order: [${body}]")
        .to(ExchangePattern.InOnly, "amqp:queue:{{com.redhat.example.rawQueueName}}")
      .end()
    ;
  }

  @PreDestroy
  void stop() throws IOException {
    if(otelTracer != null)
      otelTracer.close();
  }
}
