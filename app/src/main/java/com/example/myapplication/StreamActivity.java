package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.engineio.client.transports.WebSocket;

/** ② 서버에 bedID 전송 → 해당 침대 룸(video room)만 구독 */
public class StreamActivity extends AppCompatActivity {

    private static final String TAG = "StreamActivity";
    private static final String SOCKET_URL = "http://192.168.0.76:6000";

    private ImageView videoView;
    private TextView  statusText;
    private ProgressBar loading;

    private Socket mSocket;
    private Handler ui = new Handler(Looper.getMainLooper());
    private String bedID;                 // ← 메인에서 전달받음

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_stream);

        bedID = getIntent().getStringExtra("bedID");
        if (bedID == null || bedID.isEmpty()) {
            Toast.makeText(this,"bedID 정보가 없습니다",Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        videoView  = findViewById(R.id.videoView);
        statusText = findViewById(R.id.statusText);
        loading    = findViewById(R.id.loadingIndicator);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 배경·비율 등 레이아웃 손보려면 XML or 여기서 조정
        ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) videoView.getLayoutParams();
        lp.width  = (int)(getResources().getDisplayMetrics().widthPixels  * 0.9);
        lp.height = (int)(getResources().getDisplayMetrics().heightPixels * 0.9);
        videoView.setLayoutParams(lp);

        statusText.setText("서버 연결 중…"); loading.setVisibility(View.VISIBLE);

        initSocket();
    }

    /* ───────── Socket.IO 연결 및 이벤트 ───────── */
    private void initSocket() {
        try {
            IO.Options opts = new IO.Options();
            opts.transports = new String[]{WebSocket.NAME};
            opts.reconnection = true; opts.timeout = 10_000;
            mSocket = IO.socket(SOCKET_URL, opts);
        } catch (URISyntaxException e) { e.printStackTrace(); return; }

        // 1) 연결 후 bedID 구독
        mSocket.on(Socket.EVENT_CONNECT, args -> {
            try {
                JSONObject obj = new JSONObject(); obj.put("bedID", bedID);
                mSocket.emit("subscribe_bed", obj);
            } catch (Exception ignore) {}
            ui.post(() -> statusText.setText("스트림 대기 중…"));
        });

        // 2) 영상 프레임
        mSocket.on("video_frame", args -> {
            if (args.length==0 || !(args[0] instanceof JSONObject)) return;
            try {
                String b64 = ((JSONObject)args[0]).getString("image");
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                Bitmap src = BitmapFactory.decodeByteArray(bytes,0,bytes.length);

                // 좌우반전
                Matrix m = new Matrix(); m.preScale(-1f, 1f);
                Bitmap bmp = Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);
                if (src != bmp) src.recycle();

                ui.post(() -> {
                    loading.setVisibility(View.GONE);
                    statusText.setVisibility(View.GONE);
                    videoView.setImageBitmap(bmp);
                });
            } catch (Exception e) { Log.e(TAG,"decode err",e);}
        });

        // (선택) 오류/재연결 로그
        mSocket.on(Socket.EVENT_CONNECT_ERROR, a -> Log.e(TAG,"연결 오류"));
        mSocket.on(Socket.EVENT_DISCONNECT,   a -> Log.e(TAG,"연결 끊김"));

        mSocket.connect();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (mSocket!=null) { mSocket.off(); mSocket.disconnect(); }
    }
}
