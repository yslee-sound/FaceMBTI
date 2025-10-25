package com.sweetapps.facembti.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

// 추가 import
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.PermissionChecker

@Composable
fun ResultScreen(
    imagePath: String?,
    type: String,
    score: Int,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var bmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(imagePath) {
        if (imagePath != null) {
            bmp = BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
        }
    }

    // API 28 이하 저장 권한 런처
    var deferredSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) deferredSave?.invoke()
        deferredSave = null
    }

    fun captureScreenBitmap(): Bitmap? {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            view.draw(c)
        }
    }

    fun saveScreenshot() {
        val shot = captureScreenBitmap() ?: return
        if (Build.VERSION.SDK_INT <= 28) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PermissionChecker.PERMISSION_GRANTED
            if (!granted) {
                deferredSave = { runCatching { SaveUtils.saveBitmapToGallery(ctx, shot) } }
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        SaveUtils.saveBitmapToGallery(ctx, shot)
        android.widget.Toast.makeText(ctx, "갤러리에 저장되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun shareScreenshot() {
        val shot = captureScreenBitmap() ?: return
        val cache = SaveUtils.saveBitmapToCachePng(ctx, shot)
        val intent = Intent(Intent.ACTION_SEND).apply {
            setType("image/png")
            putExtra(Intent.EXTRA_TEXT, "나의 얼굴 MBTI 결과는 ${type} (${score}%)\n#FaceMBTI")
            val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", cache)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "공유"))
    }

    Surface(color = Color(0xFF12131A)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 상단바 (상태바 안전영역 패딩 적용)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
                }
                Text(
                    text = "분석 결과",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            // 얼굴 이미지(또는 플레이스홀더)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1E26)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "이미지 로딩 중", color = Color(0xFF8FA3B5))
                }
            }

            Spacer(Modifier.height(16.dp))

            // MBTI 카드
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFE934C),
                                    Color(0xFFE456AC),
                                    Color(0xFF6B60F4)
                                )
                            )
                        )
                        .padding(18.dp)
                        .fillMaxWidth()
                ) {
                    Column {
                        Text(text = "당신의 MBTI 유형은?", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(text = type, color = Color.White, fontSize = 42.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 설명 카드
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color(0xFF1B1E25),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(text = "얼굴에 나타난 성격 특성", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "얼굴의 특정 부위(예: 눈, 입꼬리)와 연관 지어 나타나는 성격적 특성에 대한 구체적인 설명. 사용자의 호기심을 자극하고 분석 과정에 대한 신뢰를 더하는 내용.",
                        color = Color(0xFF9BB0C2),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 하단 액션: 왼쪽 공유, 중앙 저장(FAB)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 8.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { shareScreenshot() }) {
                        Icon(Icons.Filled.Share, contentDescription = "공유", tint = Color.White)
                    }
                    Text(text = "공유", color = Color.White, fontSize = 12.sp)
                }

                FloatingActionButton(
                    onClick = { saveScreenshot() },
                    containerColor = Color(0xFF21C0FF),
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "저장")
                }
            }
        }
    }
}
