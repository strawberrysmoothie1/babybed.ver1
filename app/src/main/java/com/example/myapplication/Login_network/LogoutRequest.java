package com.example.myapplication.Login_network;

import java.util.HashMap;
import java.util.Map;

public class LogoutRequest {
    private String gdID;
    
    public LogoutRequest(String gdID) {
        this.gdID = gdID;
    }
    
    public String getGdID() {
        return gdID;
    }
    
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("gdID", gdID);
        return map;
    }
}
