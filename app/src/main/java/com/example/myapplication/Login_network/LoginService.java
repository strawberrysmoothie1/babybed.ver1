package com.example.myapplication.Login_network;

import com.example.myapplication.RegisterActivity;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface LoginService {

    @POST("/api/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("/api/register")
    Call<RegisterResponse> register(@Body RegisterRequest request);

    @POST("/api/logout")
    Call<LogoutResponse> logout(@Body Map<String, String> body);

    @POST("/api/checkDuplicate")
    Call<RegisterActivity.CheckDuplicateResponse> checkDuplicate(@Body Map<String, String> body);

    @POST("/api/checkMyBed")
    Call<CheckMyBedResponse> checkMyBed(@Body Map<String, String> body);

    @POST("/api/checkBedInfo")
    Call<CheckBedInfoResponse> checkBedInfo(@Body Map<String, String> body);

    @POST("/api/addGuardBed")
    Call<AddGuardBedResponse> addGuardBed(@Body Map<String, String> body);

    @POST("/api/deleteBed")
    Call<Void> deleteBed(@Body Map<String, String> body);

    @POST("/api/calcBedCounts")
    Call<CalcBedCountsResponse> calcBedCounts(@Body Map<String, String> body);

    @POST("/api/getPendingTempGuardianRequests")
    Call<GetPendingRequestsResponse> getPendingTempGuardianRequests(@Body Map<String, String> params);

    @POST("/api/acceptTempGuardianRequest")
    Call<AcceptRequestResponse> acceptRequest(@Body Map<String, String> params);

    @POST("/api/rejectTempGuardianRequest")
    Call<RejectRequestResponse> rejectRequest(@Body Map<String, String> params);

    @POST("/api/checkGuardBed")
    Call<CheckGuardBedResponse> checkGuardBed(@Body Map<String, String> params);

    @POST("/api/updateBedDesignation")
    Call<Map<String, Object>> updateBedDesignation(@Body Map<String, String> params);

    @POST("/api/updateBedOrder")
    Call<Map<String, Object>> updateBedOrder(@Body Map<String, Object> params);

    @POST("/api/deleteAccount")
    Call<DeleteAccountResponse> deleteAccount(@Body Map<String, String> params);
}
