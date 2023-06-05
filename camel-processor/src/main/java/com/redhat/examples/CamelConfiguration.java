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
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchResult;
import javax.sql.DataSource;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = Logger.getLogger(CamelConfiguration.class);

  protected static final String MAIL_ATTRIBUTE = "mail";
  protected static final String EMPLOYEE_NUMBER = "employeeNumber";

  @Inject
  OrderMapping orderMapping;

  @Inject
  CamelContext context;

  @ConfigProperty(name = "ldap.url")
  String ldapURL;

  @ConfigProperty(name = "ldap.securityPrincipal")
  String ldapPrincipal;

  @ConfigProperty(name = "ldap.securityCredentials")
  String ldapCredentials;

  @ConfigProperty(name = "quarkus.http.port")
  int itemDescriptionRESTPort;

  @Produces
  @Dependent 
  @Named("ldapserver")
  public DirContext createLdapServer() throws Exception {

    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, this.ldapURL);
    env.put(Context.SECURITY_AUTHENTICATION, "none");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, this.ldapPrincipal);
    env.put(Context.SECURITY_CREDENTIALS, this.ldapCredentials);

    return new InitialDirContext(env);
  }

  @PostConstruct
  void start() throws NamingException {

    // https://camel.apache.org/manual/registry.html
    Map<String, DataSource> dSources = context.getRegistry().findByTypeWithName(javax.sql.DataSource.class);
    if(dSources.size() == 0)
      throw new RuntimeException("No datasources in camel context registry");
    
    for(Entry<String,DataSource> dSource: dSources.entrySet()){
      log.infov("Datasource found in camel registry:  name =", dSource.getKey());
    }

    log.info("start() setting camel breadcrumb configs");
    context.setUseMDCLogging(true);
    context.setUseBreadcrumb(true);

    log.infov("start() {0} , {1}", this.ldapURL, this.ldapPrincipal);

  }

  private AggregationStrategy employeeNumEnrichmentStrategy() {
    return (Exchange original, Exchange resource) -> {
      if (resource.getIn().getBody() != null) {
        String employeeNumber = resource.getIn().getBody(String.class);
        original.getIn().getBody(ProcessedOrder.class).setEmpNum(employeeNumber);
      }
      return original;
    };
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

    onException((CamelOrdersException.class)).process(new Processor() {

      @Override
      public void process(Exchange exchange) throws Exception {
        log.error("App is throwing CamelOrdersException");
        Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        e.printStackTrace();
      }

    }).handled(true);

    onException((NamingException.class)).process(new Processor() {

      @Override
      public void process(Exchange exchange) throws Exception {
        log.error("App is throwing NamingException");

        // https://camel.apache.org/manual/faq/why-is-the-exception-null-when-i-use-onexception.html
        Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        e.printStackTrace();
      }

    }).handled(true);

    from("amqp:queue:raw")
      .log(LoggingLevel.INFO, "[${headers}]")
      .log(LoggingLevel.INFO, "Picked up raw order: [${body}]")
      .unmarshal().jaxb("com.redhat.examples.xml")
      .process(e -> {
        RawOrder raw = (RawOrder)e.getIn().getBody();
        e.getIn().setBody(orderMapping.rawToProcessed(raw));
      })
      .enrich()
        .constant("direct:fetchEmployeeEmail")
        .aggregationStrategy(employeeNumEnrichmentStrategy())
      .end()
      .enrich()
        .constant("direct:fetchDescriptionREST")
        .aggregationStrategy(descriptionEnrichmentStrategy())
      .end()
      .marshal().json(JsonLibrary.Jackson, false)
      .log(LoggingLevel.INFO, "Sending processed order: [${body}]")
      .to(ExchangePattern.InOnly, "amqp:queue:processed")
    ;

    from("direct:fetchEmployeeEmail")
      .process(new Processor() {

        @Override
        public void process(Exchange exchange) throws Exception {

          ProcessedOrder pOrder = (ProcessedOrder)exchange.getIn().getBody();
          String customerId = pOrder.getCustomer();
          StringBuilder ldapFilter = new StringBuilder("(").append(EMPLOYEE_NUMBER).append("=").append(customerId).append(")");
          log.info("Searching ldap for user with following filter: "+ldapFilter.toString());

          // https://camel.apache.org/manual/producertemplate.html#_send_vs_request_methods
          ProducerTemplate template = exchange.getContext().createProducerTemplate();
          Collection results = template.requestBody(
            "ldap:ldapserver?base='ou=People,dc=example,dc=org'",
            ldapFilter.toString(), 
            Collection.class
          );

          log.info("# of employees found in ldap = "+results.size());
          if(results.size() > 0) {
            SearchResult searchResult = (SearchResult) results.toArray()[0];
            Attribute mailAttr = searchResult.getAttributes().get(MAIL_ATTRIBUTE);
            pOrder.setEmail(mailAttr.get(0).toString());
            //log.info("mailAttr = "+mailAttr.get(0).toString());
          }
        }
  
      });
      
    
    from("direct:fetchDescriptionREST")
      .toD("http://localhost:"+itemDescriptionRESTPort+"/itemDescription/description/${body.item}"+"?httpMethod=GET")
    ;
    from("direct:fetchDescription")
      .to("sql:select description from ITEM_DESCRIPTION where id=:#${body.item}?outputType=SelectOne")
    ;
  }
}
