package com.example.myapplication.Login_network;

import com.example.myapplication.model.BedDisplay;

import java.util.List;

public class CheckBedInfoResponse {
    public String result;
    public List<BedDisplay> bedList;
    private boolean success;
    private String message;
    private String bedID;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getBedID() { return bedID; }
}
