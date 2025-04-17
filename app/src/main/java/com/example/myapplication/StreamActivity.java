package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;

public class StreamActivity extends AppCompatActivity {

    private static final String TAG = "StreamActivity";
    private ImageView videoView;
    private TextView statusText;
    private ProgressBar loadingIndicator;
    private Button btnBack;

    private Socket mSocket;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int RECONNECT_DELAY = 3000;
    private Handler reconnectHandler;
    private Runnable reconnectRunnable;
    private boolean isReconnecting = false;
    private int frameCount = 0;

    private static final String SOCKET_URL = "http://192.168.0.76:6000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        videoView = findViewById(R.id.videoView);
        statusText = findViewById(R.id.statusText);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        statusText.setText("스트리밍 서버에 연결 중...");
        loadingIndicator.setVisibility(View.VISIBLE);

        videoView.setImageDrawable(null);

        Log.d(TAG, "StreamActivity 생성됨, 소켓 연결 준비 중...");

        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = this::handleConnectionTimeout;

        reconnectHandler = new Handler(Looper.getMainLooper());
        reconnectRunnable = this::reconnectToServer;

        initSocketIO();
    }

    private void initSocketIO() {
        try {
            IO.Options options = new IO.Options();
            options.transports = new String[]{WebSocket.NAME};
            options.reconnection = true;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 1000;
            options.timeout = CONNECTION_TIMEOUT;
            Log.d("test", "스트리밍 서버 연결 시도: " + SOCKET_URL);
            Log.d(TAG, "스트리밍 서버 연결 시도: " + SOCKET_URL);
            mSocket = IO.socket(SOCKET_URL, options);

        } catch (URISyntaxException e) {
            Log.e(TAG, "소켓 URI 오류: " + e.getMessage(), e);
            statusText.setText("소켓 초기화 실패: " + e.getMessage());
            return;
        }

        mSocket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "✅ Socket connected successfully!");
        });

        mSocket.on("video_frame", args -> {
            Log.d("test","test");
            if (args.length > 0 && args[0] instanceof org.json.JSONObject) {
                try {
                    org.json.JSONObject obj = (org.json.JSONObject) args[0];
                    String base64Image = obj.getString("image");

                    Log.d(TAG, "📦 Received base64 image (length=" + base64Image.length() + ")");
                    byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                    if (bitmap == null) {
                        Log.e(TAG, "❌ Bitmap decode 실패! base64 길이: " + base64Image.length());
                    } else {
                        Log.d(TAG, "✅ Bitmap decode 성공! bitmap size = " + bitmap.getWidth() + "x" + bitmap.getHeight());
                        runOnUiThread(() -> videoView.setImageBitmap(bitmap));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ 이미지 처리 중 예외 발생", e);
                }
            } else {
                Log.w(TAG, "⚠️ Received video_frame with 잘못된 형식 또는 데이터 없음.");
            }
        });


        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onError);
        mSocket.on("reconnect", onReconnect);
        mSocket.on("reconnect_attempt", onReconnectAttempt);
        mSocket.on("reconnect_error", onReconnectError);
        mSocket.on("reconnect_failed", onReconnectFailed);

        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT);
        mSocket.connect();
    }

    private void reconnectToServer() {
        if (mSocket != null && !mSocket.connected() && !isReconnecting) {
            isReconnecting = true;
            Log.d(TAG, "서버에 재연결 시도 중...");
            runOnUiThread(() -> {
                statusText.setText("서버에 재연결 시도 중...");
                loadingIndicator.setVisibility(View.VISIBLE);
            });

            try {
                mSocket.connect();
            } catch (Exception e) {
                Log.e(TAG, "재연결 시도 중 오류: " + e.getMessage(), e);
                reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY);
            }
        }
    }

    private void handleConnectionTimeout() {
        if (mSocket != null && !mSocket.connected()) {
            Log.e(TAG, "소켓 연결 타임아웃");
            runOnUiThread(() -> {
                statusText.setText("연결 시간 초과: 다시 시도 중...");
                loadingIndicator.setVisibility(View.VISIBLE);
            });
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY);
        }
    }

    private final Emitter.Listener onConnect = args -> {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        isReconnecting = false;

        Log.d(TAG, "스트리밍 서버에 연결됨");
        runOnUiThread(() -> {
            statusText.setText("서버와 연결됨! 영상 스트림 대기 중...");
            Toast.makeText(this, "스트리밍 서버에 연결되었습니다", Toast.LENGTH_SHORT).show();
        });
    };

    private final Emitter.Listener onDisconnect = args -> {
        Log.d(TAG, "스트리밍 서버 연결 끊김");
        runOnUiThread(() -> {
            statusText.setText("서버 연결 끊김... 재연결 시도 중");
            loadingIndicator.setVisibility(View.VISIBLE);
        });
        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY);
    };

    private final Emitter.Listener onError = args -> {
        Log.e(TAG, "소켓 연결 오류: " + (args.length > 0 ? args[0].toString() : "알 수 없는 오류"));
        runOnUiThread(() -> {
            statusText.setText("연결 오류: 서버가 실행 중인지 확인하세요");
            loadingIndicator.setVisibility(View.VISIBLE);
        });
        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY);
    };

    private final Emitter.Listener onReconnect = args -> {
        Log.d(TAG, "서버에 재연결 성공");
        runOnUiThread(() -> {
            statusText.setText("서버에 재연결됨! 영상 스트림 대기 중...");
            Toast.makeText(this, "서버에 재연결되었습니다", Toast.LENGTH_SHORT).show();
        });
    };

    private final Emitter.Listener onReconnectAttempt = args -> {
        int attempt = args.length > 0 ? (int) args[0] : 0;
        Log.d(TAG, "재연결 시도 #" + attempt);
        runOnUiThread(() -> statusText.setText("재연결 시도 중... (" + attempt + "번째)"));
    };

    private final Emitter.Listener onReconnectError = args -> {
        Log.e(TAG, "재연결 오류: " + (args.length > 0 ? args[0].toString() : "알 수 없는 오류"));
    };

    private final Emitter.Listener onReconnectFailed = args -> {
        Log.e(TAG, "재연결 실패");
        runOnUiThread(() -> {
            statusText.setText("재연결 실패: 수동으로 다시 시도하세요");
            Toast.makeText(this, "서버 연결에 실패했습니다. 다시 시도하세요.", Toast.LENGTH_LONG).show();
        });
        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY);
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeoutHandler != null) timeoutHandler.removeCallbacks(timeoutRunnable);
        if (reconnectHandler != null) reconnectHandler.removeCallbacks(reconnectRunnable);
        if (mSocket != null) {
            mSocket.off();
            mSocket.disconnect();
        }
    }
}
