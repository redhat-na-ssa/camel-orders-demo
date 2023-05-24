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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FileServerConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(FileServerConfiguration.class);
  
  @Inject
  private AggregatorProperties props;
  
  @Override
  public void configure() throws Exception {
    
    rest("/files")
      .get("/")
        .produces("text/plain")
        .bindingMode(RestBindingMode.off)
        .to("direct:listFiles")
      .get("/{filename}")
        .produces("application/json")
        .bindingMode(RestBindingMode.off)
        .to("direct:getFile")
    ;
    
    from("direct:listFiles")
      .log(LoggingLevel.INFO, String.format("Listing files in [%s]", props.dir()))
      .process((Exchange exchange) -> { 
        if (Files.exists(Paths.get(props.dir()))) {
          Stream<Path> walk = null;
          try {
            walk = Files.walk(Paths.get(props.dir()));
            List<String> files = walk.filter(Files::isRegularFile).map(x -> x.getFileName().toString()).collect(Collectors.toList());
            exchange.getIn().setBody(String.join("\n", files));
          }finally{
            if(walk != null)
              walk.close();
          } 
        }
      })
      .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
    ;
    
    from("direct:getFile")
      .log(LoggingLevel.INFO, "Getting file [${header.filename}]")
      .routingSlip(simpleF("language:constant:file:%s${headers.filename}?contentCache=false", props.dir()))
      .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    ;
  }
}
