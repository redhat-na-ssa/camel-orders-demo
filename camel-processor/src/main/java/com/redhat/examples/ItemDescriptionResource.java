package com.redhat.examples;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;


@Path("/itemDescription")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ItemDescriptionResource {

    @GET
    public List<ItemDescription> list() {
        return ItemDescription.listAll();
    }

    @GET
    @Path("/{id}")
    public ItemDescription get(String id){
        return ItemDescription.findById(id);
    }

    @GET
    @Path("/description/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getDescription(String id) {
        ItemDescription iDescription = ItemDescription.findById(id);
        return iDescription.getDescription();
    }
    
}
