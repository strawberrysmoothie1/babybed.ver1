package com.example.myapplication.config;

public class ServerConfig {
    // 서버 기본 주소
    private static final String BASE_URL = "https://a7bd-218-159-71-14.ngrok-free.app/";
    
    // 서버 주소 가져오기
    public static String getBaseUrl() {
        return BASE_URL;
    }
} 