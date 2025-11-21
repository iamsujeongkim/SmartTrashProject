# SmartTrashProject  
인공지능 분리배출 쓰레기통 시스템

## 프로젝트 소개
IoT 센서(초음파), AI 이미지 분류, Firebase Realtime Database, 모바일 앱·관리자 웹을 통합한  
스마트 분리배출 관리 시스템입니다.

사용자는 앱으로 분리배출 종류를 선택하고 사진을 업로드하며,  
관리자는 웹 대시보드에서 실시간 쓰레기통 상태를 확인할 수 있습니다.

---

## 주요 기능

### 사용자 앱 (Android / Kotlin)
- 분리배출 항목 선택
- 사진 촬영/선택 후 업로드
- AI 이미지 분류 결과 확인
- 실시간 상태 조회 (DB 연동)

### 관리자 웹 (HTML/CSS/JS)
- 실시간 쓰레기통 상태 대시보드
- FULL / OK / EMPTY 자동 판별
- Firebase DB 연동

### 하드웨어 (ESP32)
- 초음파 센서를 이용한 쓰레기량 측정
- 서보모터 기반 자동 뚜껑 제어
- Firebase DB와 통신

### AI 분류(Tensorflow)
- Teachable Machine 기반 이미지 분류 모델 사용
- 종이/플라스틱/유리 등 쓰레기 종류 자동 판별
- 앱에서 업로드된 이미지를 서버에서 처리 후 결과 반환
- 모델 정확도 향상을 위해 데이터 수집/전처리/재학습 가능 구조

---

## 프로젝트 구조

## 프로젝트 구조

SmartTrashProject/
├─ firebase_rules.json        # Firebase Realtime Database 보안 규칙
├─ firmware_code.txt          # ESP32 펌웨어 (초음파 센서 + 서보모터 + Firebase 연동)
├─ index.js                   # AI 분류 서버 (Node.js + TensorFlow.js + Firebase Admin)
├─ index.html                 # 관리자 웹 대시보드 화면
├─ app.js                     # 관리자 웹 로직 (Firebase 연동, 상태 표시)
├─ style.css                  # 관리자 웹 스타일
└─ SmartTrashProjectApp/      # Android 사용자 앱 전체 코드 (Kotlin)
   └─ ...                     # 액티비티, 레이아웃 파일 등

---

## 시연 영상(Demo Video)
아래 링크에서 프로젝트 시연 영상을 확인할 수 있습니다.
> https://youtu.be/GLAArqQgUN8?si=n9vWY6plOMkdy48S

---

## 팀원
- 최지우 — 하드웨어/회로
- 김민후 — 앱/웹 개발
- 김수정 — Firebase 서버 개발
- 이은서 — AI 이미지 분류 모델

---

## 참고 자료
- Bigbelly 공식: https://bigbelly.com  
- Enevo 공식: https://enevo.com
