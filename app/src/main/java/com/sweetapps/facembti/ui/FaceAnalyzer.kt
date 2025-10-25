package com.sweetapps.facembti.ui

import android.annotation.SuppressLint
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.sqrt
import java.util.concurrent.atomic.AtomicBoolean

/** 간단한 얼굴 메트릭 */
data class FaceMetrics(
    val faceWidth: Float,
    val faceHeight: Float,
    val widthHeightRatio: Float,
    val eyeDistanceRatio: Float?, // 눈 사이 거리 / 얼굴 폭
    val smilingProb: Float?
)

/** 오버레이 렌더링용 좌표/박스 데이터 (회전 반영된 이미지 좌표계) */
data class FaceLandmarkPoint(val x: Float, val y: Float, val type: Int)

data class FaceOverlay(
    val boundingBox: RectF,
    val landmarks: List<FaceLandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
)

/** 분석 종합 결과 */
data class FaceAnalysisResult(
    val metrics: FaceMetrics?,
    val overlay: FaceOverlay?
)

class MlkFaceAnalyzer(
    private val onResult: (FaceAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    }

    private val busy = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (busy.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        busy.set(true)
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val (rotW, rotH) = effectiveSize(imageProxy.width, imageProxy.height, rotation)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                val metrics = face?.let { computeMetrics(it) }
                val overlay = face?.let { buildOverlay(it, rotW, rotH, rotation) }
                onResult(FaceAnalysisResult(metrics, overlay))
            }
            .addOnFailureListener {
                onResult(FaceAnalysisResult(null, null))
            }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }

    private fun effectiveSize(w: Int, h: Int, rotation: Int): Pair<Int, Int> =
        if (rotation % 180 == 0) Pair(w, h) else Pair(h, w)

    private fun computeMetrics(face: Face): FaceMetrics {
        val box = face.boundingBox
        val w = box.width().coerceAtLeast(1)
        val h = box.height().coerceAtLeast(1)
        val ratio = w.toFloat() / h.toFloat()

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val eyeRatio: Float? = if (leftEye != null && rightEye != null) {
            val dx = (leftEye.x - rightEye.x).toDouble()
            val dy = (leftEye.y - rightEye.y).toDouble()
            val dist = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
            (dist / w)
        } else null

        return FaceMetrics(
            faceWidth = w.toFloat(),
            faceHeight = h.toFloat(),
            widthHeightRatio = ratio,
            eyeDistanceRatio = eyeRatio,
            smilingProb = face.smilingProbability
        )
    }

    private fun buildOverlay(face: Face, imgW: Int, imgH: Int, rotation: Int): FaceOverlay {
        val box = face.boundingBox
        val rectF = RectF(
            box.left.toFloat().coerceIn(0f, imgW.toFloat()),
            box.top.toFloat().coerceIn(0f, imgH.toFloat()),
            box.right.toFloat().coerceIn(0f, imgW.toFloat()),
            box.bottom.toFloat().coerceIn(0f, imgH.toFloat())
        )
        val types = intArrayOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EAR,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK
        )
        val points = buildList {
            for (t in types) {
                face.getLandmark(t)?.position?.let { p ->
                    add(FaceLandmarkPoint(p.x, p.y, t))
                }
            }
        }
        return FaceOverlay(rectF, points, imgW, imgH, rotation)
    }
}
