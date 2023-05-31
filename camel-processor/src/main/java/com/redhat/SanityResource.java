package com.redhat;

import java.util.Map;
import java.util.Properties;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

public class SanityResource {


    @GET
    @Path("/propsAndEnvVars")
    @Produces(MediaType.TEXT_PLAIN)
    public String getPropsAndEnvVars() {
        StringBuilder output = new StringBuilder("SystemProperties");
        Properties props = System.getProperties();
        for(Object key : props.keySet()){
            output.append("\n\t").append(key).append(" : ").append(props.get(key));
        }
        output.append("Environment Variables");
        Map<String, String> envs = System.getenv();
        for(Map.Entry<String, String> entry : envs.entrySet()){
            output.append("\n\t").append(entry.getKey()).append(" : ").append(entry.getValue());
        }
        return output.toString();
    }
    
}
