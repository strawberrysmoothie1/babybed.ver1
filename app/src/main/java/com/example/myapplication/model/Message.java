package com.example.myapplication.model;

import java.util.Date;

public class Message {
    // MessageType enum 추가
    public enum MessageType {
        TEMP_GUARDIAN_REQUEST,
        NOTIFICATION,
        SYSTEM
    }
    
    private String id;
    private String title;
    private String content;
    private Date timestamp;
    private String senderName;
    private String type; // 예: "guard_request", "notification", "system", 등
    private boolean requiresAction;
    private TempGuardianRequest request; // 임시 보호자 요청 객체
    private MessageType messageType; // MessageType enum을 저장할 필드

    public Message() {
        this.timestamp = new Date(); // 기본 생성자에서 현재 시간으로 초기화
        this.type = "guard_request"; // 기본 타입
        this.requiresAction = true;  // 기본적으로 액션 필요
        this.senderName = "시스템";   // 기본 발신자
        this.title = "임시 보호자 요청"; // 기본 제목
        this.messageType = MessageType.TEMP_GUARDIAN_REQUEST; // 기본 메시지 타입
    }

    public Message(String id, String title, String content, Date timestamp, String senderName, String type, boolean requiresAction) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.senderName = senderName;
        this.type = type;
        this.requiresAction = requiresAction;
    }

    // MessageType getter와 setter 추가
    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
        // MessageType에 따라 type 문자열 자동 설정
        if (messageType == MessageType.TEMP_GUARDIAN_REQUEST) {
            this.type = "guard_request";
        } else if (messageType == MessageType.NOTIFICATION) {
            this.type = "notification";
        } else if (messageType == MessageType.SYSTEM) {
            this.type = "system";
        }
    }

    // Getter 및 Setter 메서드
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequiresAction() {
        return requiresAction;
    }

    public void setRequiresAction(boolean requiresAction) {
        this.requiresAction = requiresAction;
    }

    public TempGuardianRequest getRequest() {
        return request;
    }

    public void setRequest(TempGuardianRequest request) {
        this.request = request;
        
        // request 객체가 있으면 관련 필드 자동 설정
        if (request != null) {
            this.id = request.getBedID(); // bedID를 메시지 ID로 사용
            this.title = "침대 " + request.getBedID() + " 임시 보호자 요청"; // 제목에 침대 ID 포함
            
            // 임시 보호자 요청 내용 구성 - 배치 변경
            StringBuilder contentBuilder = new StringBuilder();
            
            // 첫 번째 줄: 요청자ID와 침대ID
            if (request.getRequesterID() != null && !request.getRequesterID().isEmpty()) {
                // 요청자 ID는 요청을 보낸 사람의 ID (designation에서 ! 제거한 값)
                contentBuilder.append("요청자ID: ").append(request.getRequesterID());
                
                // 요청자의 침대 명칭이 있으면 괄호와 함께 추가
                if (request.getRequesterDesignation() != null && !request.getRequesterDesignation().isEmpty()) {
                    contentBuilder.append(" (").append(request.getRequesterDesignation()).append(")");
                }
            }
            
            // 침대ID 추가
            contentBuilder.append(", 침대ID: ").append(request.getBedID());
            
            // 두 번째 줄: 기간
            if (request.getPeriod() != null && !request.getPeriod().isEmpty()) {
                contentBuilder.append("\n기간: ").append(request.getPeriod());
            }
            
            this.content = contentBuilder.toString();
            
            // MessageType 설정
            this.messageType = MessageType.TEMP_GUARDIAN_REQUEST;
        }
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", timestamp=" + timestamp +
                ", senderName='" + senderName + '\'' +
                ", type='" + type + '\'' +
                ", requiresAction=" + requiresAction +
                ", messageType=" + messageType +
                ", request=" + (request != null ? request.toString() : "null") +
                '}';
    }
} 