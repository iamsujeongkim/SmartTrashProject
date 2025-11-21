package com.example.smarttrashproject2

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var ref: DatabaseReference
    private lateinit var tvData: TextView
    private val DB_URL = "https://smarttrashproject-1a495-default-rtdb.firebaseio.com"  // 슬래시 없음 중요!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvData = findViewById(R.id.textViewData)

        // 0) 익명 로그인 먼저 진행
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("Auth", "✅ 익명 로그인 성공 uid=${it.user?.uid}")
                    initDatabase()  // 로그인 성공 후 DB 초기화
                }
                .addOnFailureListener { e ->
                    Log.e("Auth", "❌ 익명 로그인 실패", e)
                    Toast.makeText(this, "로그인 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            Log.d("Auth", "이미 로그인됨 uid=${auth.currentUser?.uid}")
            initDatabase()
        }
    }

    private fun initDatabase() {
        // 1) DB 인스턴스 + 참조
        database = FirebaseDatabase.getInstance(DB_URL)
        ref = database.getReference("RecycleCategories")

        // (옵션) 어떤 프로젝트로 붙었는지 확인 로그
        val opts = FirebaseApp.getInstance().options
        Log.d("FB", "projectId=${opts.projectId}, dbUrl=${opts.databaseUrl}")

        // 2) healthcheck 쓰기 테스트
        val healthRef = database.getReference("healthcheck")
        healthRef.setValue("ok:${System.currentTimeMillis()}")
            .addOnSuccessListener { Log.d("DB", "✅ healthcheck saved") }
            .addOnFailureListener { e -> Log.e("DB", "❌ healthcheck failed", e) }

        // 3) 초기 데이터: DB가 비어있을 때만 1회 세팅
        seedIfEmpty()


        // 4) 실시간 리스너
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.hasChildren()) {
                    tvData.text = "데이터가 없습니다."
                    Log.d("Firebase", "RecycleCategories: (empty)")
                    return
                }

                val sb = StringBuilder()
                for (categorySnapshot in snapshot.children) {
                    val name = categorySnapshot.key ?: "(unknown)"
                    val level = categorySnapshot.child("level").getValue(Int::class.java) ?: 0
                    val status = categorySnapshot.child("status").getValue(String::class.java) ?: "unknown"

                    sb.append("• $name  →  level=$level%, status=$status\n")
                    Log.d("Firebase", "$name : level=$level, status=$status")
                }
                tvData.text = sb.toString().trimEnd()
            }

            override fun onCancelled(error: DatabaseError) {
                tvData.text = "읽기 실패: ${error.message}"
                Log.e("Firebase", "읽기 실패", error.toException())
            }
        })
    }

    /** DB 비어있을 때만 한 번 기본 데이터 넣기 */
    private fun seedIfEmpty() {
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("Firebase", "Seed skip (already exists)")
                    return
                }
                val testData = mapOf(
                    "plastic" to mapOf("level" to 77, "status" to "warning"),
                    "glass"   to mapOf("level" to 10, "status" to "ok"),
                    "can"     to mapOf("level" to 95, "status" to "full")
                )
                ref.setValue(testData)
                    .addOnSuccessListener { Log.d("Firebase", "✅ Seed OK") }
                    .addOnFailureListener { e -> Log.e("Firebase", "❌ Seed FAIL", e) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Seed check cancelled", error.toException())
            }
        })
    }
}
