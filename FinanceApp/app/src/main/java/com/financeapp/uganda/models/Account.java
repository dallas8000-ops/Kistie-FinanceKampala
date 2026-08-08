package com.financeapp.uganda.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts")
public class Account {
    @PrimaryKey
    private int id;
    private String code;
    private String name;
    private String type; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    private boolean isSystem;

    public Account() {}

    public Account(int id, String code, String name, String type, boolean isSystem) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.isSystem = isSystem;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isSystem() { return isSystem; }
    public void setSystem(boolean system) { isSystem = system; }
}
