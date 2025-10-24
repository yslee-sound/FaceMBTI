package com.sweetapps.facembti.ui

import android.annotation.SuppressLint
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

class MlkFaceAnalyzer(
    private val onMetrics: (FaceMetrics?) -> Unit
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
        detector.process(input)
            .addOnSuccessListener { faces ->
                val metrics = faces.firstOrNull()?.let { face ->
                    computeMetrics(face)
                }
                onMetrics(metrics)
            }
            .addOnFailureListener {
                onMetrics(null)
            }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }

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
            val dist = sqrt(dx * dx + dy * dy).toFloat()
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
}
