package com.example.myapplication.model;

public class BedDisplay {
    private String bedID;
    private String designation;
    private int guardianCount;
    private int tempCount;
    private String userRole; // "guardian" 또는 "temp" (현재 사용자의 역할, 없으면 빈 문자열)
    private String serialNumber; // 보호자일 경우
    private String period; // 임시보호자일 경우
    private int remainingDays; // 임시보호자일 경우
    private int bedOrder; // 침대 순서

    // 기본 생성자 추가
    public BedDisplay() {
    }

    public BedDisplay(String bedID, String designation, int guardianCount, int tempCount,
                      String userRole, String serialNumber, String period, int remainingDays) {
        this.bedID = bedID;
        this.designation = designation;
        this.guardianCount = guardianCount;
        this.tempCount = tempCount;
        this.userRole = userRole;
        this.serialNumber = serialNumber;
        this.period = period;
        this.remainingDays = remainingDays;
        this.bedOrder = 0; // 기본값 설정
    }
    // Getters and setters...
    public String getBedID() { return bedID; }
    public void setBedID(String bedID) { this.bedID = bedID; }
    
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    
    public int getGuardianCount() { return guardianCount; }
    public void setGuardianCount(int guardianCount) { this.guardianCount = guardianCount; }
    
    public int getTempCount() { return tempCount; }
    public void setTempCount(int tempCount) { this.tempCount = tempCount; }
    
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    
    public int getRemainingDays() { return remainingDays; }
    public void setRemainingDays(int remainingDays) { this.remainingDays = remainingDays; }
    
    public int getBedOrder() { return bedOrder; }
    public void setBedOrder(int bedOrder) { this.bedOrder = bedOrder; }
}
