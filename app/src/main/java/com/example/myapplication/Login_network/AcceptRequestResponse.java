package com.example.myapplication.Login_network;

import com.google.gson.annotations.SerializedName;

public class AcceptRequestResponse {
    @SerializedName("result")
    private boolean result;    // true 또는 false
    
    @SerializedName("message")
    private String message;   // 응답 메시지
    
    // Getter 및 Setter
    public boolean isSuccess() {
        return result;
    }
    
    public String getMessage() {
        return message != null ? message : "";
    }
    
    @Override
    public String toString() {
        return "AcceptRequestResponse{" +
                "result=" + result +
                ", message='" + message + '\'' +
                '}';
    }
} 