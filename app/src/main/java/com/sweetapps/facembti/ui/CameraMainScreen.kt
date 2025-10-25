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

    // 마지막 촬영 썸네일(갤러리 저장 없이 캐시 기준)
    var lastShotPath by remember { mutableStateOf<String?>(null) }
    val lastShotBitmap by remember(lastShotPath, history) {
        mutableStateOf(
            lastShotPath?.let { p -> BitmapFactory.decodeFile(p)?.asImageBitmap() }
                ?: history.firstOrNull()?.let { BitmapFactory.decodeFile(it.imagePath)?.asImageBitmap() }
        )
    }

    fun takePhoto() {
        val photoFile = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        cameraController.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    lastShotPath = photoFile.absolutePath
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

                // 촬영 라벨 + 버튼(두꺼운 링) + 좌측 썸네일
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "촬영", color = Color(0xFFCAD2DC))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 왼쪽 정사각형 썸네일 버튼
                        val thumbSize = 112.dp
                        Box(
                            modifier = Modifier
                                .size(thumbSize)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B0E14))
                                .clickable {
                                    // 가장 최근 기록 열기
                                    history.firstOrNull()?.let { onHistoryClick(it) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (lastShotBitmap != null) {
                                Image(bitmap = lastShotBitmap!!, contentDescription = null)
                            }
                        }

                        // 가운데 촬영 링을 정확히 중앙에 배치하기 위한 가변 공간
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier
                                    .size(86.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .clickable { takePhoto() },
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(4.dp, Color(0xFF27C7A8))
                            ) {}
                        }

                        // 오른쪽은 왼쪽 썸네일과 동일한 폭의 빈 공간으로 균형 유지
                        Spacer(modifier = Modifier.size(thumbSize))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraMainScreenPreview() {
    MaterialTheme { CameraMainScreen() }
}
