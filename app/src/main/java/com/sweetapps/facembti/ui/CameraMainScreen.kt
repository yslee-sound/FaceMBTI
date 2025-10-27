package com.sweetapps.facembti.ui

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import java.io.File

@Composable
fun CameraMainScreen(
    modifier: Modifier = Modifier,
    onCaptured: (File) -> Unit = {},
    onHistoryClick: (AnalysisRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 권한
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) { if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    // CameraX 컨트롤러
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    var isFront by remember { mutableStateOf(true) }
    LaunchedEffect(isFront) {
        cameraController.cameraSelector = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    LaunchedEffect(hasCameraPermission) { if (hasCameraPermission) runCatching { cameraController.bindToLifecycle(lifecycleOwner) } }

    // 최근 분석 히스토리
    val history by AnalysisHistoryRepository.history.collectAsState()

    // 가이드 표시 여부
    var showGuide by remember { mutableStateOf(true) }

    fun takePhoto() {
        val photoFile = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        cameraController.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onCaptured(photoFile)
                }
                override fun onError(exception: ImageCaptureException) {
                    android.widget.Toast.makeText(context, "촬영 실패: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFF0F121A)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 카메라 프리뷰 (상단바 등 겹침 방지용 패딩 포함)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                controller = cameraController
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        }
                    )
                } else {
                    Text(
                        text = "카메라 권한이 필요합니다",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // 얼굴 정렬 가이드 오버레이 + 토글
                // AndroidView 위에 겹쳐서 그립니다.
                if (showGuide && hasCameraPermission) {
                    FaceGuideOverlay(modifier = Modifier.matchParentSize())
                    FaceGuideTips(modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp))
                }
                Surface(
                    onClick = { showGuide = !showGuide },
                    color = Color(0x66000000),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (showGuide) "가이드 끄기" else "가이드 켜기",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // 하단 영역: 최근 분석 + 촬영 버튼
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10131B))
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                Text(text = "최근 분석", color = Color(0xFFCAD2DC), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    history.take(5).forEach { record ->
                        val img = remember(record.imagePath) {
                            BitmapFactory.decodeFile(record.imagePath)?.asImageBitmap()
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A313A))
                                .clickable { onHistoryClick(record) }
                                .padding(0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (img != null) {
                                Image(bitmap = img, contentDescription = null)
                            }
                            // 타입 라벨(간단한 텍스트 오버레이)
                            Text(text = record.result.type, color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0x22334455))
                Spacer(Modifier.height(10.dp))

                // 촬영 라벨 + 버튼(두꺼운 링) (좌측 썸네일 제거)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "촬영", color = Color(0xFFCAD2DC))
                    Spacer(Modifier.height(8.dp))

                    // 가운데 촬영 버튼만 표시
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .clickable { takePhoto() },
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(4.dp, Color(0xFF27C7A8))
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 얼굴 타원 영역 (중앙, 화면 대비 비율)
        val ovalW = w * 0.6f
        val ovalH = h * 0.45f
        val left = (w - ovalW) / 2f
        val top = (h - ovalH) / 2f
        // 타원 테두리
        drawOval(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(left, top),
            size = Size(ovalW, ovalH),
            style = Stroke(width = 3.dp.toPx())
        )
        // 눈 높이 가이드 (타원 상단에서 0.4 비율 지점)
        val eyeY = top + ovalH * 0.4f
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(left + 20.dp.toPx(), eyeY),
            end = Offset(left + ovalW - 20.dp.toPx(), eyeY),
            strokeWidth = 2.dp.toPx()
        )
        // 수직 중심선
        val cx = w / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(cx, top + 12.dp.toPx()),
            end = Offset(cx, top + ovalH - 12.dp.toPx()),
            strokeWidth = 1.5.dp.toPx()
        )
        // 모서리 L자 가이드 (얼굴 프레이밍 참고용)
        val corner = 26.dp.toPx()
        val stroke = 3.dp.toPx()
        val rectLeft = left - 24.dp.toPx()
        val rectTop = top - 24.dp.toPx()
        val rectRight = left + ovalW + 24.dp.toPx()
        val rectBottom = top + ovalH + 24.dp.toPx()
        // 좌상
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectLeft, rectTop), Offset(rectLeft + corner, rectTop), stroke)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectLeft, rectTop), Offset(rectLeft, rectTop + corner), stroke)
        // 우상
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectRight - corner, rectTop), Offset(rectRight, rectTop), stroke)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectRight, rectTop), Offset(rectRight, rectTop + corner), stroke)
        // 좌하
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectLeft, rectBottom - corner), Offset(rectLeft, rectBottom), stroke)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectLeft, rectBottom), Offset(rectLeft + corner, rectBottom), stroke)
        // 우하
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectRight, rectBottom - corner), Offset(rectRight, rectBottom), stroke)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(rectRight - corner, rectBottom), Offset(rectRight, rectBottom), stroke)
    }
}

@Composable
private fun FaceGuideTips(modifier: Modifier = Modifier) {
    Surface(
        color = Color(0x66000000),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "정면을 바라보고 타원 안에 얼굴을 맞춰주세요", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(text = "밝은 곳에서 배경 단순, 안경/모자는 벗어주세요", color = Color(0xFFCAD2DC), fontSize = 12.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraMainScreenPreview() {
    MaterialTheme { CameraMainScreen() }
}
