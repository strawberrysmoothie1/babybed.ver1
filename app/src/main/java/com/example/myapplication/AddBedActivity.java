package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.Login_network.CalcBedCountsResponse;
import com.example.myapplication.Login_network.CheckMyBedResponse;
import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.Login_network.LogoutRequest;
import com.example.myapplication.Login_network.LogoutResponse;
import com.example.myapplication.item.BedAdapter;
import com.example.myapplication.model.BedCount;
import com.example.myapplication.model.BedDisplay;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddBedActivity extends AppCompatActivity {
    private SharedPreferences preferences;
    private LoginService loginService;
    private RecyclerView recyclerViewBeds;
    private BedAdapter bedAdapter;
    private TextView tvInfoCounts, tvInfoAdditional;
    
    // calcBedCounts 결과 저장
    private List<BedCount> calcBedCountsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addbed);

        // 인텐트에서 침대 요청 여부 확인
        boolean hasBedRequests = getIntent().getBooleanExtra("hasBedRequests", false);
        if (hasBedRequests) {
            showBedRequestDialog();
        }

        // 메뉴 버튼 설정
        setupMenuButton();

        preferences = getSharedPreferences("AutoLogin", MODE_PRIVATE);
        loginService = LoginClient.getClient("http://10.0.2.2:5000/").create(LoginService.class);

        Toolbar toolbar = findViewById(R.id.toolbarAddBed);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerViewBeds = findViewById(R.id.recyclerViewBeds);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerViewBeds.setLayoutManager(layoutManager);

        // 좌우 패딩 설정 (아이템 크기 160dp 기준)
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int itemWidthPx = (int) (160 * dm.density);
        int screenWidth = dm.widthPixels;
        int sidePadding = (screenWidth - itemWidthPx) / 2;
        recyclerViewBeds.setPadding(sidePadding, 0, sidePadding, 0);
        recyclerViewBeds.setClipToPadding(false);

        LinearSnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerViewBeds);

        recyclerViewBeds.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                // 스크롤이 멈추면 중앙 아이템에 맞춰 info frame 업데이트
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centerView = snapHelper.findSnapView(layoutManager);
                    if (centerView != null) {
                        int pos = layoutManager.getPosition(centerView);
                        updateFixedInfo(pos);
                    }
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int centerX = recyclerView.getWidth() / 2;
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    int childCenterX = (recyclerView.getChildAt(i).getLeft() + recyclerView.getChildAt(i).getRight()) / 2;
                    int distanceFromCenter = Math.abs(centerX - childCenterX);
                    float scale = 1.0f - (distanceFromCenter / (float) centerX) * 0.5f;
                    if (scale < 0.5f) scale = 0.5f;
                    recyclerView.getChildAt(i).setScaleX(scale);
                    recyclerView.getChildAt(i).setScaleY(scale);
                }
            }
        });

        // 침대 정보 로드
        loadBedData();

        // 고정 정보 프레임 초기화
        tvInfoCounts = findViewById(R.id.tvInfoCounts);
        tvInfoAdditional = findViewById(R.id.tvInfoAdditional);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 화면에 진입할 때마다 침대 목록 새로고침
        loadBedData();
    }

    // 침대 데이터 로드 메서드
    private void loadBedData() {
        String userId = preferences.getString("userID", "");
        if (userId.isEmpty()) {
            Toast.makeText(this, "사용자 정보가 없습니다. 로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 침대 정보 조회 API 호출
        CheckList checkList = new CheckList(loginService);
        checkList.checkMyBed(userId, new Callback<CheckMyBedResponse>() {
            @Override
            public void onResponse(Call<CheckMyBedResponse> call, Response<CheckMyBedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CheckMyBedResponse myBedResponse = response.body();
                    if (myBedResponse.isSuccess()) {
                        List<List<String>> bedInfo = myBedResponse.getBedInfo();
                        List<BedDisplay> bedDisplayList = groupBedData(bedInfo, userId);
                        bedAdapter = new BedAdapter(AddBedActivity.this, bedDisplayList, userId, loginService);
                        recyclerViewBeds.setAdapter(bedAdapter);
                        
                        // 첫 번째 아이템으로 스크롤
                        if (bedDisplayList.size() > 0) {
                            recyclerViewBeds.scrollToPosition(0);
                            updateFixedInfo(0);
                        }
                        
                        // 침대 개수 계산 API 호출
                        calcBedCounts();
                    } else {
                        Toast.makeText(AddBedActivity.this, "침대 정보 조회 실패", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AddBedActivity.this, "서버 응답 오류", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CheckMyBedResponse> call, Throwable t) {
                Toast.makeText(AddBedActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 침대 개수 계산 API 호출 메서드
    private void calcBedCounts() {
        String userId = preferences.getString("userID", "");
        if (userId.isEmpty()) {
            return;
        }
        
        // 새 calcBedCounts 호출 (백엔드에서는 GdID를 무시함)
        Map<String, String> calcRequest = new HashMap<>();
        calcRequest.put("gdID", userId);
        loginService.calcBedCounts(calcRequest).enqueue(new Callback<CalcBedCountsResponse>() {
            @Override
            public void onResponse(Call<CalcBedCountsResponse> call, Response<CalcBedCountsResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    calcBedCountsList = response.body().getBedCounts();
                    
                    // 현재 선택된 아이템의 정보 업데이트
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerViewBeds.getLayoutManager();
                    if (layoutManager != null) {
                        View centerView = new LinearSnapHelper().findSnapView(layoutManager);
                        if (centerView != null) {
                            int pos = layoutManager.getPosition(centerView);
                            updateFixedInfo(pos);
                        }
                    }
                } else {
                    Toast.makeText(AddBedActivity.this, "침대 개수 계산 실패", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<CalcBedCountsResponse> call, Throwable t) {
                Toast.makeText(AddBedActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 메뉴 버튼 설정
    private void setupMenuButton() {
        ImageButton btnMenu = findViewById(R.id.btnMenuAddBed);
        btnMenu.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(AddBedActivity.this, btnMenu);
            popupMenu.getMenuInflater().inflate(R.menu.menu_main, popupMenu.getMenu());
            
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menu_messages) {
                    // 메시지 화면으로 이동
                    Intent intent = new Intent(AddBedActivity.this, MessagesActivity.class);
                    startActivity(intent);
                    return true;
                } else if (itemId == R.id.menu_account) {
                    // 설정 화면으로 이동
                    Intent intent = new Intent(AddBedActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
    }

    // rawData를 BedDisplay 객체 리스트로 그룹화 (checkMyBed 응답을 기반으로)
    private List<BedDisplay> groupBedData(List<List<String>> rawData, String currentUserId) {
        Map<String, List<List<String>>> groupMap = new HashMap<>();
        if (rawData != null) {
            for (List<String> row : rawData) {
                String bedID = row.get(1);
                if (!groupMap.containsKey(bedID)) {
                    groupMap.put(bedID, new ArrayList<>());
                }
                groupMap.get(bedID).add(row);
            }
        }
        List<BedDisplay> list = new ArrayList<>();
        
        for (String bedID : groupMap.keySet()) {
            List<List<String>> groupRows = groupMap.get(bedID);
            int guardianCount = 0;
            int tempCount = 0;
            String userRole = "";
            String designation = groupRows.get(0).get(2);
            
            // designation이 !로 시작하는 경우 (아직 수락되지 않은 임시보호자 요청) 건너뛰기
            if (designation != null && designation.startsWith("!")) {
                continue;
            }
            
            String serialNumber = groupRows.get(0).get(5);
            String periodForDisplay = "";
            int remainingDays = 0;
            int bedOrder = 0;
            
            try {
                // bed_order 값 가져오기
                String bedOrderStr = groupRows.get(0).get(4);
                if (bedOrderStr != null && !bedOrderStr.isEmpty()) {
                    bedOrder = Integer.parseInt(bedOrderStr);
                }
            } catch (NumberFormatException e) {
                bedOrder = 0;
            }
            
            for (List<String> row : groupRows) {
                String period = row.get(3);
                if (period == null || period.isEmpty() || period.equalsIgnoreCase("null")) {
                    guardianCount++;
                    if (row.get(0).equals(currentUserId)) {
                        userRole = "guardian";
                    }
                } else {
                    tempCount++;
                    if (row.get(0).equals(currentUserId)) {
                        userRole = "temp";
                        periodForDisplay = period;
                        
                        // 서버에서 전달받은 남은 일수 정보 (리스트 크기가 7 이상인 경우)
                        if (row.size() >= 7 && row.get(6) != null && !row.get(6).isEmpty()) {
                            try {
                                remainingDays = Integer.parseInt(row.get(6));
                            } catch (NumberFormatException e) {
                                // 기본값 유지
                                remainingDays = 0;
                            }
                        } else {
                            // 서버에서 정보가 없는 경우 로컬 계산 사용 (기존 코드)
                            DateTimeFormatter formatter = null;
                            LocalDate today = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                                today = LocalDate.now();
                                try {
                                    LocalDate periodDate = null;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        periodDate = LocalDate.parse(period, formatter);
                                        remainingDays = (int) ChronoUnit.DAYS.between(today, periodDate);
                                    }
                                } catch (Exception e) {
                                    remainingDays = 0;
                                }
                            }
                        }
                    }
                }
            }
            BedDisplay bedDisplay = new BedDisplay(bedID, designation, guardianCount, tempCount, userRole, serialNumber, periodForDisplay, remainingDays);
            bedDisplay.setBedOrder(bedOrder); // bed_order 값 설정
            list.add(bedDisplay);
        }
        
        // 로컬 저장소에서 침대 순서 가져오기
        SharedPreferences orderPrefs = getSharedPreferences("BedOrder", MODE_PRIVATE);
        for (BedDisplay bed : list) {
            // 로컬에 저장된 순서가 있으면 해당 순서 사용
            int localOrder = orderPrefs.getInt("order_" + bed.getBedID(), -1);
            if (localOrder > 0) {
                bed.setBedOrder(localOrder);
            }
        }
        
        // bed_order 값으로 정렬
        list.sort((bed1, bed2) -> Integer.compare(bed1.getBedOrder(), bed2.getBedOrder()));
        
        // 마지막에 항상 "침대추가" 항목 추가
        list.add(new BedDisplay("", "침대추가", 0, 0, "", "", "", 0));
        return list;
    }

    // 중앙 아이템에 따른 고정 정보 프레임 업데이트
    private void updateFixedInfo(int pos) {
        if (bedAdapter != null && pos >= 0 && pos < bedAdapter.getItemCount()) {
            BedDisplay bed = bedAdapter.getBedDisplayAt(pos);
            View fixedFrame = findViewById(R.id.fixedInfoFrame);
            TextView tvRoleIndicator = findViewById(R.id.tvRoleIndicator);
            if ("침대추가".equals(bed.getDesignation())) {
                fixedFrame.setVisibility(View.GONE);
            } else {
                fixedFrame.setVisibility(View.VISIBLE);
                if ("temp".equals(bed.getUserRole())) {
                    tvRoleIndicator.setText("임시보호 침대");
                    tvRoleIndicator.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    tvRoleIndicator.setVisibility(View.VISIBLE);
                } else if ("guardian".equals(bed.getUserRole())) {
                    tvRoleIndicator.setText("보호 침대");
                    tvRoleIndicator.setTextColor(getResources().getColor(android.R.color.black));
                    tvRoleIndicator.setVisibility(View.VISIBLE);
                } else {
                    tvRoleIndicator.setVisibility(View.GONE);
                }

                BedCount matchingCount = null;
                if (calcBedCountsList != null) {
                    for (BedCount bc : calcBedCountsList) {
                        if (bc.getBedID().equals(bed.getBedID())) {
                            matchingCount = bc;
                            break;
                        }
                    }
                }
                if (matchingCount != null) {
                    String countsText = "보호자: " + matchingCount.getGuardianCount() + "/3" + "\n" +
                            "임시보호자: " + matchingCount.getTempCount() + "/5";
                    ((TextView) findViewById(R.id.tvInfoCounts)).setText(countsText);
                } else {
                    ((TextView) findViewById(R.id.tvInfoCounts)).setText("");
                }

                TextView tvAdditional = findViewById(R.id.tvInfoAdditional);
                if ("temp".equals(bed.getUserRole())) {
                    // 항상 서버에서 받은 남은 일수 값을 우선적으로 사용
                    int remainingDays = 0;
                    if (matchingCount != null) {
                        remainingDays = matchingCount.getRemainingDays();
                    } else {
                        // 서버에서 받은 데이터가 없는 경우에만 로컬 계산 값 사용
                        remainingDays = bed.getRemainingDays();
                    }
                    tvAdditional.setText("임시보호까지 " + remainingDays + "일 남음");
                    if (remainingDays <= 3) {
                        tvAdditional.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    } else {
                        tvAdditional.setTextColor(getResources().getColor(android.R.color.black));
                    }
                } else if ("guardian".equals(bed.getUserRole())) {
                    String serial = (matchingCount != null) ? matchingCount.getSerialNumber() : bed.getSerialNumber();
                    tvAdditional.setText("일련번호: " + serial);
                    tvAdditional.setTextColor(getResources().getColor(android.R.color.black));
                } else {
                    tvAdditional.setText("");
                }
            }
        }
    }

    private void logout() {
        String userId = preferences.getString("userID", "");
        if (userId.isEmpty()) {
            Toast.makeText(AddBedActivity.this, "저장된 사용자 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(AddBedActivity.this, LogActivity.class));
            finish();
            return;
        }
        LogoutRequest logoutRequest = new LogoutRequest(userId);
        Call<LogoutResponse> call = loginService.logout(logoutRequest.toMap());
        call.enqueue(new Callback<LogoutResponse>() {
            @Override
            public void onResponse(Call<LogoutResponse> call, Response<LogoutResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(AddBedActivity.this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AddBedActivity.this, "서버 오류: " + response.code(), Toast.LENGTH_SHORT).show();
                }
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("autoLogin", false);
                editor.remove("userID");
                editor.apply();
                startActivity(new Intent(AddBedActivity.this, LogActivity.class));
                finish();
            }
            @Override
            public void onFailure(Call<LogoutResponse> call, Throwable t) {
                Toast.makeText(AddBedActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 침대 요청 다이얼로그 표시
    private void showBedRequestDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("침대 권한 요청")
            .setMessage("새로운 침대 권한 요청이 있습니다.\n메시지함을 확인하시겠습니까?")
            .setPositiveButton("확인", (dialog, which) -> {
                Intent intent = new Intent(AddBedActivity.this, MessagesActivity.class);
                startActivity(intent);
            })
            .setNegativeButton("나중에", null)
            .show();
    }

    // 특정 위치로 스크롤하는 메소드 추가
    public void scrollToPosition(int position) {
        if (recyclerViewBeds != null) {
            recyclerViewBeds.smoothScrollToPosition(position);
        }
    }
}
