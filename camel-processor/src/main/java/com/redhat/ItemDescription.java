package com.redhat;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "ITEM_DESCRIPTION")
public class ItemDescription extends PanacheEntityBase{

    @Id
    public String id;
    public String description;
    
}
