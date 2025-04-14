package com.example.myapplication.model;

import com.google.gson.annotations.SerializedName;

public class TempGuardianRequest {
    @SerializedName("bedID")
    private String bedID;
    
    @SerializedName("period")
    private String period;
    
    @SerializedName("bed_order")
    private String bedOrder;
    
    @SerializedName("requesterID")
    private String requesterID;
    
    @SerializedName("requesterDesignation")
    private String requesterDesignation;
    
    // Getters and Setters
    public String getBedID() {
        return bedID;
    }
    
    public void setBedID(String bedID) {
        this.bedID = bedID;
    }
    
    public String getPeriod() {
        return period;
    }
    
    public void setPeriod(String period) {
        this.period = period;
    }
    
    public String getBedOrder() {
        return bedOrder;
    }
    
    public void setBedOrder(String bedOrder) {
        this.bedOrder = bedOrder;
    }
    
    public String getRequesterID() {
        return requesterID;
    }
    
    public void setRequesterID(String requesterID) {
        this.requesterID = requesterID;
    }
    
    public String getRequesterDesignation() {
        return requesterDesignation;
    }
    
    public void setRequesterDesignation(String requesterDesignation) {
        this.requesterDesignation = requesterDesignation;
    }
    
    @Override
    public String toString() {
        return "TempGuardianRequest{" +
                "bedID='" + bedID + '\'' +
                ", period='" + period + '\'' +
                ", bedOrder='" + bedOrder + '\'' +
                ", requesterID='" + requesterID + '\'' +
                ", requesterDesignation='" + requesterDesignation + '\'' +
                '}';
    }
} 