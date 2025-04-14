package com.example.myapplication.Login_network;

public class LoginResponse {
    private boolean success;
    private String message;
    private String gdID;
    private String joinDate;
    private String FCM_toKen;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
    
    public String getGdID() {
        return gdID;
    }
    
    public String getJoinDate() {
        return joinDate;
    }
    
    public String getFCM_toKen() {
        return FCM_toKen;
    }
}
