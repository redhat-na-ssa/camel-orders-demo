package com.redhat.examples;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.redhat.examples.json.ProcessedOrder;
import com.redhat.examples.xml.RawOrder;

@Mapper(componentModel = "cdi")
public interface OrderMapping {

    @Mapping(source="customerId", target="customer")
    @Mapping(source="itemId", target="item")
    @Mapping(source="quantity", target="quantity")
    ProcessedOrder rawToProcessed(RawOrder rawOrder);

    @Mapping(source="customer", target="customerId")
    @Mapping(source="item", target="itemId")
    @Mapping(source="quantity", target="quantity")
    RawOrder processedToRaw(ProcessedOrder processedOrder);
    
}
