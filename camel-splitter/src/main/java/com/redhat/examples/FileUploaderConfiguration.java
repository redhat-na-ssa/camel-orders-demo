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

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FileUploaderConfiguration extends RouteBuilder {
  
  @Inject
  private SplitterProperties props;
  
  @Override
  public void configure() throws Exception {
    
    rest("/camel/files")
      .post("/")
        .consumes("multipart/form-data")
        .produces("text/plain")
        .bindingMode(RestBindingMode.off)
        .to("direct:fileUpload")
    ;
    
    from("direct:fileUpload")
      .log(LoggingLevel.INFO, "Uploading file... ")

      // TO-DO:  Determine why Camel is not automatically setting first attachment to body of message
      .process(new SetFirstAttachmentAsByteArrayInBody())
      //.unmarshal().mimeMultipart()
      //.convertBodyTo(byte[].class)
      
      .setHeader(Exchange.FILE_NAME, simple("upload-${exchangeId}.xml"))
      .toF("file:%s", props.dir())
      .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
      .setBody(header(Exchange.FILE_NAME))
    ;
  }


}

class SetFirstAttachmentAsByteArrayInBody implements Processor {

  private static final Logger log = Logger.getLogger(SetFirstAttachmentAsByteArrayInBody.class);

  @Override
  public void process(Exchange e) throws Exception{
    Map<String, Object> headers = e.getIn().getHeaders();
    for(Map.Entry<String, Object> entry : headers.entrySet()) {
      log.debugv("header: key = {0} ; value = {1}", entry.getKey(), entry.getValue());
    }
  
    AttachmentMessage attMsg = e.getIn(AttachmentMessage.class);
    Set<String> aNames = attMsg.getAttachmentNames();
    for(String aName : aNames){
      log.debugv("attachment name = "+aName);
    }
    log.debug("exchange body = "+e.getIn().getBody());
  
    Map<String, Attachment> aMap = attMsg.getAttachmentObjects();
    if(aMap == null || (aMap.size() < 1))
        throw new Exception("Message must include at least 1 attachment");

    Object[] aObjs = aMap.values().toArray();
    InputStream iStream = ((Attachment)aObjs[0]).getDataHandler().getInputStream();
    e.getIn().setBody(iStream.readAllBytes());
  }

}

