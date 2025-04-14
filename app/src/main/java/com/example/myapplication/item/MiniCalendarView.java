package com.example.myapplication.item;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MiniCalendarView extends LinearLayout {
    private TextView tvMonthYear;
    private GridLayout gridDays;
    private Calendar currentCalendar;
    private Button btnPrevMonth, btnNextMonth;
    private String selectedDate = "";
    private Button selectedButton = null;

    public MiniCalendarView(Context context) {
        super(context);
        init(context);
    }

    public MiniCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        
        // 현재 날짜 설정
        currentCalendar = Calendar.getInstance();
        
        // 헤더 레이아웃 (월 표시 및 이전/다음 버튼)
        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(HORIZONTAL);
        headerLayout.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        
        // 이전 월 버튼
        btnPrevMonth = new Button(context);
        btnPrevMonth.setText("◀");
        btnPrevMonth.setLayoutParams(new LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1));
        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        headerLayout.addView(btnPrevMonth);
        
        // 월/년 표시 텍스트뷰
        tvMonthYear = new TextView(context);
        tvMonthYear.setLayoutParams(new LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 2));
        tvMonthYear.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        tvMonthYear.setTextSize(16);
        headerLayout.addView(tvMonthYear);
        
        // 다음 월 버튼
        btnNextMonth = new Button(context);
        btnNextMonth.setText("▶");
        btnNextMonth.setLayoutParams(new LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1));
        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });
        headerLayout.addView(btnNextMonth);
        
        addView(headerLayout);
        
        // 요일 헤더 추가
        LinearLayout daysHeader = new LinearLayout(context);
        daysHeader.setOrientation(HORIZONTAL);
        daysHeader.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        
        String[] weekDays = {"일", "월", "화", "수", "목", "금", "토"};
        for (String day : weekDays) {
            TextView tvDay = new TextView(context);
            tvDay.setText(day);
            tvDay.setLayoutParams(new LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1));
            tvDay.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            tvDay.setPadding(0, 10, 0, 10);
            daysHeader.addView(tvDay);
        }
        
        addView(daysHeader);
        
        // 날짜 그리드
        gridDays = new GridLayout(context);
        gridDays.setColumnCount(7);
        addView(gridDays);
        
        // 초기 달력 표시
        updateCalendar();
    }
    
    private void updateCalendar() {
        // 월/년 텍스트 업데이트
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentCalendar.getTime()));
        
        // 그리드 초기화
        gridDays.removeAllViews();
        
        // 현재 월의 1일로 설정
        Calendar calendar = (Calendar) currentCalendar.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        
        // 1일의 요일 구하기 (일요일=1, 토요일=7)
        int firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK);
        
        // 현재 월의 마지막 날짜
        int maxDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        // 오늘 날짜
        Calendar today = Calendar.getInstance();
        
        // 빈 셀 추가 (1일 이전)
        for (int i = 1; i < firstDayOfMonth; i++) {
            addEmptyCell();
        }
        
        // 날짜 버튼 추가
        for (int day = 1; day <= maxDaysInMonth; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);
            
            // 오늘 이전 날짜는 비활성화
            boolean isPastDate = calendar.before(today);
            
            addDateButton(day, calendar, isPastDate);
        }
    }
    
    private void addEmptyCell() {
        View emptyView = new View(getContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 100;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        emptyView.setLayoutParams(params);
        gridDays.addView(emptyView);
    }
    
    private void addDateButton(int day, Calendar calendar, boolean isPastDate) {
        Button btnDay = new Button(getContext());
        btnDay.setText(String.valueOf(day));
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        btnDay.setLayoutParams(params);
        
        // 과거 날짜는 비활성화
        btnDay.setEnabled(!isPastDate);
        
        if (isPastDate) {
            btnDay.setAlpha(0.5f);
        }
        
        // 날짜 선택 이벤트
        final String dateStr = formatDate(calendar.getTime());
        btnDay.setOnClickListener(v -> {
            if (selectedButton != null) {
                selectedButton.setBackgroundColor(Color.TRANSPARENT);
            }
            selectedButton = btnDay;
            selectedDate = dateStr;
            btnDay.setBackgroundColor(Color.LTGRAY);
        });
        
        gridDays.addView(btnDay);
    }
    
    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(date);
    }
    
    public String getSelectedDate() {
        return selectedDate;
    }
} 