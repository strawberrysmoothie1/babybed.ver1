package com.example.myapplication.item;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.AddBedActivity;
import com.example.myapplication.Login_network.AddGuardBedResponse;
import com.example.myapplication.Login_network.CheckBedInfoResponse;
import com.example.myapplication.Login_network.CheckGuardBedResponse;
import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.model.BedDisplay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BedAdapter extends RecyclerView.Adapter<BedAdapter.BedViewHolder> {
    private Context context;
    private List<BedDisplay> bedDisplayList;
    private Random random = new Random();
    private String currentUserId;
    private com.example.myapplication.Login_network.LoginService loginService;

    public BedAdapter(Context context, List<BedDisplay> bedDisplayList, String currentUserId,
                      com.example.myapplication.Login_network.LoginService loginService) {
        this.context = context;
        this.bedDisplayList = bedDisplayList;
        this.currentUserId = currentUserId;
        this.loginService = loginService;
    }

    @Override
    public BedViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bed, parent, false);
        return new BedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(BedViewHolder holder, int position) {
        BedDisplay bed = bedDisplayList.get(position);
        holder.tvDesignation.setText(bed.getDesignation());

        // "침대추가" 항목 처리
        if ("침대추가".equals(bed.getDesignation())) {
            holder.ivBedImage.setImageResource(R.drawable.addbed);
            holder.btnSetting.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (context instanceof AddBedActivity) {
                    ((AddBedActivity) context).scrollToPosition(holder.getAdapterPosition());
                }
                new Handler().postDelayed(() -> showAddBedDialog(), 300);
            });
        } else {
            int[] images = {R.drawable.babybed1, R.drawable.babybed2, R.drawable.babybed3};
            int index = random.nextInt(images.length);
            holder.ivBedImage.setImageResource(images[index]);
            holder.btnSetting.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(v -> {
                if (context instanceof AddBedActivity) {
                    ((AddBedActivity) context).scrollToPosition(holder.getAdapterPosition());
                    
                    // 침대 버튼 클릭 시 MainActivity로 이동하고 침대 명칭 전달
                    if (!"침대추가".equals(bed.getDesignation())) {
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra("bedDesignation", bed.getDesignation());
                        intent.putExtra("bedID", bed.getBedID());
                        context.startActivity(intent);
                    }
                }
            });

            // 설정 버튼 클릭 시 팝업 메뉴 표시
            holder.btnSetting.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, holder.btnSetting);
                popup.getMenu().add("침대 삭제");
                popup.getMenu().add("순서 변경");
                popup.getMenu().add("명칭 변경");
                // 로그인 사용자가 해당 침대의 보호자라면 "임시보호자 추가" 옵션 추가
                if ("guardian".equals(bed.getUserRole())) {
                    popup.getMenu().add("임시보호자 추가");
                }
                popup.setOnMenuItemClickListener(item -> {
                    String title = item.getTitle().toString();
                    if ("침대 삭제".equals(title)) {
                        int pos = holder.getAdapterPosition();
                        showDeleteDialog(bed, pos);
                        return true;
                    } else if ("순서 변경".equals(title)) {
                        showOrderChangeDialog(bed);
                        return true;
                    } else if ("명칭 변경".equals(title)) {
                        showDesignationChangeDialog(bed);
                        return true;
                    } else if ("임시보호자 추가".equals(title)) {
                        showAddTempGuardianDialog(bed);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }

    private void showAddBedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("침대 추가");
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_bed, null);
        builder.setView(dialogView);
        builder.setNegativeButton("취소", (dialog, which) -> dialog.cancel());
        builder.setPositiveButton("확인", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String currentText = dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString();
            if ("확인".equals(currentText)) {
                EditText etSerial = dialogView.findViewById(R.id.etBedSerial);
                String serialInput = etSerial.getText().toString().trim();
                if (serialInput.isEmpty()) {
                    Toast.makeText(context, "일련번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("serialNumber", serialInput);
                Call<CheckBedInfoResponse> callApi = loginService.checkBedInfo(requestBody);
                callApi.enqueue(new Callback<CheckBedInfoResponse>() {
                    @Override
                    public void onResponse(Call<CheckBedInfoResponse> call, Response<CheckBedInfoResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            String foundBedID = response.body().getBedID();
                            etSerial.setVisibility(View.GONE);
                            EditText etDesignation = dialogView.findViewById(R.id.etBedDesignation);
                            etDesignation.setVisibility(View.VISIBLE);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("추가");
                            dialogView.setTag(foundBedID);
                        } else {
                            Toast.makeText(context, "침대 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    }
                    @Override
                    public void onFailure(Call<CheckBedInfoResponse> call, Throwable t) {
                        Toast.makeText(context, "서버 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });
            } else if ("추가".equals(currentText)) {
                EditText etDesignation = dialogView.findViewById(R.id.etBedDesignation);
                String designationInput = etDesignation.getText().toString().trim();
                if (designationInput.isEmpty()) {
                    Toast.makeText(context, "침대 명칭을 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String bedID = (String) dialogView.getTag();
                if (bedID == null || bedID.isEmpty()) {
                    Toast.makeText(context, "유효하지 않은 침대 정보입니다.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return;
                }
                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("gdID", currentUserId);
                requestBody.put("bedID", bedID);
                requestBody.put("designation", designationInput);
                requestBody.put("period", "");
                Call<AddGuardBedResponse> callAdd = loginService.addGuardBed(requestBody);
                callAdd.enqueue(new Callback<AddGuardBedResponse>() {
                    @Override
                    public void onResponse(Call<AddGuardBedResponse> call, Response<AddGuardBedResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            Toast.makeText(context, "침대가 추가되었습니다.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            if (context instanceof AddBedActivity) {
                                ((AddBedActivity) context).recreate();
                            }
                        } else {
                            String errMsg = (response.body() != null) ? response.body().getMessage() : "알 수 없는 오류";
                            Toast.makeText(context, "침대 추가 실패: " + errMsg, Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<AddGuardBedResponse> call, Throwable t) {
                        Toast.makeText(context, "침대 추가 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showDeleteDialog(BedDisplay bed, int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("침대 삭제");
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_bed, null);
        builder.setView(dialogView);
        builder.setPositiveButton("삭제", (dialog, which) -> {
            EditText etPassword = dialogView.findViewById(R.id.etDeletePassword);
            String password = etPassword.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(context, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            deleteBed(bed.getBedID(), password, pos);
        });
        builder.setNegativeButton("취소", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void deleteBed(String bedID, String password, int pos) {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("gdID", currentUserId);
        requestBody.put("bedID", bedID);
        requestBody.put("password", password);
        Call<Void> call = loginService.deleteBed(requestBody);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(context, "삭제 성공", Toast.LENGTH_SHORT).show();
                    bedDisplayList.remove(pos);
                    notifyItemRemoved(pos);
                } else {
                    Toast.makeText(context, "삭제 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(context, "삭제 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return bedDisplayList.size();
    }

    public BedDisplay getBedDisplayAt(int position) {
        return bedDisplayList.get(position);
    }

    // 임시보호자 추가 다이얼로그 표시
    private void showAddTempGuardianDialog(BedDisplay bed) {
        String initialMessage = "'" + bed.getDesignation() + "' 침대에 대한 임시 보호자를 추가합니다.";
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("임시보호자 추가");

        // 메시지와 입력 필드를 담을 LinearLayout 생성
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView messageView = new TextView(context);
        messageView.setText(initialMessage);
        messageView.setTextSize(15);
        messageView.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.hakgyoansim_geurimilgi));
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.bottomMargin = (int)(16 * context.getResources().getDisplayMetrics().density);
        messageView.setLayoutParams(messageParams);
        layout.addView(messageView);

        // 초기 입력: 임시보호자 아이디 입력
        final EditText input = new EditText(context);
        input.setHint("임시보호자 아이디 입력");
        input.setTextSize(14);
        input.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.hakgyoansim_geurimilgi));
        layout.addView(input);

        builder.setView(layout);
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        builder.setPositiveButton("확인", null);
        final AlertDialog dialog = builder.create();
        dialog.show();

        final String[] verifiedGuardianIdHolder = new String[1];

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String btnText = dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString();
            if ("확인".equals(btnText)) {
                String guardianId = input.getText().toString().trim();
                if (guardianId.isEmpty()) {
                    Toast.makeText(context, "아이디를 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 본인 아이디 체크
                if (guardianId.equals(currentUserId)) {
                    Toast.makeText(context, "본인 아이디는 임시보호자로 추가할 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // "다음" 단계일 때: 먼저 GuardianLog 테이블과 GuardBed 테이블 모두 검증하고 통과 시 2단계로
                // 1. GuardianLog 테이블에 해당 아이디가 존재하는지 검사
                Map<String, String> checkParams = new HashMap<>();
                checkParams.put("id", guardianId);
                Call<com.example.myapplication.RegisterActivity.CheckDuplicateResponse> checkCall = 
                    loginService.checkDuplicate(checkParams);
                
                checkCall.enqueue(new Callback<com.example.myapplication.RegisterActivity.CheckDuplicateResponse>() {
                    @Override
                    public void onResponse(Call<com.example.myapplication.RegisterActivity.CheckDuplicateResponse> call, 
                                           Response<com.example.myapplication.RegisterActivity.CheckDuplicateResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            boolean available = response.body().isAvailable();
                            // available이 true면 사용 가능한 아이디 (즉, 존재하지 않음)
                            if (available) {
                                Toast.makeText(context, "해당 아이디가 존재하지 않습니다. 가입된 사용자만 임시보호자로 추가할 수 있습니다.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            
                            // 2. GuardBed 테이블에서 이미 보호중인 침대인지 검사
                            Map<String, String> guardBedParams = new HashMap<>();
                            guardBedParams.put("gdID", guardianId);
                            guardBedParams.put("bedID", bed.getBedID());
                            Call<CheckGuardBedResponse> guardBedCall = loginService.checkGuardBed(guardBedParams);
                            
                            guardBedCall.enqueue(new Callback<CheckGuardBedResponse>() {
                                @Override
                                public void onResponse(Call<CheckGuardBedResponse> call, Response<CheckGuardBedResponse> response) {
                                    if (response.isSuccessful() && response.body() != null) {
                                        Log.d("CheckGuardBed", "Raw response: " + response.raw().toString());
                                        if (response.body().isExists()) {
                                            Toast.makeText(context, "이미 보호중인 아이디 입니다.", Toast.LENGTH_SHORT).show();
                                        } else {
                                            // 모든 검증 통과: 2단계로 전환
                                            verifiedGuardianIdHolder[0] = guardianId;
                                            messageView.setText("임시보호기간을 선택하세요.");
                                            // 제거: 기존 입력 EditText 제거하고 달력 추가
                                            layout.removeView(input);
                                            // 미니 달력 뷰 추가
                                            MiniCalendarView miniCalendarView = new MiniCalendarView(context);
                                            miniCalendarView.setId(R.id.miniCalendarView);
                                            layout.addView(miniCalendarView);
                                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("추가");
                                        }
                                    } else {
                                        Toast.makeText(context, "검증 실패 (GuardBed): 응답이 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                                @Override
                                public void onFailure(Call<CheckGuardBedResponse> call, Throwable t) {
                                    Toast.makeText(context, "검증 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            Toast.makeText(context, "검증 실패 (GuardianLog): 응답이 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<com.example.myapplication.RegisterActivity.CheckDuplicateResponse> call, Throwable t) {
                        Toast.makeText(context, "검증 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } else if ("추가".equals(btnText)) {
                MiniCalendarView miniCalendarView = (MiniCalendarView) layout.findViewById(R.id.miniCalendarView);
                String selectedDate = miniCalendarView.getSelectedDate();
                if (selectedDate.isEmpty()) {
                    Toast.makeText(context, "임시보호기간을 선택하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 임시보호자 추가 API 호출
                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("gdID", verifiedGuardianIdHolder[0]);  // 임시보호자 ID를 gdID로 설정
                requestBody.put("bedID", bed.getBedID());              // 침대 ID
                requestBody.put("designation", "!" + currentUserId);    // 요청자 ID 앞에 ! 표시 추가
                requestBody.put("period", selectedDate);               // 임시보호 종료일
                requestBody.put("requestedBy", currentUserId);         // 요청자 ID를 별도 파라미터로 전송
                
                Call<AddGuardBedResponse> callAdd = loginService.addGuardBed(requestBody);
                callAdd.enqueue(new Callback<AddGuardBedResponse>() {
                    @Override
                    public void onResponse(Call<AddGuardBedResponse> call, Response<AddGuardBedResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            Toast.makeText(context, "임시보호자 요청이 전송되었습니다.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            if (context instanceof AddBedActivity) {
                                ((AddBedActivity) context).recreate();
                            }
                        } else {
                            String errMsg;
                            if (response.code() == 409) {
                                errMsg = "이미 해당 사용자에게 침대 요청을 보냈습니다.";
                            } else if (response.body() != null) {
                                errMsg = response.body().getMessage();
                            } else {
                                errMsg = "서버 응답 오류 (코드: " + response.code() + ")";
                            }
                            Toast.makeText(context, "임시보호자 추가 실패: " + errMsg, Toast.LENGTH_SHORT).show();
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<AddGuardBedResponse> call, Throwable t) {
                        Toast.makeText(context, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // 침대 순서 변경 다이얼로그 표시
    private void showOrderChangeDialog(BedDisplay bed) {
        // 현재 사용자의 모든 침대 목록 가져오기 (침대추가 항목 제외)
        List<BedDisplay> userBeds = new ArrayList<>();
        for (BedDisplay b : bedDisplayList) {
            if (!"침대추가".equals(b.getDesignation())) {
                userBeds.add(b);
            }
        }
        
        if (userBeds.isEmpty()) {
            Toast.makeText(context, "순서를 변경할 침대가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 현재 침대의 인덱스 찾기
        int currentIndex = IntStream.range(0, userBeds.size()).filter(i -> userBeds.get(i).getBedID().equals(bed.getBedID())).findFirst().orElse(-1);

        if (currentIndex == -1) {
            Toast.makeText(context, "침대 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 다이얼로그 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("침대 순서 변경");
        
        // 레이아웃 설정
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        
        // 안내 메시지
        TextView messageView = new TextView(context);
        messageView.setText("'" + bed.getDesignation() + "' 침대의 순서를 변경합니다.");
        messageView.setTextSize(16);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.bottomMargin = (int)(16 * context.getResources().getDisplayMetrics().density);
        messageView.setLayoutParams(messageParams);
        layout.addView(messageView);
        
        // 현재 순서 표시
        TextView currentOrderView = new TextView(context);
        currentOrderView.setText("현재 순서: " + (currentIndex + 1));
        currentOrderView.setTextSize(14);
        LinearLayout.LayoutParams currentOrderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        currentOrderParams.bottomMargin = (int)(16 * context.getResources().getDisplayMetrics().density);
        currentOrderView.setLayoutParams(currentOrderParams);
        layout.addView(currentOrderView);
        
        // 순서 선택을 위한 NumberPicker
        NumberPicker numberPicker = new NumberPicker(context);
        numberPicker.setMinValue(1);
        numberPicker.setMaxValue(userBeds.size());
        numberPicker.setValue(currentIndex + 1); // 1부터 시작하는 인덱스로 설정
        layout.addView(numberPicker);
        
        builder.setView(layout);
        
        // 확인 버튼
        builder.setPositiveButton("적용", (dialog, which) -> {
            int newPosition = numberPicker.getValue() - 1; // 0부터 시작하는 인덱스로 변환
            
            // 순서가 변경된 경우에만 처리
            if (newPosition != currentIndex) {
                updateBedOrder(bed, currentIndex, newPosition, userBeds);
            }
        });
        
        // 취소 버튼
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        
        builder.show();
    }
    
    // 침대 순서 업데이트
    private void updateBedOrder(BedDisplay bed, int oldPosition, int newPosition, List<BedDisplay> userBeds) {
        // 1. 기존 순서대로 침대 목록 복사
        List<BedDisplay> reorderedBeds = new ArrayList<>(userBeds);
        
        // 2. 이동할 침대 제거
        BedDisplay movingBed = reorderedBeds.remove(oldPosition);
        
        // 3. 새 위치에 침대 삽입
        reorderedBeds.add(newPosition, movingBed);
        
        // 4. 로컬에도 저장 (서버 오류 시를 대비)
        try {
            SharedPreferences preferences = context.getSharedPreferences("BedOrder", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            
            for (int i = 0; i < reorderedBeds.size(); i++) {
                BedDisplay currentBed = reorderedBeds.get(i);
                editor.putInt("order_" + currentBed.getBedID(), i + 1);
            }
            editor.apply();
        } catch (Exception e) {
            Log.e("BedAdapter", "로컬 저장 오류: " + e.getMessage());
        }
        
        // 서버에 침대 순서 업데이트 요청
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("gdID", currentUserId);
        requestBody.put("bedID", bed.getBedID());
        requestBody.put("oldPosition", oldPosition + 1); // 1부터 시작하는 인덱스로 변환
        requestBody.put("newPosition", newPosition + 1); // 1부터 시작하는 인덱스로 변환
        
        // 모든 침대 ID와 순서 정보 추가
        List<Map<String, Object>> bedOrders = new ArrayList<>();
        
        // 재정렬된 침대 목록에 순서 부여
        for (int i = 0; i < reorderedBeds.size(); i++) {
            Map<String, Object> bedOrder = new HashMap<>();
            bedOrder.put("bedID", reorderedBeds.get(i).getBedID());
            bedOrder.put("order", i + 1); // 1부터 시작하는 순서
            bedOrders.add(bedOrder);
        }
        
        requestBody.put("bedOrders", bedOrders);
        
        // 서버 API 호출
        Call<Map<String, Object>> call = loginService.updateBedOrder(requestBody);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    boolean success = (boolean) response.body().get("success");
                    if (success) {
                        Toast.makeText(context, "침대 순서가 변경되었습니다.", Toast.LENGTH_SHORT).show();
                        
                        // 화면 새로고침
                        if (context instanceof AddBedActivity) {
                            ((AddBedActivity) context).recreate();
                        }
                    } else {
                        String message = (String) response.body().get("message");
                        Toast.makeText(context, "순서 변경 실패: " + message, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "서버 응답 오류", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(context, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // 침대 명칭 변경 다이얼로그 표시
    private void showDesignationChangeDialog(BedDisplay bed) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("침대 명칭 변경");
        
        // 레이아웃 설정
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        
        // 안내 메시지
        TextView messageView = new TextView(context);
        messageView.setText("'" + bed.getDesignation() + "' 침대의 명칭을 변경합니다.");
        messageView.setTextSize(16);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.bottomMargin = (int)(16 * context.getResources().getDisplayMetrics().density);
        messageView.setLayoutParams(messageParams);
        layout.addView(messageView);
        
        // 명칭 입력 필드
        final EditText input = new EditText(context);
        input.setHint("새 침대 명칭 입력");
        input.setText(bed.getDesignation()); // 현재 명칭을 기본값으로 설정
        input.setSelection(input.getText().length()); // 커서를 끝으로 이동
        layout.addView(input);
        
        builder.setView(layout);
        
        // 확인 버튼
        builder.setPositiveButton("적용", null); // 나중에 설정
        
        // 취소 버튼
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // 확인 버튼 클릭 리스너 설정 (다이얼로그가 자동으로 닫히지 않도록)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newDesignation = input.getText().toString().trim();
            
            // 입력 검증
            if (newDesignation.isEmpty()) {
                Toast.makeText(context, "침대 명칭을 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 현재 명칭과 동일한 경우 무시
            if (newDesignation.equals(bed.getDesignation())) {
                dialog.dismiss();
                return;
            }
            
            // 서버에 침대 명칭 업데이트 요청
            updateBedDesignation(bed, newDesignation, dialog);
        });
    }
    
    // 침대 명칭 업데이트
    private void updateBedDesignation(BedDisplay bed, String newDesignation, AlertDialog dialog) {
        // 서버에 침대 명칭 업데이트 요청
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("gdID", currentUserId);
        requestBody.put("bedID", bed.getBedID());
        requestBody.put("designation", newDesignation);
        
        // 서버 API 호출
        Call<Map<String, Object>> call = loginService.updateBedDesignation(requestBody);
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    boolean success = (boolean) response.body().get("success");
                    if (success) {
                        dialog.dismiss();
                        Toast.makeText(context, "침대 명칭이 변경되었습니다.", Toast.LENGTH_SHORT).show();
                        
                        // 화면 새로고침
                        if (context instanceof AddBedActivity) {
                            ((AddBedActivity) context).recreate();
                        }
                    } else {
                        String message = (String) response.body().get("message");
                        Toast.makeText(context, "명칭 변경 실패: " + message, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "서버 응답 오류", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(context, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static class BedViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBedImage;
        TextView tvDesignation;
        ImageButton btnSetting;

        public BedViewHolder(View itemView) {
            super(itemView);
            ivBedImage = itemView.findViewById(R.id.ivBedImage);
            tvDesignation = itemView.findViewById(R.id.tvDesignation);
            btnSetting = itemView.findViewById(R.id.btnSetting);
        }
    }
}
