package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.Login_network.CheckBedInfoResponse;
import com.example.myapplication.Login_network.GetPendingRequestsResponse;
import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.Login_network.LogoutRequest;
import com.example.myapplication.Login_network.LogoutResponse;
import com.example.myapplication.config.ServerConfig;
import com.example.myapplication.model.BedDisplay;
import com.example.myapplication.model.TempGuardianRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private LoginService loginService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("AutoLogin", MODE_PRIVATE);
        loginService = LoginClient.getClient(ServerConfig.getBaseUrl()).create(LoginService.class);

        // 뒤로가기 버튼 설정
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            // AddBedActivity로 이동
            Intent intent = new Intent(MainActivity.this, AddBedActivity.class);
            startActivity(intent);
            finish(); // 현재 액티비티 종료
        });

        // Intent에서 전달받은 침대 명칭이 있으면 표시
        TextView tvDesignation = findViewById(R.id.tvDesignation);
        Intent intent = getIntent();
        String bedDesignation = intent.getStringExtra("bedDesignation");
        String bedID = intent.getStringExtra("bedID");
        
        if (bedDesignation != null && !bedDesignation.isEmpty()) {
            // 침대 명칭과 ID를 함께 표시
            tvDesignation.setText(bedDesignation + " (" + bedID + ")");
            // SharedPreferences에도 저장
            preferences.edit().putString("designation", bedDesignation).apply();
            preferences.edit().putString("bedID", bedID).apply();
        } else {
            // Intent에 없으면 SharedPreferences에서 가져오기
            String savedDesignation = preferences.getString("designation", "");
            String savedBedID = preferences.getString("bedID", "");
            if (!savedDesignation.isEmpty()) {
                if (!savedBedID.isEmpty()) {
                    tvDesignation.setText(savedDesignation + " (" + savedBedID + ")");
                } else {
                    tvDesignation.setText(savedDesignation);
                }
            } else {
                tvDesignation.setText("환영합니다!");
            }
        }

        // 메뉴 버튼 설정 - 로그인 후 침대 요청이 있는지 확인하고 아이콘 표시
        setupMenuButton();

        // 실시간 영상 버튼
        Button btnRealTime = findViewById(R.id.btnRealTime);
        btnRealTime.setOnClickListener(view -> {
            Intent streamIntent = new Intent(MainActivity.this, StreamActivity.class);
            startActivity(streamIntent);
        });

        // 이벤트 로그 버튼
        Button btnEventLog = findViewById(R.id.btnEventLog);
        btnEventLog.setOnClickListener(view ->
                Toast.makeText(this, "이벤트 로그 준비 중입니다.", Toast.LENGTH_SHORT).show()
        );
    }

    private void logout() {
        String userId = preferences.getString("userID", "");
        
        // 로컬 로그아웃 처리 함수
        Runnable localLogout = () -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("autoLogin", false);
            editor.remove("userID");
            editor.remove("autoLoginID");
            editor.remove("password");
            editor.apply();
            Toast.makeText(MainActivity.this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, LogActivity.class));
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
                Toast.makeText(MainActivity.this, "서버 연결 실패, 로컬에서 로그아웃됩니다.", Toast.LENGTH_SHORT).show();
                localLogout.run();
            }
        });
    }

    // 메뉴 버튼 설정 - 로그인 후 침대 요청이 있는지 확인하고 아이콘 표시
    private void setupMenuButton() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, btnMenu);
            popupMenu.getMenuInflater().inflate(R.menu.menu_main, popupMenu.getMenu());
            
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_messages) {
                    // 메시지 화면으로 이동
                    Intent intent = new Intent(MainActivity.this, MessagesActivity.class);
                    startActivity(intent);
                    return true;
                } else if (itemId == R.id.menu_account) {
                    // 설정 화면으로 이동
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });

        // 메시지 알림 아이콘 설정 (로그인 후 침대 요청 체크)
        checkForPendingRequests();
    }
    
    // 침대 권한 요청 여부를 확인하고 알림 표시
    private void checkForPendingRequests() {
        Log.d("MainActivity", "침대 권한 요청 확인 시작");
        String userId = preferences.getString("userID", "");
        if (userId.isEmpty()) {
            Log.d("MainActivity", "사용자 ID가 없습니다.");
            return;
        }
        
        // null 체크 추가
        if (loginService == null) {
            Log.e("MainActivity", "loginService가 초기화되지 않았습니다");
            return;
        }
        
        Log.d("MainActivity", "침대 권한 요청 API 호출: 사용자 ID=" + userId);
        
        // Map으로 변환하여 API 호출
        Map<String, String> params = new HashMap<>();
        params.put("gdID", userId);
        
        // getPendingTempGuardianRequests API 호출
        Call<GetPendingRequestsResponse> call = loginService.getPendingTempGuardianRequests(params);
        call.enqueue(new Callback<GetPendingRequestsResponse>() {
            @Override
            public void onResponse(Call<GetPendingRequestsResponse> call, Response<GetPendingRequestsResponse> response) {
                Log.d("MainActivity", "API 응답 수신: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    GetPendingRequestsResponse pendingResponse = response.body();
                    
                    // designation이 느낌표(!)로 시작하는 요청만 처리
                    if (pendingResponse.getResult()) {
                        List<TempGuardianRequest> requests = pendingResponse.getRequests();
                        
                        if (requests != null && !requests.isEmpty()) {
                            // 요청 중인 침대 확인
                            int requestCount = requests.size();
                            
                            Log.d("MainActivity", "침대 권한 요청 " + requestCount + "개 발견");
                            
                            // 메시지 아이콘에 알림 표시
                            ImageButton btnMenu = findViewById(R.id.btnMenu);
                            if (requestCount > 0) {
                                // 알림 표시 (여기서는 간단히 아이콘 변경만 함)
                                btnMenu.setImageResource(R.drawable.menu_send);
                                
                                // 알림 토스트 표시
                                Toast.makeText(MainActivity.this, 
                                    requestCount + "개의 침대 권한 요청이 있습니다. 메시지함을 확인하세요.", 
                                    Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Log.d("MainActivity", "요청 목록이 비어 있습니다.");
                        }
                    } else {
                        Log.d("MainActivity", "요청 목록 결과가 false입니다.");
                    }
                } else {
                    Log.e("MainActivity", "응답은 성공했지만 본문이 null이거나 응답 실패: " 
                        + (response.body() == null ? "body null" : "code " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<GetPendingRequestsResponse> call, Throwable t) {
                // 네트워크 오류 무시
                Log.e("MainActivity", "권한 요청 조회 실패", t);
            }
        });
    }
}
