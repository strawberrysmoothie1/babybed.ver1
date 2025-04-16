package com.example.myapplication.item;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.Message;
import com.example.myapplication.model.TempGuardianRequest;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private Context context;
    private List<Message> messages;
    private OnMessageActionListener listener;

    public interface OnMessageActionListener {
        void onAccept(Message message, int position);
        void onReject(Message message, int position);
    }

    public MessageAdapter(Context context, List<Message> messages, OnMessageActionListener listener) {
        this.context = context;
        this.messages = messages;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 새 item_request 레이아웃을 사용
        View view = LayoutInflater.from(context).inflate(R.layout.item_request, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        
        // 메시지 내용 표시 (Message 클래스의 content 필드 사용)
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            holder.tvRequestInfo.setText(message.getContent());
        } else {
            // 기존 방식으로 폴백
            TempGuardianRequest req = message.getRequest();
            String bedID = "";
            String period = "";
            if (req != null) {
                bedID = req.getBedID();
                period = req.getPeriod() != null ? req.getPeriod() : "";
            } else {
                bedID = message.getId();
            }
            String requestInfo = bedID + " - " + period;
            holder.tvRequestInfo.setText(requestInfo);
        }

        // 수락/거절 버튼 설정 (guard_request 타입에 한해서)
        if (message.isRequiresAction() && 
            (message.getMessageType() == Message.MessageType.TEMP_GUARDIAN_REQUEST || 
             "guard_request".equals(message.getType()))) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(message, holder.getAdapterPosition());
                }
            });
            holder.btnReject.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReject(message, holder.getAdapterPosition());
                }
            });
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    public void updateMessages(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        // TextView for 요청 정보 (침대ID + 기간)
        TextView tvRequestInfo;
        LinearLayout layoutActions;
        Button btnAccept, btnReject;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRequestInfo = itemView.findViewById(R.id.tvRequestInfo);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}
