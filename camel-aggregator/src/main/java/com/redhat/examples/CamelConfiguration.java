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

import java.util.ArrayList;
import java.util.Map;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.builder.AggregationStrategies;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(CamelConfiguration.class);
  
  @Inject
  AggregatorProperties props;

  
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
    
    from("amqp:queue:processed?acknowledgementModeName=CLIENT_ACKNOWLEDGE")
      .log(LoggingLevel.INFO, "Picked up processed order: [${body}]")
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
      .aggregate()
        .simple("${body[customer]}")
        .aggregationStrategy(this.orderAggregationStrategy())
        .completionTimeout(5000L)
        .completionSize(10)
          .log(LoggingLevel.INFO, "Completing aggregate order: [${exchangeProperty.CamelAggregatedCorrelationKey}]")
          //.transform().groovy("['orders':request.body]")
          .marshal().json(JsonLibrary.Jackson, true)
          .setHeader("CurrentTimeMillis", method(System.class, "currentTimeMillis"))
          .to(ExchangePattern.InOnly, String.format("file:%s?fileName=order-${exchangeProperty.CamelAggregatedCorrelationKey}-${header.CurrentTimeMillis}.json", props.dir()))
      .end()
    ;
  }
}
