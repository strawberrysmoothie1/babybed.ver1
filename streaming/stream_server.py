import socket
import struct
import threading
import base64
import cv2
import numpy as np
import time

from flask import Flask
from flask_socketio import SocketIO

app = Flask(__name__)
# eventlet를 권장 (pip install eventlet)
socketio = SocketIO(
    app,
    cors_allowed_origins="*",
    async_mode='eventlet',
    ping_interval=25,   # ping 주기 (초)
    ping_timeout=60     # pong 못 받으면 끊는 시간 (초)
)

def tcp_video_server():
    HOST = '0.0.0.0'
    PORT = 9999

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"[TCP] 포트 {PORT} 대기 중... (라즈베리파이 연결)")

    try:
        conn, addr = server_socket.accept()
        print(f"[TCP] 라즈베리파이 연결됨: {addr}")

        data = b""
        payload_size = struct.calcsize(">L")
        frame_count = 0
        start_time = time.time()

        while True:
            while len(data) < payload_size:
                packet = conn.recv(4096)
                if not packet:
                    raise ConnectionError("연결 끊김")
                data += packet

            packed_msg_size = data[:payload_size]
            data = data[payload_size:]
            msg_size = struct.unpack(">L", packed_msg_size)[0]
            
            print(f"[TCP] 프레임 크기: {msg_size} 바이트")

            while len(data) < msg_size:
                data += conn.recv(4096)

            frame_data = data[:msg_size]
            data = data[msg_size:]
            
            frame_count += 1
            current_time = time.time()
            elapsed_time = current_time - start_time
            
            if elapsed_time >= 1.0:  # 1초마다 FPS 계산
                fps = frame_count / elapsed_time
                print(f"[TCP] FPS: {fps:.2f}")
                frame_count = 0
                start_time = current_time

            # 디코딩
            try:
                frame = cv2.imdecode(np.frombuffer(frame_data, dtype=np.uint8), cv2.IMREAD_COLOR)
                if frame is None:
                    print("[TCP] 프레임 디코딩 실패!")
                    continue
                
                print(f"[TCP] 프레임 크기: {frame.shape[1]}x{frame.shape[0]}, 타입: {frame.dtype}")
                
                # JPG 인코딩 → base64
                encode_start = time.time()
                _, buffer = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
                base64_str = base64.b64encode(buffer).decode('utf-8')
                encode_time = time.time() - encode_start
                
                print(f"[TCP] Base64 인코딩 시간: {encode_time*1000:.2f}ms, 크기: {len(base64_str)} 문자")
                
                # 서버 콘솔에 로그도 찍어보자
                print("[TCP] 프레임 받음 & emit!")

                # Socket.IO 브로드캐스트
                socketio.emit('video_frame', base64_str)
                
            except Exception as e:
                print(f"[TCP] 프레임 처리 중 오류: {e}")

    except ConnectionError as e:
        print(f"[TCP] 연결 오류: {e}")
    except Exception as e:
        print(f"[TCP] 예외 발생: {e}")
    finally:
        conn.close()
        server_socket.close()
        print("[TCP] 서버 소켓 종료")

@app.route('/')
def index():
    return '🎥 TCP -> Socket.IO 스트리밍 서버 (websocket only 가능)'

@socketio.on('connect')
def handle_connect():
    print("[Socket.IO] 앱 연결")

@socketio.on('disconnect')
def handle_disconnect():
    print("[Socket.IO] 앱 연결 해제")

if __name__ == '__main__':
    # 백그라운드로 TCP 서버 스레드
    t = threading.Thread(target=tcp_video_server, daemon=True)
    t.start()

    print("[서버 시작] 스트리밍 서버 동작 중.")
    # debug=True + use_reloader=False → 디버그는 켜되 자동 reloader는 안 켬
    socketio.run(
        app,
        host='0.0.0.0',
        port=6000,
        debug=True,
        use_reloader=False
    )
