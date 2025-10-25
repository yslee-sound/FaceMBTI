package com.sweetapps.facembti.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AnalyzingScreen(
    imagePath: String,
    onFinished: (MbtiResult) -> Unit,
    onBack: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0.05f) }

    LaunchedEffect(imagePath) {
        // 1.5초 진행바 애니메이션 + 분석 (더미)
        val file = File(imagePath)
        val total = 1500
        val step = 50
        var elapsed = 0
        while (elapsed < total) {
            delay(step.toLong())
            elapsed += step
            progress = (elapsed.toFloat() / total).coerceIn(0f, 1f)
        }
        val result = analyzeMbti(file)
        onFinished(result)
    }

    Surface(color = Color(0xFF12131A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
        ) {
            // 상단바: 뒤로가기
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Text(text = "분석 중", color = Color.White, fontSize = 18.sp)
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color(0x22334455))
            Spacer(Modifier.height(18.dp))

            // 본문
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF4EC8FF),
                        trackColor = Color(0x334EC8FF),
                        strokeWidth = 6.dp
                    )
                    Spacer(Modifier.height(28.dp))
                    Text(text = "분석중...", color = Color(0xFFE6EAF2), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(18.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = Color(0xFF4EC8FF),
                        trackColor = Color(0x22334455)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "얼굴을 분석하고 있습니다. 잠시만 기다려주세요.",
                        color = Color(0xFFB3C0CC),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "MBTI 유형이 16가지나 있다는 사실을 알고 계셨나요?",
                        color = Color(0x668FA3B5),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
