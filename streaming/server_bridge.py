#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import socket
import struct
import threading
import base64

import cv2
import numpy as np

from flask import Flask
from flask_socketio import SocketIO

########################################
# 1) Flask + SocketIO 설정 (포트 6000)
########################################
app = Flask(__name__)
socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

@app.route('/')
def index():
    return "RPi(9999)->[Server]->(6000)App : 연결 확인 페이지"

########################################
# 2) 라즈베리파이로부터 (TCP/9999) 영상 수신
########################################
def rpi_receiver_tcp():
    HOST = '0.0.0.0'
    PORT = 9999
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)

    print(f"[TCP] 라즈베리파이 영상 수신 포트: {PORT} 대기 중...")
    conn, addr = server_socket.accept()
    print(f"[TCP] 라즈베리파이 연결됨: {addr}")

    data_buf = b""
    payload_size = struct.calcsize(">L")  # 4 bytes

    try:
        while True:
            # (1) 길이(4바이트) 먼저 수신
            while len(data_buf) < payload_size:
                packet = conn.recv(4096)
                if not packet:
                    print("[TCP] 연결 끊김")
                    return
                data_buf += packet

            packed_size = data_buf[:payload_size]
            data_buf = data_buf[payload_size:]
            msg_size = struct.unpack(">L", packed_size)[0]

            # (2) 실제 프레임 데이터 수신
            while len(data_buf) < msg_size:
                data_buf += conn.recv(4096)

            frame_data = data_buf[:msg_size]
            data_buf = data_buf[msg_size:]

            # (3) OpenCV로 JPEG → 이미지
            frame = cv2.imdecode(np.frombuffer(frame_data, dtype=np.uint8), cv2.IMREAD_COLOR)
            if frame is None:
                print("[TCP] 프레임 디코딩 실패")
                continue

            # (4) 다시 JPEG 인코딩 후 base64로 Socket.IO 브로드캐스트
            _, jpg_buffer = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
            b64_frame = base64.b64encode(jpg_buffer).decode('utf-8')

            # 여기 수정 포인트: JSON 형태로 emit

            # socketio.emit('video_frame', {'image': b64_frame})
            # print("[TCP] 수신 소켓 종료")
            video_send(b64_frame)

    except Exception as e:
        print("[TCP] 예외 발생:", e)
    finally:
        conn.close()
        server_socket.close()
        print("[TCP] 수신 소켓 종료")


def video_send(b64_frame) :
    socketio.emit('video_frame', {'image': b64_frame})


########################################
# 3) 메인 실행: 백그라운드(Thread)로 RPi 수신 + Flask SocketIO(6000)
########################################
if __name__ == "__main__":
    t = threading.Thread(target=rpi_receiver_tcp, daemon=True)
    t.start()

    print("[Server] SocketIO (port=6000) 시작")
    socketio.run(app, host='0.0.0.0', port=6000, debug=True, use_reloader=False)
