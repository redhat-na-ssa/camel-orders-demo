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

import java.util.Random;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(CamelConfiguration.class);

  private static Random randomObj = new Random();

  @Inject
  private SplitterProperties props;

  @Inject
  CamelContext context;

  @PostConstruct
  void start() {
    log.info("start() setting camel breadcrumb configs");
    context.setUseMDCLogging(true);
    context.setUseBreadcrumb(true);
  }

  @Override
  public void configure() throws Exception {

    fromF("file:%s?delete=true&readLock=fileLock", props.dir())

      .process( e -> {
        /*
         * https://people.apache.org/~dkulp/camel/mdc-logging.html
         *    When enabled Camel will enrich the Camel Message by adding a header to it with the key breadcrumbId containing the id. 
         *    Camel will use the messageId if no existing breadcrumbId was found in the message.
         * 
         * https://camel.apache.org/components/3.20.x/eips/message.html
         *    By default, the message uses the same id as Exchange.getExchangeId as messages are associated with the Exchange
         * 
         * Subsequently, the following generates a new random Id as the breadcrumb that will propogate to all downstream camel routes
         */
        e.getIn().setHeader(Exchange.BREADCRUMB_ID, Integer.toString(randomObj.nextInt()));
      })

      .log(LoggingLevel.INFO, "Picked up orders file: [${headers.CamelFileName}]")
      .split(xpath("/orders/order"))
        .log(LoggingLevel.INFO, "Sending order: [${body}]")
        .to(ExchangePattern.InOnly, "amqp:queue:raw?useMessageIDAsCorrelationID=true")
      .end()
    ;
  }
}
