package com.sweetapps.facembti.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PreviewScreen(
    imagePath: String,
    onBack: () -> Unit,
    onAnalyzeClick: (String) -> Unit
) {
    var bmp by remember(imagePath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(imagePath) {
        bmp = BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
    }

    Surface(color = Color(0xFF12131A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 상단바
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
                    text = "미리보기",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            // 이미지 미리보기
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1E26)),
                contentAlignment = Alignment.Center
            ) {
                val localBmp = bmp
                if (localBmp != null) {
                    Image(
                        bitmap = localBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "이미지 로딩 중", color = Color(0xFF8FA3B5))
                }
            }

            Spacer(Modifier.height(24.dp))

            // 하단 버튼: MBTI 분석으로 이동
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                Button(
                    onClick = { onAnalyzeClick(imagePath) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27C7A8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.Center)
                ) {
                    Text(text = "MBTI", fontSize = 18.sp, color = Color.White)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreenPreview() {
    PreviewScreen(
        imagePath = "",
        onBack = {},
        onAnalyzeClick = {}
    )
}
