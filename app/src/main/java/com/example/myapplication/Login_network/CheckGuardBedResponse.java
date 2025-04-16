package com.example.myapplication.Login_network;

import com.google.gson.annotations.SerializedName;

public class CheckGuardBedResponse {
    @SerializedName("success")
    private boolean success;
    
    @SerializedName("exists")
    private boolean exists;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("row")
    private String row;
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isExists() {
        return exists;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getRow() {
        return row;
    }
} 