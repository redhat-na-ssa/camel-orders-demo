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

import com.redhat.examples.json.ProcessedOrder;
import com.redhat.examples.xml.RawOrder;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.util.Map;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(CamelConfiguration.class);

  @Inject
  OrderMapping orderMapping;

  @Inject
  CamelContext context;

  @PostConstruct
  void start() {

    // https://camel.apache.org/manual/registry.html
    Map<String, DataSource> dSources = context.getRegistry().findByTypeWithName(javax.sql.DataSource.class);
    if(dSources.size() == 0)
      throw new RuntimeException("No datasources in camel context registry");
    
    for(Entry<String,DataSource> dSource: dSources.entrySet()){
      log.infov("Datasource found in camel registry:  name =", dSource.getKey());

    }
  }
  
  private AggregationStrategy descriptionEnrichmentStrategy() {
    return (Exchange original, Exchange resource) -> {
      if (resource.getIn().getBody() != null) {
        original.getIn().getBody(ProcessedOrder.class).setDescription(resource.getIn().getBody(String.class));
      }
      return original;
    };
  }
  
  @Override
  public void configure() throws Exception {
    from("amqp:queue:raw?acknowledgementModeName=CLIENT_ACKNOWLEDGE")
      .log(LoggingLevel.INFO, "Picked up raw order: [${body}]")
      .unmarshal().jaxb("com.redhat.examples.xml")
      .process(e -> {
        RawOrder raw = (RawOrder)e.getIn().getBody();
        e.getIn().setBody(orderMapping.rawToProcessed(raw));
      })
      .enrich()
        .constant("direct:fetchDescription")
        .aggregationStrategy(descriptionEnrichmentStrategy())
      .end()
      .marshal().json(JsonLibrary.Jackson, false)
      .log(LoggingLevel.INFO, "Sending processed order: [${body}]")
      .to(ExchangePattern.InOnly, "amqp:queue:processed")
    ;
    
    from("direct:fetchDescription")
      .to("sql:select description from ITEM_DESCRIPTION where id=:#${body.item}?outputType=SelectOne")
    ;
  }
}
