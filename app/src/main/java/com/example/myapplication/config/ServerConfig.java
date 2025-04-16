package com.example.myapplication.config;

public class ServerConfig {
    // 서버 기본 주소
    private static final String BASE_URL = "http://192.168.0.76:5000/";
    
    // 서버 주소 가져오기
    public static String getBaseUrl() {
        return BASE_URL;
    }
} 