package com.example.smarttrashproject2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 100
        private const val REQUEST_IMAGE_CAPTURE = 101
        private const val REQUEST_GALLERY_PICK = 102
    }

    private lateinit var btnCapture: ImageView
    private lateinit var btnGallery: ImageView

    private lateinit var instructionText: TextView

    private var photoUri: Uri? = null

    private val DB_URL = "https://smarttrashproject-1a495-default-rtdb.firebaseio.com"
    private lateinit var userSelected: String
    private var BIN_ID: String? = null

    // 🔹 한글 → 영어 매핑
    private fun mapToLabel(korean: String): String = when (korean.trim()) {
        "플라스틱" -> "plastic"
        "유리" -> "glass"
        "종이" -> "paper"
        else -> korean.lowercase()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        btnCapture = findViewById(R.id.btnCapture)
        btnGallery = findViewById(R.id.btnGallery)
        instructionText = findViewById(R.id.textInstruction)

        // 🔹 Firebase 익명 로그인
        FirebaseAuth.getInstance().signInAnonymously()

        // 🔹 선택된 카테고리 (한글)
        val rawKorean = intent.getStringExtra("category") ?: "플라스틱"

        // 🔹 영어 라벨로 변환
        userSelected = mapToLabel(rawKorean)
        BIN_ID = userSelected

        // 🔹 선택 안내 문구 & 필수 안내 문구 출력
        val top = "\"${rawKorean}\"을(를) 선택하셨습니다\n"
        val bottom = "쓰레기를 세워서 촬영해주세요"

        val spannable = SpannableString(top + bottom).apply {

            // 첫 줄 → 얇게(normal)
            setSpan(
                StyleSpan(Typeface.NORMAL),
                0,
                top.length,
                0
            )

            // 둘째 줄 → 굵게(bold)
            setSpan(
                StyleSpan(Typeface.BOLD),
                top.length,
                (top + bottom).length,
                0
            )
        }

        instructionText.text = spannable
        instructionText.textAlignment = View.TEXT_ALIGNMENT_CENTER

        // 🔹 카메라 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }

        // 🔹 AI 초기화
        AiClassifier.init(this)

        // 버튼 이벤트
        btnCapture.setOnClickListener { dispatchTakePictureIntent() }
        btnGallery.setOnClickListener { pickImageFromGallery() }
    }

    // 🔸 카메라 앱 열기
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val photoFile: File? = try { createImageFile() }
        catch (ex: IOException) {
            Toast.makeText(this, "사진 저장 실패", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.also {
            photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    // 🔸 갤러리 열기
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY_PICK)
    }

    // 🔸 임시 사진 파일 생성
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    // 🔸 URI → Bitmap
    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e("CameraActivity", "Bitmap 디코딩 실패: ${e.message}")
            null
        }
    }

    // 🔸 카메라/갤러리 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                photoUri?.let { uri ->
                    val bmp = decodeBitmapFromUri(uri)
                    if (bmp != null) analyzeImage(bmp)

                    val intent = Intent(this, ResultActivity::class.java)
                    intent.putExtra("imageUri", uri.toString())
                    startActivity(intent)
                }
            }

            REQUEST_GALLERY_PICK -> {
                data?.data?.let { selectedUri ->
                    val bmp = decodeBitmapFromUri(selectedUri)
                    if (bmp != null) analyzeImage(bmp)

                    val intent = Intent(this, ResultActivity::class.java)
                    intent.putExtra("imageUri", selectedUri.toString())
                    startActivity(intent)
                }
            }
        }
    }

    // 🔸 AI 분석 + Firebase 기록 + 쓰레기통 OPEN 명령
    private fun analyzeImage(bitmap: Bitmap) {
        val result = AiClassifier.classify(bitmap, userSelected)
        val db = FirebaseDatabase.getInstance(DB_URL)
        val now = System.currentTimeMillis()

        // 분석 기록
        val sessionRef = db.getReference("classify_sessions").push()
        val session = mapOf(
            "userSelected" to userSelected,
            "predicted" to result.predicted,
            "confidence" to result.confidence,
            "matched" to result.matched,
            "binId" to BIN_ID,
            "at" to now
        )
        sessionRef.setValue(session)

        // 맞는 쓰레기일 때 → 쓰레기통 OPEN
        if (result.matched && BIN_ID != null) {
            val cmdRef = db.getReference("bins/$BIN_ID/cmd/inbox").push()
            val cmd = mapOf("cmd" to "OPEN", "at" to now)
            cmdRef.setValue(cmd)
        }
    }
}
