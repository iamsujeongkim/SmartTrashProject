// ------------------------------
// Firebase SDK
// ------------------------------
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-app.js";
import {
  getDatabase,
  ref,
  onValue,
  set,
  update,
  get,
  onDisconnect,
  serverTimestamp,
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-database.js";
import {
  getAuth,
  signInAnonymously,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

// ------------------------------
// Firebase Init
// ------------------------------
const firebaseConfig = {
  apiKey: "AIzaSyDJRWOXET6wEts3l4PHnIxLmLOqxBKZTb0",
  authDomain: "smarttrashproject-1a495.firebaseapp.com",
  databaseURL: "https://smarttrashproject-1a495-default-rtdb.firebaseio.com",
  projectId: "smarttrashproject-1a495",
  storageBucket: "smarttrashproject-1a495.appspot.com",
  messagingSenderId: "115591477913",
  appId: "1:115591477913:web:11312055056e7f39e69608",
  measurementId: "G-DP6YD5S4YJ",
};

const app = initializeApp(firebaseConfig);
const db = getDatabase(app);
const auth = getAuth(app);

// ------------------------------
// UI 요소
// ------------------------------
const binList = document.getElementById("bin-list");
let previousStatus = {};
let lastProcessedSession = null;

// ------------------------------
// 평균 통계 계산
// ------------------------------
function calcStats(history) {
  let totalTime = 0;
  let count = 0;
  let lastEmptied = null;

  Object.values(history || {}).forEach((h) => {
    if (h.fullAt && h.emptiedAt) {
      totalTime += h.emptiedAt - h.fullAt;
      count++;
    }
    if (h.emptiedAt && (!lastEmptied || h.emptiedAt > lastEmptied)) {
      lastEmptied = h.emptiedAt;
    }
  });

  const avgSeconds = count ? totalTime / count / 1000 : 0;
  const hours = Math.floor(avgSeconds / 3600);
  const minutes = Math.floor((avgSeconds % 3600) / 60);
  const seconds = Math.floor(avgSeconds % 60);

  let avgText = "";
  if (hours > 0) avgText += `${hours}시간 `;
  if (minutes > 0) avgText += `${minutes}분 `;
  avgText += `${seconds}초`;

  const recommend =
    lastEmptied && avgSeconds
      ? new Date(lastEmptied + avgSeconds * 1000).toLocaleString("ko-KR")
      : "-";

  return {
    avgTimeText: avgText,
    lastEmptied: lastEmptied ? new Date(lastEmptied).toLocaleString("ko-KR") : "-",
    recommendDate: recommend,
  };
}

// ------------------------------
// 상태 변화 감지 → history 갱신
// ------------------------------
function handleStatusChange(binId, oldStatus, newStatus) {
  const now = Date.now();
  const today = new Date().toISOString().split("T")[0];

  const historyRef = ref(db, `bins/${binId}/history/${today}`);
  const lastRef = ref(db, `bins/${binId}/last`);

  if (oldStatus === "FULL" && (newStatus === "OK" || newStatus === "EMPTY")) {
    update(historyRef, { emptiedAt: now });
    update(lastRef, { emptiedAt: now });
  } else if ((oldStatus === "OK" || oldStatus === "EMPTY") && newStatus === "FULL") {
    update(historyRef, { fullAt: now });
    update(lastRef, { fullAt: now });
  }

  update(lastRef, { at: now });
}

// ------------------------------
// 렌더링 함수
// ------------------------------
function renderBins(bins) {
  binList.innerHTML = "";

  Object.entries(bins).forEach(([key, bin]) => {
    // ------------------------
    // 채움 정도 계산 (history 기반 최신값 사용)
    // ------------------------
    let fill = "-";

    if (bin.history) {
      const entries = Object.values(bin.history);

      if (entries.length > 0) {
        // 1) Firebase push() 순서 그대로 → 마지막 항목이 최신
        let latest = entries[entries.length - 1];

        // 2) 만약 최신 항목에 level/distance_cm 없으면, 뒤에서부터 찾기
        if (
          latest.level === undefined &&
          latest.distance_cm === undefined
        ) {
          for (let i = entries.length - 1; i >= 0; i--) {
            if (
              entries[i].level !== undefined ||
              entries[i].distance_cm !== undefined
            ) {
              latest = entries[i];
              break;
            }
          }
        }

        // 3) fill 계산
        if (latest.level !== undefined) {
          fill = Number(latest.level);
        } else if (latest.distance_cm !== undefined) {
          fill = 100 - Number(latest.distance_cm);
        }
      }
    }

    // 값 정리
    if (!isNaN(fill)) {
      fill = Math.max(0, Math.min(100, Math.round(fill)));
    } else {
      fill = "-";
    }

    // ------------------------
    // 기타 상태값 처리
    // ------------------------
    const status =
      bin.status || bin.last?.status || "N/A";

    const lastFull = bin.last?.fullAt
      ? new Date(bin.last.fullAt).toLocaleString("ko-KR")
      : "-";

    const lastEmpty = bin.last?.emptiedAt
      ? new Date(bin.last.emptiedAt).toLocaleString("ko-KR")
      : "-";

    const formattedDate = bin.last?.formattedDate || "-";

    const stats = calcStats(bin.history || {});

    // ------------------------
    // 카드 렌더링
    // ------------------------
    const card = document.createElement("div");
    card.className = `bin-card ${status.toLowerCase()}`;
    card.innerHTML = `
      <h3>${key.toUpperCase()}</h3>
      <p><strong>채움 정도:</strong> ${fill}%</p>
      <p><strong>상태:</strong> ${status}</p>
      <p><strong>최근 채워짐:</strong> ${lastFull}</p>
      <p><strong>최근 비움:</strong> ${lastEmpty}</p>
      <p><strong>마지막 업데이트:</strong> ${formattedDate}</p>
      <hr>
      <p><strong>평균 채움 속도:</strong> ${stats.avgTimeText}</p>
      <p><strong>최근 비움 시각:</strong> ${stats.lastEmptied}</p>
      <p><strong>비움 권장 시각:</strong> ${stats.recommendDate}</p>
    `;
    binList.appendChild(card);
  });
}

// ------------------------------
// 뚜껑 열기
// ------------------------------
async function sendOpenCommand(binId) {
  try {
    const msgId = `cmd_${Date.now()}`;
    await set(ref(db, `bins/${binId}/cmd/inbox/${msgId}`), {
      cmd: "OPEN",
      at: Date.now(),
    });
    console.log(`🟩 명령 전송 완료 → ${binId}`);
  } catch (err) {
    console.error("❌ 명령 전송 실패:", err);
  }
}

// ------------------------------
// 인증 및 전체 흐름
// ------------------------------
signInAnonymously(auth);

onAuthStateChanged(auth, async (user) => {
  if (!user) return;

  const uid = user.uid;
  console.log("관리자 UID:", uid);

  const adminSnap = await get(ref(db, `admins/${uid}`));
  if (!adminSnap.exists() || adminSnap.val() !== true) {
    console.warn("⚠ 관리자 아님");
    return;
  }

  const pRef = ref(db, `admin_presence/${uid}`);
  await set(pRef, { online: true, at: serverTimestamp() });
  onDisconnect(pRef).remove();

  // ----------------------
  // bins 실시간 감시
  // ----------------------
  const binsRef = ref(db, "bins");
  onValue(binsRef, (snapshot) => {
    const bins = snapshot.val();
    if (!bins) return;

    renderBins(bins);

    Object.entries(bins).forEach(([binId, binData]) => {
      const curr = binData.status || binData.last?.status;
      const prev = previousStatus[binId];

      if (prev && curr && prev !== curr) {
        handleStatusChange(binId, prev, curr);
      }

      previousStatus[binId] = curr;
    });
  });

  // ----------------------
  // formattedDate 자동 업데이트
  // ----------------------
  ["glass", "paper", "plastic"].forEach((t) => {
    const atRef = ref(db, `bins/${t}/last/at`);
    onValue(atRef, async (snap) => {
      const ts = snap.val();
      if (!ts) return;

      const newFormatted = new Date(ts).toLocaleString("ko-KR");

      const oldSnap = await get(
        ref(db, `bins/${t}/last/formattedDate`)
      );

      if (oldSnap.val() !== newFormatted) {
        update(ref(db, `bins/${t}/last`), {
          formattedDate: newFormatted,
        });
      }
    });
  });

  // ----------------------
  // 자동 분리수거 → 뚜껑 열기
  // ----------------------
  const sessionsRef = ref(db, "classify_sessions");
  onValue(sessionsRef, (snapshot) => {
    const data = snapshot.val();
    if (!data) return;

    const entries = Object.entries(data);
    if (!entries.length) return;

    const [sid, latest] = entries.sort((a, b) => (b[1].at || 0) - (a[1].at || 0))[0];

    if (sid === lastProcessedSession) return;

    if (latest.matched && latest.predicted === latest.userSelected) {
      const binId = latest.binId || latest.bind;
      if (binId) sendOpenCommand(binId);
      lastProcessedSession = sid;
    }
  });
});
