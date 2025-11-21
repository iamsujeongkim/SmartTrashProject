package com.example.smarttrashproject2

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object AiClassifier {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private const val MODEL_NAME = "model_unquant.tflite" // assets/ 에 넣은 파일명 그대로
    private const val LABELS_NAME = "labels.txt"          // assets/ 에 넣은 파일명 그대로
    private const val INPUT_SIZE = 224                    // Teachable Machine 기본 입력 크기
    private const val THRESHOLD = 0.85f                   // 팀에서 조정

    fun init(context: Context) {
        if (interpreter != null) return
        interpreter = Interpreter(loadModelFile(context, MODEL_NAME))
        labels = loadLabels(context, LABELS_NAME)
        Log.d("AI", "Interpreter init ok. labels=$labels")
    }

    data class Result(val predicted: String, val confidence: Float, val matched: Boolean)

    fun classify(bitmap: Bitmap, userSelected: String): Result {
        // 1) 전처리: 224x224 리사이즈 + 0~255 → 0~1 스케일
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val input = imageProcessor.process(tensorImage)

        // 2) 출력 버퍼: [1, numClasses], float32
        val numClasses = labels.size.coerceAtLeast(1)
        val output = TensorBuffer.createFixedSize(intArrayOf(1, numClasses), input.dataType)

        // 3) 추론
        interpreter!!.run(input.buffer, output.buffer.rewind())

        // 4) 결과 해석 (argmax)
        val scores = output.floatArray
        var maxIdx = 0
        var maxScore = Float.NEGATIVE_INFINITY
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIdx = i
            }
        }
        val predicted = labels.getOrElse(maxIdx) { "unknown" }
        val confidence = maxScore
        val matched = (predicted == userSelected && confidence >= THRESHOLD)

        return Result(predicted, confidence, matched)
    }

    // ---- helpers ----

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        context.assets.openFd(filename).use { afd ->
            afd.createInputStream().channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
            }
        }
    }

    private fun loadLabels(context: Context, filename: String): List<String> {
        val out = mutableListOf<String>()
        BufferedReader(InputStreamReader(context.assets.open(filename))).use { br ->
            br.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    // labels.txt가 "0 can" 형식이면 두 번째 토큰 사용, 아니면 첫 토큰 사용
                    val parts = line.split(Regex("\\s+"))
                    val label = if (parts.size >= 2 && parts[0].all { it.isDigit() }) parts[1] else parts[0]
                    out += label
                }
        }
        return out
    }
}
