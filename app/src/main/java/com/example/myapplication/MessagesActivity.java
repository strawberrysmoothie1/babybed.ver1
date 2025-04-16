package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.Login_network.AcceptRequestResponse;
import com.example.myapplication.Login_network.GetPendingRequestsResponse;
import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.Login_network.RejectRequestResponse;
import com.example.myapplication.item.MessageAdapter;
import com.example.myapplication.model.Message;
import com.example.myapplication.model.TempGuardianRequest;
import com.example.myapplication.config.ServerConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessagesActivity extends AppCompatActivity implements MessageAdapter.OnMessageActionListener {

    private static final String TAG = "MessagesActivity";
    private RecyclerView recyclerViewMessages;
    private TextView tvNoMessages;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;
    private SharedPreferences preferences;
    private LoginService loginService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        // 로그 시작 표시
        Log.d(TAG, "============= MessagesActivity onCreate 시작 =============");

        try {
            // SharedPreferences 및 Retrofit 서비스 초기화 - 가장 먼저 초기화
            preferences = getSharedPreferences("AutoLogin", MODE_PRIVATE);
            loginService = LoginClient.getClient(ServerConfig.getBaseUrl()).create(LoginService.class);
            
            // 사용자 ID 로깅
            String userId = preferences.getString("userID", "");
            Log.d(TAG, "사용자 ID: " + userId);

            // UI 요소 초기화
            recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
            tvNoMessages = findViewById(R.id.tvNoMessages);
            ImageButton btnBack = findViewById(R.id.btnBack);
            ImageButton btnMenu = findViewById(R.id.btnMenuMessages);

            if (recyclerViewMessages == null) {
                Log.e(TAG, "recyclerViewMessages가 null입니다!");
            }
            
            if (tvNoMessages == null) {
                Log.e(TAG, "tvNoMessages가 null입니다!");
            }

            // 뒤로 가기 버튼 클릭 이벤트
            btnBack.setOnClickListener(v -> {
                Log.d(TAG, "뒤로가기 버튼 클릭");
                finish();
            });

            // 메뉴 버튼 설정
            setupMenuButton();

            // RecyclerView 설정
            recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
            messageList = new ArrayList<>();
            messageAdapter = new MessageAdapter(this, messageList, this);
            recyclerViewMessages.setAdapter(messageAdapter);

            // 메시지 데이터 로드
            Log.d(TAG, "loadMessages() 호출 전");
            loadMessages();
            Log.d(TAG, "loadMessages() 호출 후");
            
        } catch (Exception e) {
            Log.e(TAG, "onCreate 중 예외 발생", e);
            Toast.makeText(this, "초기화 중 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        
        Log.d(TAG, "============= MessagesActivity onCreate 종료 =============");
    }

    // 서버에서 실제 데이터를 가져옴
    private void loadMessages() {
        Log.d(TAG, "============= loadMessages() 시작 =============");
        try {
            // 사용자 ID 가져오기
            String userId = preferences.getString("userID", "");
            if (userId.isEmpty()) {
                Log.e(TAG, "사용자 ID가 없습니다. 로그인이 필요합니다.");
                Toast.makeText(this, "사용자 ID가 없습니다. 로그인이 필요합니다.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // API 호출 URL 및 매개변수 로깅 (디버깅용)
            Map<String, String> params = Collections.singletonMap("gdID", userId);
            Log.d(TAG, "API 호출 시작: getPendingTempGuardianRequests - 사용자 ID: " + userId);
            Log.d(TAG, "요청 URL: " + LoginClient.getClient(ServerConfig.getBaseUrl()).baseUrl().toString() + "api/getPendingTempGuardianRequests");
            Log.d(TAG, "요청 매개변수: " + params.toString());
            
            // API 호출 - 디버깅을 위한 자세한 로그 추가
            loginService.getPendingTempGuardianRequests(params).enqueue(new Callback<GetPendingRequestsResponse>() {
                @Override
                public void onResponse(Call<GetPendingRequestsResponse> call, Response<GetPendingRequestsResponse> response) {
                    Log.d(TAG, "API 응답 수신: " + response.code());
                    Log.d(TAG, "API 요청 URL: " + call.request().url());
                    
                    if (response.isSuccessful()) {
                        Log.d(TAG, "API 응답 성공");
                        GetPendingRequestsResponse responseBody = response.body();
                        
                        if (responseBody != null) {
                            Log.d(TAG, "응답 본문: " + responseBody.toString());
                            
                            // 응답 결과 검사 - designation이 느낌표(!)로 시작하는 요청만 표시
                            if (responseBody.getResult() && responseBody.getRequests() != null) {
                                updateMessages(responseBody.getRequests());
                                Log.d(TAG, "메시지 업데이트 완료: " + responseBody.getRequests().size() + "개의 요청");
                            } else {
                                // 요청은 성공했지만 결과가 false인 경우
                                Log.d(TAG, "요청 성공, 하지만 결과: " + responseBody.getResult());
                                showNoMessages();
                            }
                        } else {
                            Log.e(TAG, "응답 본문이 null입니다.");
                            showNoMessages();
                        }
                    } else {
                        try {
                            // 오류 응답 본문 확인
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "오류 본문 없음";
                            Log.e(TAG, "API 응답 실패: " + response.code() + " - " + errorBody);
                        } catch (IOException e) {
                            Log.e(TAG, "오류 응답 읽기 실패", e);
                        }
                        showNoMessages();
                    }
                }

                @Override
                public void onFailure(Call<GetPendingRequestsResponse> call, Throwable t) {
                    Log.e(TAG, "API 호출 실패: " + t.getMessage(), t);
                    Log.e(TAG, "API 실패 상세: " + t.toString() + "\n원인: " + (t.getCause() != null ? t.getCause().toString() : "알 수 없음"));
                    Toast.makeText(MessagesActivity.this, "서버 연결 오류: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    showNoMessages();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "loadMessages 실행 중 예외 발생", e);
            Toast.makeText(this, "메시지 로딩 중 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
            showNoMessages();
        }
        Log.d(TAG, "============= loadMessages() 종료 =============");
    }

    private void showNoMessages() {
        Log.d(TAG, "showNoMessages() 호출됨 - 메시지 없음 표시");
        if (tvNoMessages != null) {
            tvNoMessages.setVisibility(View.VISIBLE);
        } else {
            Log.e(TAG, "tvNoMessages가 null입니다!");
        }
        
        if (recyclerViewMessages != null) {
            recyclerViewMessages.setVisibility(View.GONE);
        } else {
            Log.e(TAG, "recyclerViewMessages가 null입니다!");
        }
    }

    private void updateMessages(List<TempGuardianRequest> requests) {
        Log.d(TAG, "updateMessages() 호출됨: " + requests.size() + "개의 요청");
        
        messageList.clear();
        
        try {
            if (requests.isEmpty()) {
                Log.d(TAG, "요청 목록이 비어 있음");
                showNoMessages();
                return;
            }
            
            for (TempGuardianRequest request : requests) {
                try {
                    String bedId = request.getBedID();
                    String period = request.getPeriod();
                    String requesterID = request.getRequesterID(); // 요청자 ID 가져오기
                    String requesterDesignation = request.getRequesterDesignation(); // 요청자가 지정한 침대 명칭
                    
                    // BedID, 기간을 이용해 Message 객체 생성
                    // designation이 !로 시작하면 임시보호자 요청으로 간주
                    Message message = new Message();
                    message.setId(bedId);
                    message.setMessageType(Message.MessageType.TEMP_GUARDIAN_REQUEST);
                    
                    // 요청 정보 설정
                    String content = "침대 ID: " + bedId;
                    if (period != null && !period.isEmpty()) {
                        content += "\n임시보호 기간: " + period + "까지";
                    }
                    // 요청자 ID와 침대 명칭 정보 추가
                    if (requesterID != null && !requesterID.isEmpty()) {
                        content += "\n요청자: " + requesterID;
                        // 요청자가 지정한 침대 명칭이 있으면 괄호 안에 추가
                        if (requesterDesignation != null && !requesterDesignation.isEmpty()) {
                            content += " (" + requesterDesignation + ")";
                        }
                    }
                    message.setContent(content);
                    message.setTimestamp(new Date()); // 현재 시간으로 설정
                    
                    messageList.add(message);
                    
                    Log.d(TAG, "메시지 추가: " + message.getContent());
                } catch (Exception e) {
                    Log.e(TAG, "요청 처리 중 오류: " + e.getMessage(), e);
                }
            }
            
            // UI 업데이트
            if (messageList.isEmpty()) {
                showNoMessages();
            } else {
                tvNoMessages.setVisibility(View.GONE);
                recyclerViewMessages.setVisibility(View.VISIBLE);
                messageAdapter.notifyDataSetChanged();
                Log.d(TAG, "메시지 어댑터 업데이트됨: " + messageList.size() + "개의 메시지");
            }
        } catch (Exception e) {
            Log.e(TAG, "updateMessages 처리 중 오류: " + e.getMessage(), e);
            showNoMessages();
        }
    }

    @Override
    public void onAccept(Message message, int position) {
        // 침대 관리 권한 요청 수락 로직
        String userId = preferences.getString("userID", "");
        String bedId = message.getId(); // 메시지 ID로 저장된 침대 ID
        
        if (userId.isEmpty() || bedId.isEmpty()) {
            Toast.makeText(this, "사용자 정보 또는 침대 ID가 누락되었습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // null 체크 추가
        if (loginService == null) {
            Log.e(TAG, "loginService가 초기화되지 않았습니다");
            Toast.makeText(this, "서비스 초기화 오류", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 다이얼로그를 표시하여 침대 명칭 입력 받기
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("침대 명칭 설정");
        
        // 레이아웃 설정
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        
        // 안내 메시지
        TextView messageView = new TextView(this);
        messageView.setText("새로 보호할 침대명칭을 정해주세요!");
        messageView.setTextSize(16);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.bottomMargin = (int)(16 * getResources().getDisplayMetrics().density);
        messageView.setLayoutParams(messageParams);
        layout.addView(messageView);
        
        // 침대 명칭 입력 필드
        final EditText input = new EditText(this);
        input.setHint("침대 명칭 입력");
        input.setSelection(input.getText().length()); // 커서를 끝으로 이동
        layout.addView(input);
        
        builder.setView(layout);
        
        // 확인 버튼
        builder.setPositiveButton("확인", null); // 나중에 설정
        
        // 취소 버튼
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // 확인 버튼 클릭 리스너 설정 (다이얼로그가 자동으로 닫히지 않도록)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String designation = input.getText().toString().trim();
            
            // 입력 검증
            if (designation.isEmpty()) {
                Toast.makeText(MessagesActivity.this, "침대 명칭을 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // !로 시작하는 명칭은 사용 불가
            if (designation.startsWith("!")) {
                Toast.makeText(MessagesActivity.this, "!로 시작하는 명칭은 사용할 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 서버에 침대 요청 수락 API 호출
            acceptTempGuardianRequest(userId, bedId, designation, position, dialog);
        });
    }
    
    // 임시 보호자 요청 수락 API 호출 메서드
    private void acceptTempGuardianRequest(String userId, String bedId, String designation, int position, AlertDialog dialog) {
        // GuardBed 테이블에서 현재 사용자의 침대 개수 조회 (bed_order 값 계산용)
        Map<String, String> countParams = new HashMap<>();
        countParams.put("gdID", userId);
        
        // 서버에 침대 요청 수락 API 호출
        Map<String, String> params = new HashMap<>();
        params.put("gdID", userId);
        params.put("bedID", bedId);
        params.put("designation", designation);
        
        Call<AcceptRequestResponse> call = loginService.acceptRequest(params);
        call.enqueue(new Callback<AcceptRequestResponse>() {
            @Override
            public void onResponse(Call<AcceptRequestResponse> call, Response<AcceptRequestResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AcceptRequestResponse acceptResponse = response.body();
                    if (acceptResponse.isSuccess()) {
                        dialog.dismiss(); // 다이얼로그 닫기
                        
                        Toast.makeText(MessagesActivity.this, 
                            "요청을 수락했습니다: " + bedId, Toast.LENGTH_SHORT).show();
                        
                        // 요청이 수락되었으므로 해당 메시지를 목록에서 제거
                        messageList.remove(position);
                        messageAdapter.notifyItemRemoved(position);
                        
                        // 메시지가 없으면 '메시지 없음' 텍스트 표시
                        if (messageList.isEmpty()) {
                            showNoMessages();
                        }
                    } else {
                        Toast.makeText(MessagesActivity.this, 
                            "요청 수락 실패: " + acceptResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MessagesActivity.this, 
                        "서버 응답 오류: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AcceptRequestResponse> call, Throwable t) {
                Log.e(TAG, "요청 수락 API 호출 실패", t);
                Toast.makeText(MessagesActivity.this, 
                    "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onReject(Message message, int position) {
        // 침대 관리 권한 요청 거절 로직
        String userId = preferences.getString("userID", "");
        String bedId = message.getId(); // 메시지 ID로 저장된 침대 ID
        
        if (userId.isEmpty() || bedId.isEmpty()) {
            Toast.makeText(this, "사용자 정보 또는 침대 ID가 누락되었습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // null 체크 추가
        if (loginService == null) {
            Log.e(TAG, "loginService가 초기화되지 않았습니다");
            Toast.makeText(this, "서비스 초기화 오류", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 확인 다이얼로그 표시
        new AlertDialog.Builder(this)
            .setTitle("요청 거절")
            .setMessage("정말로 이 요청을 거절하시겠습니까?")
            .setPositiveButton("예", (dialog, which) -> {
                // 서버에 침대 요청 거절 API 호출
                rejectTempGuardianRequest(userId, bedId, position);
            })
            .setNegativeButton("아니오", null)
            .show();
    }
    
    // 임시 보호자 요청 거절 API 호출 메서드
    private void rejectTempGuardianRequest(String userId, String bedId, int position) {
        // 서버에 침대 요청 거절 API 호출
        Map<String, String> params = new HashMap<>();
        params.put("gdID", userId);
        params.put("bedID", bedId);
        
        Call<RejectRequestResponse> call = loginService.rejectRequest(params);
        call.enqueue(new Callback<RejectRequestResponse>() {
            @Override
            public void onResponse(Call<RejectRequestResponse> call, Response<RejectRequestResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RejectRequestResponse rejectResponse = response.body();
                    if (rejectResponse.isSuccess()) {
                        Toast.makeText(MessagesActivity.this, 
                            "요청을 거절했습니다: " + bedId, Toast.LENGTH_SHORT).show();
                        
                        // 요청이 거절되었으므로 해당 메시지를 목록에서 제거
                        messageList.remove(position);
                        messageAdapter.notifyItemRemoved(position);
                        
                        // 메시지가 없으면 '메시지 없음' 텍스트 표시
                        if (messageList.isEmpty()) {
                            showNoMessages();
                        }
                    } else {
                        Toast.makeText(MessagesActivity.this, 
                            "요청 거절 실패: " + rejectResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MessagesActivity.this, 
                        "서버 응답 오류: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RejectRequestResponse> call, Throwable t) {
                Log.e(TAG, "요청 거절 API 호출 실패", t);
                Toast.makeText(MessagesActivity.this, 
                    "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 메뉴 버튼 설정
    private void setupMenuButton() {
        ImageButton btnMenu = findViewById(R.id.btnMenuMessages);
        btnMenu.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(MessagesActivity.this, btnMenu);
            popupMenu.getMenuInflater().inflate(R.menu.menu_main, popupMenu.getMenu());
            
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_messages) {
                    // 이미 메시지 화면에 있으므로 무시
                    return true;
                } else if (itemId == R.id.menu_account) {
                    // 설정 화면으로 이동
                    Intent intent = new Intent(MessagesActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
    }

    private void logout() {
        // 로그아웃 로직 구현
        // 예: 로그인 정보 제거 및 로그인 화면으로 이동
        preferences.edit()
            .remove("userID")
            .remove("autoLoginID")
            .remove("password")
            .putBoolean("autoLogin", false)
            .apply();
        Intent intent = new Intent(MessagesActivity.this, LogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
} 