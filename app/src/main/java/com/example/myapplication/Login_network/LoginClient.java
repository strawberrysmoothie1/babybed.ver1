package com.example.myapplication.Login_network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginClient {

    private static Retrofit retrofit = null;
    private static String currentBaseUrl = ""; // 현재 사용 중인 baseUrl 저장

    public static Retrofit getClient(String baseUrl) {
        // 새로운 baseUrl이 들어오면 Retrofit 인스턴스를 새로 생성
        if (retrofit == null || !currentBaseUrl.equals(baseUrl)) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            currentBaseUrl = baseUrl; // 현재 사용 중인 baseUrl 업데이트
        }
        return retrofit;
    }
}
