from flask import Flask, request, jsonify
import pymysql
from datetime import datetime
import firebase_admin
from firebase_admin import credentials, messaging
import threading

# Firebase Admin 초기화 (서비스 계정 키 파일을 사용하여 Firebase와 통신)
cred = credentials.Certificate("c:/Capstone/server/babybed-b6356-firebase-adminsdk-fbsvc-e8e08e3e71.json")
firebase_admin.initialize_app(cred)

app = Flask(__name__)

# DB 연결 함수: MySQL 데이터베이스와 연결
def get_connection():
    return pymysql.connect(
        host='localhost',
        user='root',
        password='1234',
        db='babybed',
        charset='utf8'
    )

# 서버 시작 시 BedInfo 테이블의 데이터를 출력 (디버깅용)
def print_bedinfo_on_startup():
    try:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM BedInfo")
        rows = cursor.fetchall()
        print("==== [Startup] BedInfo Table Data ====")
        for row in rows:
            print(row)
        print("=======================================")
    except Exception as e:
        print("DB 조회 오류:", e)
    finally:
        cursor.close()
        conn.close()

# 전역 딕셔너리: 각 사용자(gdID)에 대해 알림 전송 스케줄러 종료 이벤트를 저장
notification_stop_events = {}

# 특정 사용자에 대해 알림 전송 스케줄러를 실행하는 함수
def run_notification_scheduler(user_id):
    try:
        from notification_scheduler import schedule_notifications_for_user
        stop_event = threading.Event()  # 스레드 종료를 위한 이벤트 생성
        notification_stop_events[user_id] = stop_event
        schedule_notifications_for_user(user_id, stop_event)
    except Exception as e:
        print("Notification scheduler 실행 오류:", e)
    finally:
        if user_id in notification_stop_events:
            del notification_stop_events[user_id]

# 로그인 API: 사용자가 아이디와 비밀번호를 입력하여 로그인 요청을 할 때 호출
@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    gdID = data.get('gdID')
    pw = data.get('password')
    auto_login = data.get('autoLogin', False)  # 클라이언트에서 자동로그인 여부를 전달

    # 필수 입력값 검증
    if not gdID or not pw:
        responseDict = {"success": False, "message": "아이디와 비밀번호를 입력하세요."}
        print("login response:", responseDict)
        return jsonify(responseDict), 400

    try:
        conn = get_connection()
        cursor = conn.cursor()
        # GuardianLog 테이블에서 해당 아이디와 비밀번호를 가진 사용자 정보 조회
        sql = "SELECT JoinDate, FCM_toKen FROM GuardianLog WHERE GdID=%s AND pw=%s"
        cursor.execute(sql, (gdID, pw))
        result = cursor.fetchone()

        if result:
            join_date, fcm_token = result
            # 자동로그인 설정 시 알림 스케줄러 실행
            if auto_login:
                if gdID not in notification_stop_events:
                    threading.Thread(target=run_notification_scheduler, args=(gdID,), daemon=True).start()
            responseDict = {
                "success": True,
                "message": "로그인 성공",
                "gdID": gdID,
                "joinDate": str(join_date),
                "FCM_toKen": fcm_token
            }
            print("login response:", responseDict)
            return jsonify(responseDict), 200
        else:
            responseDict = {"success": False, "message": "아이디 또는 비밀번호가 틀립니다."}
            print("login response:", responseDict)
            return jsonify(responseDict), 401

    except Exception as e:
        print(e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("login response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 회원가입 API: 새로운 사용자를 등록하기 위한 API
@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    gdID = data.get('gdID')
    password = data.get('password')
    fcm_token = data.get('fcmToken')

    # 필수 입력값 검증
    if not gdID or not password or not fcm_token:
        responseDict = {"success": False, "message": "필수 입력값 누락"}
        print("register response:", responseDict)
        return jsonify(responseDict), 400

    join_date_str = datetime.now().strftime('%Y-%m-%d')

    try:
        conn = get_connection()
        cursor = conn.cursor()
        # 이미 등록된 아이디인지 확인
        sql_check = "SELECT GdID FROM GuardianLog WHERE GdID=%s"
        cursor.execute(sql_check, (gdID,))
        row = cursor.fetchone()
        if row:
            responseDict = {"success": False, "message": "이미 존재하는 아이디입니다."}
            print("register response:", responseDict)
            return jsonify(responseDict), 409

        # GuardianLog 테이블에 새 사용자 삽입
        sql_insert = """
            INSERT INTO GuardianLog (GdID, pw, JoinDate, FCM_toKen)
            VALUES (%s, %s, %s, %s)
        """
        cursor.execute(sql_insert, (gdID, password, join_date_str, fcm_token))
        conn.commit()

        # 회원가입 성공 후 축하 푸시 알림 전송
        send_registration_congrats(fcm_token, gdID)

        responseDict = {"success": True, "message": "회원가입 성공"}
        print("register response:", responseDict)
        return jsonify(responseDict), 200

    except Exception as e:
        print("회원가입 오류:", e)
        responseDict = {"success": False, "message": str(e)}
        print("register response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 회원가입 성공 시 축하 푸시 알림 전송 함수
def send_registration_congrats(token, userId):
    message = messaging.Message(
        notification=messaging.Notification(
            title="회원가입 축하드립니다!",
            body=f"{userId}님, 가입을 환영합니다!"
        ),
        token=token
    )
    try:
        response = messaging.send(message)
        print("푸시 전송 성공:", response)
    except Exception as e:
        print("푸시 전송 실패:", e)

# 아이디 중복 체크 API
@app.route('/api/checkDuplicate', methods=['POST'])
def check_duplicate():
    data = request.get_json()
    gdID = data.get('gdID')
    if not gdID:
        # 'gdID'가 없으면 'id' 파라미터 확인
        gdID = data.get('id')
        
    if not gdID:
        responseDict = {"success": False, "message": "아이디가 누락되었습니다."}
        print("checkDuplicate response:", responseDict)
        return jsonify(responseDict), 400
    try:
        conn = get_connection()
        cursor = conn.cursor()
        sql = "SELECT GdID FROM GuardianLog WHERE GdID = %s"
        cursor.execute(sql, (gdID,))
        result = cursor.fetchone()
        if result:
            responseDict = {"success": True, "available": False, "message": "이미 존재하는 아이디입니다."}
        else:
            responseDict = {"success": True, "available": True, "message": "사용 가능한 아이디입니다."}
        print("checkDuplicate response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("checkDuplicate response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 로그아웃 API: 사용자가 로그아웃 시 호출, 알림 스케줄러 종료
@app.route('/api/logout', methods=['POST'])
def logout():
    data = request.get_json()
    gdID = data.get('gdID')
    if not gdID:
        responseDict = {"success": False, "message": "gdID 누락"}
        print("logout response:", responseDict)
        return jsonify(responseDict), 400

    # 해당 사용자의 알림 스케줄러 종료
    if gdID in notification_stop_events:
        notification_stop_events[gdID].set()
        responseDict = {"success": True, "message": f"{gdID} 로그아웃 및 알림 전송 중지"}
        print("logout response:", responseDict)
        return jsonify(responseDict), 200
    else:
        responseDict = {"success": False, "message": f"{gdID}에 대해 실행 중인 알림 스케줄러가 없습니다."}
        print("logout response:", responseDict)
        return jsonify(responseDict), 400

# 사용자의 침대 정보 조회 API (checkMyBed)
@app.route('/api/checkMyBed', methods=['POST'])
def check_my_bed():
    data = request.get_json()
    gdID = data.get('gdID')
    print("check_my_bed gdID:", gdID)
    if not gdID:
        responseDict = {"success": False, "message": "gdID 누락"}
        print("checkMyBed response:", responseDict)
        return jsonify(responseDict), 400
    try:
        conn = get_connection()
        cursor = conn.cursor()
        # GuardBed와 BedInfo 테이블을 조인하여 해당 사용자의 침대 정보를 조회
        # designation이 '!'로 시작하지 않는 침대만 가져오고 bed_order 기준 오름차순 정렬
        sql = """
            SELECT gb.GdID, gb.bedID, gb.designation, DATE_FORMAT(gb.period, '%%Y-%%m-%%d') as period, 
                   CAST(gb.bed_order AS CHAR) as bed_order, bi.serialNumber
            FROM GuardBed gb
            JOIN BedInfo bi ON gb.bedID = bi.bedID
            WHERE gb.GdID = %s AND gb.designation IS NOT NULL AND gb.designation NOT LIKE '!%%'
            ORDER BY gb.bed_order ASC
        """
        print("Executing SQL:", sql, "with parameter:", gdID)
        cursor.execute(sql, (gdID,))
        rows = cursor.fetchall()
        print("Raw rows fetched:", rows)
        bedInfo = []
        for row in rows:
            bedData = list(row)
            # 임시보호자인 경우 남은 기간 계산
            if bedData[3] and bedData[3].strip() and bedData[3].lower() != 'null':
                try:
                    period_date = datetime.strptime(bedData[3], "%%Y-%%m-%%d").date()
                    today = datetime.today().date()
                    remaining_days = (period_date - today).days
                    # 남은 일수 정보를 추가 (임시 필드)
                    bedData.append(str(remaining_days))
                except Exception as e:
                    print("남은 기간 계산 오류:", e)
                    bedData.append("0")  # 오류 시 기본값
            else:
                bedData.append("0")  # 임시보호자가 아닌 경우
            
            bedInfo.append(bedData)
            print("Row with remaining days:", bedData)
        
        responseDict = {"success": True, "bedInfo": bedInfo}
        print("checkMyBed response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in check_my_bed:", e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("checkMyBed response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 침대별 보호자/임시보호자 수 계산 API
@app.route('/api/calcBedCounts', methods=['POST'])
def calc_bed_counts():
    # GdID 값은 무시하고 전체 데이터를 대상으로 계산함
    try:
        conn = get_connection()
        cursor = conn.cursor()
        # GuardBed와 BedInfo 테이블을 조인하여 침대별 정보 계산
        sql = """
            SELECT gb.GdID, gb.bedID, gb.designation, DATE_FORMAT(gb.period, '%Y-%m-%d') as period, 
                   CAST(gb.bed_order AS CHAR) as bed_order, bi.serialNumber
            FROM GuardBed gb
            JOIN BedInfo bi ON gb.bedID = bi.bedID
            ORDER BY gb.bed_order ASC
        """
        print("Executing SQL:", sql)
        cursor.execute(sql)
        rows = cursor.fetchall()
        print("Raw rows fetched for calc:", rows)
        
        # 그룹화: 같은 bedID별로 묶어서 보호자와 임시보호자 수 계산
        groups = {}
        for row in rows:
            bedID = row[1]
            groups.setdefault(bedID, []).append(row)
        
        bedCounts = []
        for bedID, groupRows in groups.items():
            designation = groupRows[0][2]
            serialNumber = groupRows[0][5]
            guardianCount = 0
            tempCount = 0
            periodDisplay = ""
            remainingDays = 0
            for row in groupRows:
                period = row[3]
                # period가 없는 경우 보호자로 간주, 있으면 임시보호자로 간주
                if period is None or period.strip() == "" or period.strip().lower() == "null":
                    guardianCount += 1
                else:
                    tempCount += 1
                    periodDisplay = period
                    try:
                        periodDate = datetime.strptime(period, "%Y-%m-%d").date()
                        today = datetime.today().date()
                        remainingDays = (periodDate - today).days
                    except Exception as e:
                        print("남은 기간 계산 오류:", e)
                        remainingDays = 0
            bedCount = {
                "bedID": bedID,
                "designation": designation,
                "guardianCount": guardianCount,
                "tempCount": tempCount,
                "serialNumber": serialNumber,
                "period": periodDisplay,
                "remainingDays": remainingDays
            }
            bedCounts.append(bedCount)
            # 터미널에 각 침대별 계산 결과 출력
            print("Calculated bed count:", bedCount)
            
        responseDict = {"success": True, "bedCounts": bedCounts}
        print("calcBedCounts response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in calc_bed_counts:", e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("calcBedCounts response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 침대 삭제 API
@app.route('/api/deleteBed', methods=['POST'])
def delete_bed():
    data = request.get_json()
    gdID = data.get('gdID')
    bedID = data.get('bedID')
    password = data.get('password')
    if not gdID or not bedID or not password:
        responseDict = {"success": False, "message": "필수 값 누락"}
        print("deleteBed response:", responseDict)
        return jsonify(responseDict), 400
    try:
        conn = get_connection()
        cursor = conn.cursor()
        # GuardianLog에서 비밀번호 조회하여 일치 여부 확인
        sql_check = "SELECT pw FROM GuardianLog WHERE GdID = %s"
        cursor.execute(sql_check, (gdID,))
        row = cursor.fetchone()
        if not row:
            responseDict = {"success": False, "message": "존재하지 않는 사용자입니다."}
            print("deleteBed response:", responseDict)
            return jsonify(responseDict), 404
        db_pw = row[0]
        if db_pw != password:
            responseDict = {"success": False, "message": "비밀번호가 일치하지 않습니다."}
            print("deleteBed response:", responseDict)
            return jsonify(responseDict), 403
        
        # GuardBed 테이블에서 해당 침대 정보 삭제
        sql_delete = "DELETE FROM GuardBed WHERE GdID = %s AND bedID = %s"
        cursor.execute(sql_delete, (gdID, bedID))
        conn.commit()
        responseDict = {"success": True, "message": "삭제 성공"}
        print("deleteBed response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in delete_bed:", e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("deleteBed response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 침대 정보 조회 API: serialNumber를 통해 침대 ID 확인
@app.route('/api/checkBedInfo', methods=['POST'])
def check_bed_info():
    data = request.get_json()
    serial = data.get('serialNumber')
    if not serial:
        responseDict = {"success": False, "message": "serialNumber 누락"}
        print("checkBedInfo response:", responseDict)
        return jsonify(responseDict), 400
    try:
        conn = get_connection()
        cursor = conn.cursor()
        sql = "SELECT bedID FROM BedInfo WHERE serialNumber = %s"
        cursor.execute(sql, (serial,))
        row = cursor.fetchone()
        if row:
            bedID = row[0]
            responseDict = {"success": True, "message": "침대 정보 확인", "bedID": bedID}
            print("checkBedInfo response:", responseDict)
            return jsonify(responseDict), 200
        else:
            responseDict = {"success": False, "message": "침대 정보가 없습니다."}
            print("checkBedInfo response:", responseDict)
            return jsonify(responseDict), 404
    except Exception as e:
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("checkBedInfo response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 침대 추가 API: 새로운 GuardBed 레코드를 추가하여 사용자의 침대를 등록
@app.route('/api/addGuardBed', methods=['POST'])
def add_guard_bed():
    data = request.get_json()
    gdID = data.get('gdID')
    bedID = data.get('bedID')
    designation = data.get('designation')
    period = data.get('period')  # 빈 문자열이면 null로 처리
    requestedBy = data.get('requestedBy')  # 요청자 ID (임시보호자 추가 요청 시)

    # 필수 값 검사: gdID와 bedID는 반드시 있어야 함
    if not gdID or not bedID:
        responseDict = {"success": False, "message": "필수 값 누락"}
        print("addGuardBed response:", responseDict)
        return jsonify(responseDict), 400

    # designation이 빈 문자열이거나 "null"이면 None 처리
    if not designation or designation.strip().lower() == "null" or designation.strip() == "":
        designation = None

    try:
        conn = get_connection()
        cursor = conn.cursor()

        # 임시보호자 추가 요청 여부 확인 (designation이 !로 시작하고 requestedBy가 있는 경우)
        is_temp_guardian_request = designation and designation.startswith('!') and requestedBy

        # 해당 침대에 대한 요청이 이미 존재하는지 확인 (중복 방지)
        if is_temp_guardian_request:
            check_sql = """SELECT COUNT(*) FROM GuardBed WHERE GdID = %s AND bedID = %s"""
            cursor.execute(check_sql, (gdID, bedID))
            count = cursor.fetchone()[0]
            
            if count > 0:
                responseDict = {"success": False, "message": "이미 해당 침대에 대한 요청이 존재합니다"}
                print("addGuardBed response:", responseDict)
                return jsonify(responseDict), 409  # 409 Conflict

        # 현재 gdID의 GuardBed 테이블에서 가장 큰 bed_order 값을 가져와 +1하여 새로운 순서를 지정
        sql_max = "SELECT COALESCE(MAX(bed_order), 0) FROM GuardBed WHERE GdID = %s"
        cursor.execute(sql_max, (gdID,))
        result = cursor.fetchone()
        bed_order = result[0] + 1 if result and result[0] is not None else 1

        sql_insert = """
            INSERT INTO GuardBed (GdID, bedID, designation, period, bed_order)
            VALUES (%s, %s, %s, %s, %s)
        """
        # period 값이 없으면 None으로 처리
        period_val = period if period and period.strip() != "" else None

        cursor.execute(sql_insert, (gdID, bedID, designation, period_val, bed_order))
        conn.commit()
        responseDict = {"success": True, "message": "침대 추가 성공"}
        print("addGuardBed response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("addGuardBed response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# GuardBed 존재 여부 확인 API: 특정 gdID와 bedID에 해당하는 레코드가 있는지 체크
@app.route('/api/checkGuardBed', methods=['POST'])
def check_guard_bed():
    data = request.get_json()
    gdID = data.get('gdID')
    bedID = data.get('bedID')
    
    if not gdID or not bedID:
        responseDict = {"success": False, "message": "필수 값 누락"}
        print("checkGuardBed response:", responseDict)
        return jsonify(responseDict), 400
    
    try:
        conn = get_connection()
        cursor = conn.cursor()
        sql = "SELECT * FROM GuardBed WHERE GdID = %s AND bedID = %s"
        print("Executing SQL:", sql, "with parameters:", gdID, bedID)
        cursor.execute(sql, (gdID, bedID))
        row = cursor.fetchone()
        if row:
            responseDict = {"success": True, "exists": True, "message": "이미 임시보호자 등록됨", "row": str(row)}
        else:
            responseDict = {"success": True, "exists": False, "message": "등록된 임시보호자가 없음"}
        print("checkGuardBed response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("checkGuardBed response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 임시 보호자 요청 목록 조회 API: 사용자의 GuardBed 레코드 중 designation 값이 '!'로 시작하는 경우를 조회
@app.route('/api/getPendingTempGuardianRequests', methods=['POST'])
def get_pending_temp_guardian_requests():
    data = request.get_json()
    gdID = data.get('gdID')
    if not gdID:
        responseDict = {"result": False, "message": "gdID 누락"}
        print("getPendingTempGuardianRequests response:", responseDict)
        return jsonify(responseDict), 400

    try:
        conn = get_connection()
        cursor = conn.cursor()
        # designation이 '!'로 시작하는 경우를 pending 요청으로 간주
        # 요청자가 갖고 있는 침대 명칭도 함께 조회
        sql = """
            SELECT gb1.bedID, gb1.designation, DATE_FORMAT(gb1.period, '%%Y-%%m-%%d') as period, gb1.bed_order,
                   (SELECT gb2.designation FROM GuardBed gb2 
                    WHERE gb2.bedID = gb1.bedID AND gb2.GdID = SUBSTRING(gb1.designation, 2) 
                    LIMIT 1) as requester_designation
            FROM GuardBed gb1
            WHERE gb1.GdID = %s 
            AND gb1.designation LIKE '!%%'
        """
        print("Executing SQL:", sql, "with gdID:", gdID)
        cursor.execute(sql, (gdID,))
        rows = cursor.fetchall()

        requests = []
        for row in rows:
            # designation에서 '!' 제거하여 요청자 ID 추출
            requesterID = row[1][1:] if row[1] and row[1].startswith('!') else ""
            # 요청자가 지정한 침대 명칭
            requesterDesignation = row[4] if row[4] else ""
            
            requests.append({
                "bedID": row[0] if row[0] else "",
                "requesterID": requesterID,
                "period": row[2] if row[2] else "",
                "bed_order": row[3] if row[3] else 0,
                "requesterDesignation": requesterDesignation
            })

        print("Found pending requests:", len(requests), "for user:", gdID)
        for req in requests:
            print("Request details:", req)
            
        responseDict = {"result": True, "requests": requests, "message": "요청 목록 조회 성공"}
        print("getPendingTempGuardianRequests response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in get_pending_temp_guardian_requests:", e)
        responseDict = {"result": False, "message": "서버 오류: " + str(e)}
        print("getPendingTempGuardianRequests response:", responseDict)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 임시 보호자 요청 수락 API: 사용자가 특정 침대 요청을 수락할 때 designation 값을 업데이트함
@app.route('/api/acceptTempGuardianRequest', methods=['POST'])
def accept_temp_guardian_request():
    data = request.get_json()
    gdID = data.get('gdID')
    bedID = data.get('bedID')
    designation = data.get('designation', bedID + ' 침대')  # 기본 명칭 설정, designation이 없으면 bedID를 기반으로 생성
    
    if not gdID or not bedID:
        responseDict = {"result": False, "message": "필수 값 누락 (gdID, bedID)"}
        print("acceptTempGuardianRequest response:", responseDict)
        return jsonify(responseDict), 400
    
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 해당 사용자의 GuardBed 레코드 중 designation이 '!'로 시작하는 요청이 있는지 확인
        check_sql = """
            SELECT designation FROM GuardBed
            WHERE GdID = %s AND bedID = %s
                AND designation LIKE '!%%'
        """
        cursor.execute(check_sql, (gdID, bedID))
        result = cursor.fetchone()
        
        if not result:
            responseDict = {"result": False, "message": "해당하는 요청을 찾을 수 없거나 이미 처리되었습니다."}
            print("acceptTempGuardianRequest response:", responseDict)
            return jsonify(responseDict), 404
        
        # 사용자의 GuardBed 테이블에서 designation이 !로 시작하지 않는 튜플의 개수 계산
        count_sql = """
            SELECT COUNT(*) FROM GuardBed
            WHERE GdID = %s AND designation NOT LIKE '!%%'
        """
        cursor.execute(count_sql, (gdID,))
        count = cursor.fetchone()[0]
        
        # 새로운 bed_order 값은 !로 시작하지 않는 designation을 가진 침대 개수 + 1
        new_bed_order = count + 1
        print(f"계산된 새 bed_order 값: {new_bed_order} (현재 정상 침대 개수: {count})")
        
        # 해당 요청의 designation 값과 bed_order 값을 업데이트하여 수락 처리
        update_sql = """
            UPDATE GuardBed
            SET designation = %s, bed_order = %s
            WHERE GdID = %s AND bedID = %s
                AND designation LIKE '!%%'
        """
        cursor.execute(update_sql, (designation, new_bed_order, gdID, bedID))
        conn.commit()
        
        if cursor.rowcount > 0:
            responseDict = {"result": True, "message": "임시 보호자 요청이 수락되었습니다."}
        else:
            responseDict = {"result": False, "message": "업데이트 실패: 행이 변경되지 않았습니다."}
        
        print("acceptTempGuardianRequest response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in accept_temp_guardian_request:", e)
        responseDict = {"result": False, "message": "서버 오류: " + str(e)}
        print("acceptTempGuardianRequest exception:", e)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 임시 보호자 요청 거절 API: 사용자가 요청을 거절하면 해당 GuardBed 레코드를 삭제함
@app.route('/api/rejectTempGuardianRequest', methods=['POST'])
def reject_temp_guardian_request():
    data = request.get_json()
    gdID = data.get('gdID')
    bedID = data.get('bedID')
    
    if not gdID or not bedID:
        responseDict = {"result": False, "message": "필수 값 누락 (gdID, bedID)"}
        print("rejectTempGuardianRequest response:", responseDict)
        return jsonify(responseDict), 400
    
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 해당 요청이 존재하는지 확인 (designation이 '!'로 시작하는 경우)
        check_sql = """
            SELECT designation FROM GuardBed
            WHERE GdID = %s AND bedID = %s
                AND designation LIKE '!%%'
        """
        cursor.execute(check_sql, (gdID, bedID))
        result = cursor.fetchone()
        
        if not result:
            responseDict = {"result": False, "message": "해당하는 요청을 찾을 수 없거나 이미 처리되었습니다."}
            print("rejectTempGuardianRequest response:", responseDict)
            return jsonify(responseDict), 404
        
        # 해당 요청 레코드 삭제
        delete_sql = """
            DELETE FROM GuardBed
            WHERE GdID = %s AND bedID = %s
                AND designation LIKE '!%%'
        """
        cursor.execute(delete_sql, (gdID, bedID))
        conn.commit()
        
        if cursor.rowcount > 0:
            responseDict = {"result": True, "message": "임시 보호자 요청이 거절되었습니다."}
        else:
            responseDict = {"result": False, "message": "삭제 실패: 행이 변경되지 않았습니다."}
        
        print("rejectTempGuardianRequest response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in reject_temp_guardian_request:", e)
        responseDict = {"result": False, "message": "서버 오류: " + str(e)}
        print("rejectTempGuardianRequest exception:", e)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 침대 순서 변경 API: 침대의 표시 순서를 변경
@app.route('/api/updateBedOrder', methods=['POST'])
def update_bed_order():
    data = request.get_json()
    gdID = data.get('gdID')
    bedOrders = data.get('bedOrders')
    
    if not gdID or not bedOrders:
        responseDict = {"success": False, "message": "필수 값 누락 (gdID, bedOrders)"}
        print("updateBedOrder response:", responseDict)
        return jsonify(responseDict), 400
    
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        for bedOrder in bedOrders:
            bedID = bedOrder.get('bedID')
            order = bedOrder.get('order')
            
            if not bedID or not order:
                continue
                
            # 침대 순서 업데이트
            update_sql = """
                UPDATE GuardBed
                SET bed_order = %s
                WHERE GdID = %s AND bedID = %s
            """
            cursor.execute(update_sql, (order, gdID, bedID))
        
        conn.commit()
        responseDict = {"success": True, "message": "침대 순서가 업데이트되었습니다."}
        print("updateBedOrder response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in update_bed_order:", e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("updateBedOrder exception:", e)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 침대 명칭 변경 API: 침대의 명칭(designation)을 변경
@app.route('/api/updateBedDesignation', methods=['POST'])
def update_bed_designation():
    data = request.get_json()
    gdID = data.get('gdID')
    bedID = data.get('bedID')
    designation = data.get('designation')
    
    if not gdID or not bedID or not designation:
        responseDict = {"success": False, "message": "필수 값 누락 (gdID, bedID, designation)"}
        print("updateBedDesignation response:", responseDict)
        return jsonify(responseDict), 400
    
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 침대 명칭 업데이트
        update_sql = """
            UPDATE GuardBed
            SET designation = %s
            WHERE GdID = %s AND bedID = %s
        """
        cursor.execute(update_sql, (designation, gdID, bedID))
        
        conn.commit()
        if cursor.rowcount > 0:
            responseDict = {"success": True, "message": "침대 명칭이 업데이트되었습니다."}
        else:
            responseDict = {"success": False, "message": "변경할 침대를 찾을 수 없습니다."}
        
        print("updateBedDesignation response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in update_bed_designation:", e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("updateBedDesignation exception:", e)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

# 계정 삭제 API: 비밀번호 확인 후 사용자 계정 삭제
@app.route('/api/deleteAccount', methods=['POST'])
def delete_account():
    data = request.get_json()
    gdID = data.get('gdID')
    password = data.get('password')
    
    if not gdID or not password:
        responseDict = {"success": False, "message": "필수 값 누락 (gdID, password)"}
        print("deleteAccount response:", responseDict)
        return jsonify(responseDict), 400
    
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 비밀번호 확인
        check_sql = "SELECT pw FROM GuardianLog WHERE GdID = %s"
        cursor.execute(check_sql, (gdID,))
        result = cursor.fetchone()
        
        if not result:
            responseDict = {"success": False, "message": "존재하지 않는 사용자입니다."}
            print("deleteAccount response:", responseDict)
            return jsonify(responseDict), 404
            
        db_pw = result[0]
        if db_pw != password:
            responseDict = {"success": False, "message": "비밀번호가 일치하지 않습니다."}
            print("deleteAccount response:", responseDict)
            return jsonify(responseDict), 403
        
        # GuardBed 테이블에서 해당 사용자의 모든 침대 정보 삭제
        delete_beds_sql = "DELETE FROM GuardBed WHERE GdID = %s"
        cursor.execute(delete_beds_sql, (gdID,))
        
        # GuardianLog 테이블에서 사용자 삭제
        delete_account_sql = "DELETE FROM GuardianLog WHERE GdID = %s"
        cursor.execute(delete_account_sql, (gdID,))
        
        conn.commit()
        
        # 해당 사용자의 알림 스케줄러 종료 (있는 경우)
        if gdID in notification_stop_events:
            notification_stop_events[gdID].set()
        
        responseDict = {"success": True, "message": "계정이 성공적으로 삭제되었습니다."}
        print("deleteAccount response:", responseDict)
        return jsonify(responseDict), 200
    except Exception as e:
        print("Exception in delete_account:", e)
        responseDict = {"success": False, "message": "서버 오류: " + str(e)}
        print("deleteAccount exception:", e)
        return jsonify(responseDict), 500
    finally:
        cursor.close()
        conn.close()

if __name__ == '__main__':
    print_bedinfo_on_startup()
    app.run(host='0.0.0.0', port=5000, debug=True)
