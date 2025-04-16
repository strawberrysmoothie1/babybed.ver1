package com.example.myapplication.Login_network;

public class LogoutResponse {
    private boolean success;
    private String message;
    public boolean isSuccess() {
        return success;
    }
    public String getMessage() {
        return message;
    }

    public boolean getSuccess() {
        return success;
    }
    
    public String getResult() {
        return success ? "true" : "false";
    }
}
