// =====================
// 1. 필요한 모듈 불러오기
// =====================
const tfnode = require('@tensorflow/tfjs-node'); // Node.js용 TensorFlow
const admin = require('firebase-admin');
const axios = require('axios');
const fs = require('fs');

// =====================
// 2. Firebase Admin 초기화
// =====================
const serviceAccount = require('./smarttrashproject-1a495-firebase-adminsdk-fbsvc-2994ac7c5d.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: 'https://smarttrashproject-1a495-default-rtdb.firebaseio.com/'
});

const db = admin.database();

// =====================
// 3. AI 모델 로드
// =====================
let model;
(async () => {
  try {
    model = await tfnode.loadLayersModel('file://C:/Users/khool/recycle-server-clean/model/model.json');
    console.log('✅ AI 모델 로드 완료');
  } catch (err) {
    console.error('❌ 모델 로드 실패:', err);
  }
})();

// =====================
// 4. 클래스 정의
// =====================
const classes = ["plastic", "paper", "glass"];

// =====================
// 5. 이미지 예측 함수
// =====================
async function predictImageBuffer(imageBuffer) {
  console.log('🔹 이미지 텐서 변환 중...');
  const tensor = tfnode.node.decodeImage(imageBuffer, 3)
                   .resizeNearestNeighbor([224, 224])
                   .expandDims();

  console.log('🔹 모델 예측 중...');
  const prediction = model.predict(tensor);
  const scores = prediction.dataSync();
  const maxIndex = scores.indexOf(Math.max(...scores));
  const result = { class: classes[maxIndex], confidence: scores[maxIndex] };
  console.log(`✅ 예측 완료: ${result.class} (${result.confidence.toFixed(2)})`);
  return result;
}

// =====================
// 6. Firebase 이벤트 핸들러
// =====================
db.ref('images').on('child_added', async (snapshot) => {
  const imageId = snapshot.key;
  const data = snapshot.val();
  if (!data || !data.url) return;

  console.log(`📸 새 이미지 감지: ${imageId} (URL: ${data.url})`);

  try {
    // 1) 이미지 다운로드
    console.log('🔹 이미지 다운로드 중...');
    const response = await axios.get(data.url, { responseType: 'arraybuffer' });
    const imageBuffer = Buffer.from(response.data, 'binary');

    // 2) AI 모델 예측
    const result = await predictImageBuffer(imageBuffer);

    // 3) 결과 DB 업로드
    console.log('🔹 결과 DB 업로드 중...');
    await db.ref(`results/${imageId}`).set(result);
    console.log(`💾 결과 DB 업로드 완료: ${imageId}`);

    // =====================
    // 7. 쓰레기통 제어 명령 전송
    // =====================
    if (result.class) {
      const binPath = `/bins/${result.class}/cmd/inbox`;

      console.log(`🗑️ 쓰레기통 명령 전송: ${binPath}`);

      await db.ref(binPath).push({
        cmd: "OPEN",
        at: Date.now()
      });

      console.log(`✅ 쓰레기통 열림 명령 전송 완료 → ${binPath}`);
    } else {
      console.log("⚠️ 분류 결과가 유효하지 않아 명령을 전송하지 않음.");
    }

  } catch (err) {
    console.error(`❌ 이미지 처리 실패 (ID: ${imageId}):`, err);
  }
});

// =====================
// 8. 서버 상태 출력
// =====================
console.log('Realtime AI 분석 서버 실행 중...');
console.log('Firebase Realtime DB "images" 노드를 실시간 감시합니다.');
