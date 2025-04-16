# tcp_video_server.py
import socket
import cv2
import numpy as np
import struct

HOST = '0.0.0.0'  # 내부망 모든 인터페이스에서 받겠다
PORT = 9999       # 아무 포트나 사용 가능

server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.bind((HOST, PORT))
server_socket.listen(1)
print(f"[서버 대기 중] 포트 {PORT}에서 연결을 기다리는 중...")

conn, addr = server_socket.accept()
print(f"[클라이언트 연결됨] {addr}")

data = b""
payload_size = struct.calcsize(">L")  # 4 bytes

while True:
    while len(data) < payload_size:
        packet = conn.recv(4096)
        if not packet:
            break
        data += packet

    packed_msg_size = data[:payload_size]
    data = data[payload_size:]
    msg_size = struct.unpack(">L", packed_msg_size)[0]

    while len(data) < msg_size:
        data += conn.recv(4096)

    frame_data = data[:msg_size]
    data = data[msg_size:]

    frame = cv2.imdecode(np.frombuffer(frame_data, dtype=np.uint8), cv2.IMREAD_COLOR)
    cv2.imshow('TCP 영상 수신', frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

conn.close()
cv2.destroyAllWindows()
