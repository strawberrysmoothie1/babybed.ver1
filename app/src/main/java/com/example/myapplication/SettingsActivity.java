package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.Login_network.LogoutRequest;
import com.example.myapplication.Login_network.LogoutResponse;
import com.example.myapplication.Login_network.DeleteAccountResponse;
import com.example.myapplication.config.ServerConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences preferences;
    private LoginService loginService;
    private TextView tvUserID, tvJoinDate;
    private Switch switchNightMode, switchNotification;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 툴바 설정
        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // SharedPreferences 및 LoginService 초기화
        preferences = getSharedPreferences("AutoLogin", MODE_PRIVATE);
        loginService = LoginClient.getClient(ServerConfig.getBaseUrl()).create(LoginService.class);

        // UI 요소 초기화
        tvUserID = findViewById(R.id.tvUserID);
        tvJoinDate = findViewById(R.id.tvJoinDate);
        switchNightMode = findViewById(R.id.switchNightMode);
        switchNotification = findViewById(R.id.switchNotification);

        // 사용자 정보 표시
        String userID = preferences.getString("userID", "");
        String joinDate = preferences.getString("joinDate", "");
        tvUserID.setText(userID);
        tvJoinDate.setText(joinDate);

        // 설정 상태 복원
        boolean isNightMode = preferences.getBoolean("nightMode", false);
        boolean isNotificationEnabled = preferences.getBoolean("notification", true);

        switchNightMode.setChecked(isNightMode);
        switchNotification.setChecked(isNotificationEnabled);

        // 다크 모드 스위치 리스너
        switchNightMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferences.edit().putBoolean("nightMode", isChecked).apply();
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
            }
        });

        // 알림 스위치 리스너
        switchNotification.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferences.edit().putBoolean("notification", isChecked).apply();
                Toast.makeText(SettingsActivity.this,
                        isChecked ? "알림이 활성화되었습니다." : "알림이 비활성화되었습니다.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // 로그아웃 버튼 리스너
        findViewById(R.id.btnLogout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("로그아웃")
                        .setMessage("정말 로그아웃하시겠습니까?")
                        .setPositiveButton("예", (dialog, which) -> logout())
                        .setNegativeButton("아니오", null)
                        .show();
            }
        });

        // 계정 삭제 버튼 리스너
        findViewById(R.id.btnDeleteAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 비밀번호 입력 다이얼로그 표시
                AlertDialog.Builder passwordDialog = new AlertDialog.Builder(SettingsActivity.this);
                passwordDialog.setTitle("계정 삭제 확인");
                passwordDialog.setMessage("계정을 삭제하려면 비밀번호를 입력하세요. 이 작업은 되돌릴 수 없습니다.");
                
                // 비밀번호 입력 필드 추가
                final EditText etPassword = new EditText(SettingsActivity.this);
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                etPassword.setHint("비밀번호");
                passwordDialog.setView(etPassword);
                
                passwordDialog.setPositiveButton("삭제", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(SettingsActivity.this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                    } else {
                        // 계정 삭제 API 호출
                        deleteAccount(password);
                    }
                });
                
                passwordDialog.setNegativeButton("취소", null);
                passwordDialog.show();
            }
        });

        // 앱 정보 버튼 리스너
        findViewById(R.id.btnAppInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("앱 정보")
                        .setMessage("아기침대 앱 버전 1.0\n\n이 앱은 침대 공유 및 관리를 위한 앱입니다.\n개발자: 캡스톤 팀")
                        .setPositiveButton("확인", null)
                        .show();
            }
        });
    }

    private void logout() {
        String userId = preferences.getString("userID", "");
        
        // 로컬 로그아웃 처리 함수
        Runnable localLogout = () -> {
            // 모든 로그인 정보 삭제
            preferences.edit()
                .remove("userID")
                .remove("autoLoginID")
                .remove("password")
                .putBoolean("autoLogin", false)
                .apply();

            Toast.makeText(SettingsActivity.this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(SettingsActivity.this, LogActivity.class));
            finish();
        };
        
        // 사용자 ID가 없는 경우 로컬 로그아웃만 진행
        if (userId.isEmpty()) {
            localLogout.run();
            return;
        }

        LogoutRequest logoutRequest = new LogoutRequest(userId);
        Call<LogoutResponse> call = loginService.logout(logoutRequest.toMap());
        call.enqueue(new Callback<LogoutResponse>() {
            @Override
            public void onResponse(Call<LogoutResponse> call, Response<LogoutResponse> response) {
                // 성공 여부와 상관없이 로컬 로그아웃 진행
                localLogout.run();
            }

            @Override
            public void onFailure(Call<LogoutResponse> call, Throwable t) {
                // 네트워크 오류 시에도 로컬 로그아웃 진행
                Toast.makeText(SettingsActivity.this, "서버 연결 실패, 로컬에서 로그아웃됩니다.", Toast.LENGTH_SHORT).show();
                localLogout.run();
            }
        });
    }

    // 계정 삭제 함수
    private void deleteAccount(String password) {
        String userId = preferences.getString("userID", "");
        if (userId.isEmpty()) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 진행 상황 다이얼로그 표시
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setMessage("계정 삭제 중...")
                .setCancelable(false)
                .show();
        
        // API 호출 파라미터 설정
        Map<String, String> params = new HashMap<>();
        params.put("gdID", userId);
        params.put("password", password);
        
        // API 호출
        Call<DeleteAccountResponse> call = loginService.deleteAccount(params);
        call.enqueue(new Callback<DeleteAccountResponse>() {
            @Override
            public void onResponse(Call<DeleteAccountResponse> call, Response<DeleteAccountResponse> response) {
                progressDialog.dismiss();
                
                if (response.isSuccessful() && response.body() != null) {
                    DeleteAccountResponse result = response.body();
                    if (result.isSuccess()) {
                        Toast.makeText(SettingsActivity.this, "계정이 성공적으로 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        
                        // 모든 로그인 정보 삭제
                        preferences.edit()
                            .remove("userID")
                            .remove("autoLoginID")
                            .remove("password")
                            .remove("joinDate")
                            .putBoolean("autoLogin", false)
                            .apply();
                        
                        // 로그인 화면으로 이동
                        Intent intent = new Intent(SettingsActivity.this, LogActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(SettingsActivity.this, result.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "알 수 없는 오류";
                        if (response.code() == 403) {
                            Toast.makeText(SettingsActivity.this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SettingsActivity.this, "오류: " + errorBody, Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        Toast.makeText(SettingsActivity.this, "오류 발생: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
            
            @Override
            public void onFailure(Call<DeleteAccountResponse> call, Throwable t) {
                progressDialog.dismiss();
                Toast.makeText(SettingsActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
} 