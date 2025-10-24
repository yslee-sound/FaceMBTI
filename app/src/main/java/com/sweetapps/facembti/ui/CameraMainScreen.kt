package com.sweetapps.facembti.ui

import android.Manifest
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalDensity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import java.util.Locale

private enum class AspectRatioMode { FULL, R3_4, R1_1 }

private data class MbtiResult(val type: String, val score: Int)

@Composable
fun CameraMainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // 권한
    var hasCameraPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED
    ) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // CameraX 컨트롤러
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    // 얼굴 메트릭 상태 + 분석기 연결
    var faceMetrics by remember { mutableStateOf<FaceMetrics?>(null) }
    LaunchedEffect(cameraController) {
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            MlkFaceAnalyzer { metrics -> faceMetrics = metrics }
        )
    }

    // 상태: 전/후면, 화면비율, 플래시, 타이머
    var isFront by remember { mutableStateOf(true) }
    var ratioMode by remember { mutableStateOf(AspectRatioMode.FULL) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timerSec by remember { mutableStateOf(0) } // 0 / 3 / 5
    var counting by remember { mutableStateOf(false) }
    var countDown by remember { mutableStateOf(0) }

    LaunchedEffect(isFront) {
        cameraController.cameraSelector = if (isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }
    LaunchedEffect(flashMode) {
        cameraController.imageCaptureFlashMode = flashMode
    }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            try { cameraController.bindToLifecycle(lifecycleOwner) } catch (_: Exception) {}
        }
    }

    // 썸네일
    var lastThumbPath by remember { mutableStateOf<String?>(null) }
    var lastThumbBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // MBTI 결과 시트 상태
    var mbtiResult by remember { mutableStateOf<MbtiResult?>(null) }
    var showResult by remember { mutableStateOf(false) }

    LaunchedEffect(lastThumbPath) {
        lastThumbPath?.let { path ->
            val bmp = BitmapFactory.decodeFile(path)
            lastThumbBitmap = bmp?.asImageBitmap()
        }
    }

    // WRITE_EXTERNAL_STORAGE 권한 런처 (API <= 28)
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    fun requestWriteIfNeeded(): Boolean {
        return if (Build.VERSION.SDK_INT <= 28) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PermissionChecker.PERMISSION_GRANTED
            if (!granted) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            granted
        } else true
    }

    suspend fun saveToGallery(file: File) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FaceMBTI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } else {
            // API 28 이하: 공용 Pictures/FaceMBTI 로 복사
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(dir, "FaceMBTI").apply { if (!exists()) mkdirs() }
            val target = File(targetDir, file.name)
            withContext(Dispatchers.IO) {
                file.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "갤러리에 저장됨", Toast.LENGTH_SHORT).show()
        }
    }

    // 촬영
    val takePhotoImmediate: () -> Unit = {
        val photoFile = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        cameraController.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    lastThumbPath = photoFile.absolutePath
                    // 더미 MBTI 분석 트리거
                    mbtiResult = analyzeMbti(photoFile)
                    showResult = true
                    // 갤러리 저장 (권한 필요 시 요청만 하고 이번 촬영은 캐시에 유지)
                    if (Build.VERSION.SDK_INT >= 29 || requestWriteIfNeeded()) {
                        scope.launch(Dispatchers.IO) {
                            runCatching { saveToGallery(photoFile) }
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) { }
            }
        )
    }

    val onPressShutter: () -> Unit = {
        if (!hasCameraPermission || counting) {
            if (!hasCameraPermission) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            // 조기 반환 대신 분기 종료
        } else if (timerSec <= 0) {
            takePhotoImmediate()
        } else {
            counting = true
            scope.launch {
                for (i in timerSec downTo 1) {
                    countDown = i
                    delay(1000)
                }
                countDown = 0
                counting = false
                takePhotoImmediate()
            }
        }
    }

    // PreviewView 참조 보관 (포커스 좌표 변환용)
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // 포커스 링 UI 상태
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusVisible by remember { mutableStateOf(false) }

    fun showFocusRing(pt: Offset) {
        focusPoint = pt
        focusVisible = true
        // 1초 후 숨김
        scope.launch {
            delay(1000)
            focusVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 프리뷰 컨테이너: 화면비율 반영
        if (hasCameraPermission) {
            Box(
                modifier = when (ratioMode) {
                    AspectRatioMode.FULL -> Modifier.fillMaxSize()
                    AspectRatioMode.R3_4 -> Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f/4f)
                        .align(Alignment.Center)
                    AspectRatioMode.R1_1 -> Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.Center)
                }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            this.controller = cameraController
                            this.scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 제스처 레이어 (탭 포커스 + 핀치 줌)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val pv = previewViewRef ?: return@detectTapGestures
                                val factory: MeteringPointFactory =
                                    SurfaceOrientedMeteringPointFactory(
                                        pv.width.toFloat(), pv.height.toFloat()
                                    )
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                // CameraX 버전에 따라 controller에 직접 호출이 없을 수 있으므로 cameraControl 통해 호출
                                cameraController.cameraControl?.startFocusAndMetering(action)
                                showFocusRing(offset)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                val zs = cameraController.zoomState.value
                                val current = zs?.zoomRatio ?: 1f
                                val minZ = zs?.minZoomRatio ?: 1f
                                val maxZ = zs?.maxZoomRatio ?: 10f
                                val newRatio = (current * zoomChange).coerceIn(minZ, maxZ)
                                cameraController.setZoomRatio(newRatio)
                            }
                        }
                )

                // 포커스 링 렌더링
                if (focusVisible && focusPoint != null) {
                    val size = 64.dp
                    val ringPx = with(LocalDensity.current) { size.toPx() }
                    Box(
                        modifier = Modifier
                            .size(size)
                            .offset { androidx.compose.ui.unit.IntOffset(
                                (focusPoint!!.x - ringPx / 2).toInt(),
                                (focusPoint!!.y - ringPx / 2).toInt()
                            ) }
                            .border(2.dp, Color.Yellow, CircleShape)
                    )
                }
            }
        } else {
            Text(
                text = "카메라 권한이 필요합니다",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 분석 수치 오버레이 (좌상단)
        faceMetrics?.let { m ->
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 80.dp, start = 12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = "비율 ${String.format(Locale.KOREA, "%.2f", m.widthHeightRatio)}", color = Color.White, fontSize = 12.sp)
                    if (m.eyeDistanceRatio != null) {
                        Text(text = "눈간격 ${String.format(Locale.KOREA, "%.2f", m.eyeDistanceRatio)}", color = Color.White, fontSize = 12.sp)
                    }
                    if (m.smilingProb != null) {
                        Text(text = "스마일 ${String.format(Locale.KOREA, "%.0f%%", m.smilingProb * 100)}", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // 카운트다운 오버레이
        if (countDown > 0) {
            Text(
                text = countDown.toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        TopBars(
            selectedQuality = "기본",
            onQualityChange = { /* TODO */ },
            isFront = isFront,
            onToggleCamera = { isFront = !isFront },
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        )

        LeftTools(
            flashMode = flashMode,
            onToggleFlash = {
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
            },
            timerSec = timerSec,
            onToggleTimer = {
                timerSec = when (timerSec) { 0 -> 3; 3 -> 5; else -> 0 }
            },
            ratioMode = ratioMode,
            onToggleRatio = {
                ratioMode = when (ratioMode) {
                    AspectRatioMode.FULL -> AspectRatioMode.R3_4
                    AspectRatioMode.R3_4 -> AspectRatioMode.R1_1
                    AspectRatioMode.R1_1 -> AspectRatioMode.FULL
                }
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )

        BottomControls(
            mode = "촬영",
            onModeChange = { /*TODO*/ },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            thumb = lastThumbBitmap,
            onShutter = onPressShutter
        )
    }

    // 결과 시트 렌더링
    if (showResult && mbtiResult != null) {
        MbtiResultSheet(
            result = mbtiResult!!,
            onDismiss = { showResult = false }
        )
    }
}

private fun analyzeMbti(file: File): MbtiResult {
    val types = listOf(
        "INTJ","INTP","ENTJ","ENTP",
        "INFJ","INFP","ENFJ","ENFP",
        "ISTJ","ISFJ","ESTJ","ESFJ",
        "ISTP","ISFP","ESTP","ESFP"
    )
    // 파일명 해시 기반의 결정적 선택
    val hash = abs(file.name.hashCode())
    val type = types[hash % types.size]
    val score = 70 + (hash % 31) // 70~100
    return MbtiResult(type, score)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MbtiResultSheet(result: MbtiResult, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "당신의 MBTI는", color = Color.Black, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = result.type, color = Color(0xFF4EC8FF), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { result.score / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF4EC8FF)
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "근접도 ${result.score}%", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("리트라이") }
                Button(onClick = onDismiss) { Text("확인") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TopBars(
    selectedQuality: String,
    onQualityChange: (String) -> Unit,
    isFront: Boolean,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        QualityChips(selectedQuality, onQualityChange)

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 녹색 인디케이터 (카메라 활성 표기 느낌)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2AD37A))
            )
            Spacer(Modifier.width(12.dp))
            // 카메라 전/후면 전환
            IconButton(onClick = onToggleCamera) {
                Icon(
                    imageVector = Icons.Default.SwitchCamera,
                    contentDescription = if (isFront) "후면 카메라" else "전면 카메라",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
private fun QualityChips(selected: String, onChange: (String) -> Unit) {
    val items = listOf("기본", "고화질", "아이폰")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .border(1.dp, Color(0x22000000), RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { label ->
            val selectedState = label == selected
            FilterChip(
                selected = selectedState,
                onClick = { onChange(label) },
                label = {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selectedState) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                modifier = Modifier.padding(horizontal = 2.dp),
                leadingIcon = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = Color(0xFFF1F7FF),
                    labelColor = Color.Black,
                    selectedLabelColor = Color(0xFF0A84FF)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedState
                )
            )
        }
    }
}

@Composable
private fun LeftTools(
    flashMode: Int,
    onToggleFlash: () -> Unit,
    timerSec: Int,
    onToggleTimer: () -> Unit,
    ratioMode: AspectRatioMode,
    onToggleRatio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val flashIcon = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
            else -> Icons.Default.FlashOff
        }
        val flashText = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> "켜짐"
            ImageCapture.FLASH_MODE_AUTO -> "자동"
            else -> "꺼짐"
        }
        SideTool(icon = flashIcon, label = "플래시", subLabel = flashText, onClick = onToggleFlash)

        SideTool(icon = Icons.Default.AutoFixHigh, label = "보정")

        val timerText = if (timerSec == 0) "끄기" else "${timerSec}s"
        SideTool(icon = Icons.Default.Timer, label = "타이머", subLabel = timerText, onClick = onToggleTimer)

        val ratioText = when (ratioMode) {
            AspectRatioMode.FULL -> "풀"
            AspectRatioMode.R3_4 -> "3:4"
            AspectRatioMode.R1_1 -> "1:1"
        }
        SideTool(icon = Icons.Default.AspectRatio, label = "화면비율", subLabel = ratioText, onClick = onToggleRatio)

        SideTool(icon = Icons.Default.ExpandLess, label = "접기")
    }
}

@Composable
private fun SideTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = clickableMod) {
        Surface(
            color = Color.White.copy(alpha = 0.9f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = CircleShape
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.padding(8.dp),
                tint = Color.Black
            )
        }
        Text(
            text = label,
            color = Color(0x99000000),
            fontSize = 11.sp
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                color = Color(0x99000000),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun BottomControls(
    mode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    thumb: androidx.compose.ui.graphics.ImageBitmap?,
    onShutter: () -> Unit
) {
    Column(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xAA000000))
            )
        )
    ) {
        // 상단에 모드 탭 (촬영/비디오)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            ModeTab("촬영", mode == "촬영") { onModeChange("촬영") }
            Spacer(Modifier.width(20.dp))
            ModeTab("비디오", mode == "비디오") { onModeChange("비디오") }
        }

        Spacer(Modifier.height(12.dp))

        // 셔터 영역
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 좌측 썸네일
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = "thumbnail",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            ShutterButton(size = 90.dp, onClick = onShutter)

            // 우측 카메라/AI 토글 자리 (간단한 아이콘 버튼)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* TODO gallery or options */ }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "사진", tint = Color.White)
                }
                IconButton(onClick = { /* TODO AI mode */ }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = Color.White)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 하단 기능 버튼들 (보정/이펙트/뷰티/필터)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomFeature(icon = Icons.Default.Tune, label = "보정")
            BottomFeature(icon = Icons.Default.Mood, label = "이펙트")
            BottomFeature(icon = Icons.Default.Brush, label = "뷰티")
            BottomFeature(icon = Icons.Default.FilterList, label = "필터")
        }

        // 하단 바 (홈/검색/촬영/AI/마이)
        BottomNavBar()
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color(0xFF4EC8FF) else Color(0x66FFFFFF),
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ShutterButton(size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(4.dp, Color(0xFF59D2FF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.7f)
                .clip(CircleShape)
                .background(Color(0xFFE6F7FF))
                .border(2.dp, Color(0xFF59D2FF), CircleShape)
        )
    }
}

@Composable
private fun BottomFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun BottomNavBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(Icons.Default.Home, "홈")
        NavItem(Icons.Default.Search, "검색")
        NavItem(Icons.Default.PhotoCamera, "촬영")
        NavItem(Icons.Default.AutoAwesome, "AI")
        NavItem(Icons.Default.Person, "MY")
    }
}

@Composable
private fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun CameraMainScreenPreview() {
    MaterialTheme {
        CameraMainScreen()
    }
}
