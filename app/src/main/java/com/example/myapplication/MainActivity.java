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

import com.example.myapplication.Login_network.GetPendingRequestsResponse;
import com.example.myapplication.Login_network.LoginClient;
import com.example.myapplication.Login_network.LoginService;
import com.example.myapplication.Login_network.LogoutRequest;
import com.example.myapplication.Login_network.LogoutResponse;
import com.example.myapplication.config.ServerConfig;
import com.example.myapplication.model.TempGuardianRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** ① ‘현재 선택한 침대’ 명칭·ID 저장 → 실시간 버튼 누르면 StreamActivity 로 bedID 전달 */
public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private LoginService loginService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("AutoLogin", MODE_PRIVATE);
        loginService = LoginClient.getClient(ServerConfig.getBaseUrl())
                .create(LoginService.class);

        // ───────── 현재 선택된 침대 표시 ─────────
        TextView tvDes = findViewById(R.id.tvDesignation);
        Intent in = getIntent();
        String bedName = in.getStringExtra("bedDesignation");   // AddBedActivity 등에서 전달
        String bedID   = in.getStringExtra("bedID");

        if (bedName != null && !bedName.isEmpty()) {            // 새 선택
            tvDes.setText(bedName + " (" + bedID + ")");
            prefs.edit().putString("designation", bedName)
                    .putString("bedID", bedID).apply();
        } else {                                                // 저장된 값
            bedName = prefs.getString("designation", "");
            bedID   = prefs.getString("bedID", "");
            tvDes.setText(bedName.isEmpty() ? "환영합니다!"
                    : bedName + (bedID.isEmpty() ? "" : " ("+bedID+")"));
        }

        // ───────── 실시간 영상 버튼 ─────────
        Button btnRealTime = findViewById(R.id.btnRealTime);
        String finalBedID = bedID;               // 람다 캡처용
        btnRealTime.setOnClickListener(v -> {
            if (finalBedID == null || finalBedID.isEmpty()) {
                Toast.makeText(this, "먼저 침대를 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, StreamActivity.class);
            i.putExtra("bedID", finalBedID);
            startActivity(i);
        });

        // ───────── 뒤로가기(AddBedActivity) ─────────
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(this, AddBedActivity.class));
            finish();
        });

        // 메뉴 버튼 및 알림 체크
        setupMenuButton();
    }

    /* ──────────────────────────────────────────────────────────
       아래 함수들은 기존 코드와 동일 – 침대 권한 요청 알림, 로그아웃 등
       필요 없는 부분은 자유롭게 정리해도 무방
       ────────────────────────────────────────────────────────── */

    private void setupMenuButton() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(v -> {
            PopupMenu pop = new PopupMenu(MainActivity.this, btnMenu);
            pop.getMenuInflater().inflate(R.menu.menu_main, pop.getMenu());
            pop.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_messages) {
                    startActivity(new Intent(this, MessagesActivity.class));
                    return true;
                } else if (id == R.id.menu_account) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                }
                return false;
            });
            pop.show();
        });
        checkForPendingRequests();
    }

    private void checkForPendingRequests() {
        String userId = prefs.getString("userID", "");
        if (userId.isEmpty()) return;

        Map<String, String> p = new HashMap<>();
        p.put("gdID", userId);

        loginService.getPendingTempGuardianRequests(p)
                .enqueue(new Callback<GetPendingRequestsResponse>() {
                    @Override public void onResponse(Call<GetPendingRequestsResponse> call,
                                                     Response<GetPendingRequestsResponse> res) {
                        if (!res.isSuccessful() || res.body()==null) return;
                        List<TempGuardianRequest> reqs = res.body().getRequests();
                        if (reqs!=null && !reqs.isEmpty()) {
                            ((ImageButton)findViewById(R.id.btnMenu))
                                    .setImageResource(R.drawable.menu_send);
                            Toast.makeText(MainActivity.this,
                                    reqs.size()+"개의 침대 요청이 있습니다.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    @Override public void onFailure(Call<GetPendingRequestsResponse> call, Throwable t){}
                });
    }

    private void logout() { /* ← 기존에 쓰던 로직 그대로 */ }
}
