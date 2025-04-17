# server_test.py

import socket
import struct
import cv2
import numpy as np

def main():
    HOST = '0.0.0.0'  # 모든 인터페이스에서 대기
    PORT = 9999       # 6000번 포트로 수신

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # 동일 포트 재사용 가능하게 설정 (테스트 시 편리)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"[서버] 포트 {PORT}에서 라즈베리파이 연결 대기 중...")

    conn, addr = server_socket.accept()
    print(f"[서버] 라즈베리파이 연결됨: {addr}")

    data_buffer = b""
    payload_size = struct.calcsize(">L")  # 4바이트(영상 길이 정보)

    try:
        while True:
            # 1) 먼저 길이(4바이트) 정보 수신
            while len(data_buffer) < payload_size:
                packet = conn.recv(4096)
                if not packet:
                    print("[서버] 연결이 끊겼습니다.")
                    return
                data_buffer += packet
            
            # 영상 길이 추출
            packed_msg_size = data_buffer[:payload_size]
            data_buffer = data_buffer[payload_size:]
            msg_size = struct.unpack(">L", packed_msg_size)[0]

            # 2) 실제 영상 데이터(msg_size 바이트) 수신
            while len(data_buffer) < msg_size:
                data_buffer += conn.recv(4096)

            frame_data = data_buffer[:msg_size]
            data_buffer = data_buffer[msg_size:]

            # 3) 영상 디코딩 (JPEG → OpenCV 이미지)
            frame = cv2.imdecode(np.frombuffer(frame_data, dtype=np.uint8), cv2.IMREAD_COLOR)
            if frame is None:
                print("[서버] 프레임 디코딩 실패!")
                continue
            
            # 4) 화면에 표시
            cv2.imshow("RaspberryPi Stream (Port 9999)", frame)

            # 'q' 키를 누르면 종료
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break

    except Exception as e:
        print("[서버] 예외 발생:", e)
    finally:
        conn.close()
        server_socket.close()
        cv2.destroyAllWindows()
        print("[서버] 연결 종료 및 소켓 닫음.")

if __name__ == '__main__':
    main()
