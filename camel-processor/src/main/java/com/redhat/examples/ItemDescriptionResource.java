package com.redhat.examples;

import java.util.List;

import com.redhat.ItemDescription;

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

    
}
