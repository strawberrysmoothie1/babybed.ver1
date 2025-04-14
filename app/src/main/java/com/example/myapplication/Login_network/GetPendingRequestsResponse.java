package com.example.myapplication.Login_network;

import com.example.myapplication.model.TempGuardianRequest;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GetPendingRequestsResponse {
    @SerializedName("result")
    private boolean result;            // true 또는 false
    
    @SerializedName("message")
    private String message;           // 응답 메시지
    
    @SerializedName("requests")
    private List<TempGuardianRequest> requests;    // 요청 목록
    
    // Getter 및 Setter
    public boolean getResult() {
        return result;
    }
    
    public String getMessage() {
        return message != null ? message : "";
    }
    
    public List<TempGuardianRequest> getRequests() {
        return requests;
    }
    
    @Override
    public String toString() {
        return "GetPendingRequestsResponse{" +
                "result=" + result +
                ", message='" + message + '\'' +
                ", requests=" + (requests != null ? requests.size() : "null") +
                '}';
    }
} 