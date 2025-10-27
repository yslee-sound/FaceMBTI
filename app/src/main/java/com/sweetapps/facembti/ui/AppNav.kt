package com.sweetapps.facembti.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            CameraMainScreen(
                onCaptured = { file ->
                    val path = Uri.encode(file.absolutePath)
                    // 변경: 촬영 후 미리보기 화면으로 이동
                    nav.navigate("preview?path=$path")
                },
                onHistoryClick = { record ->
                    val encoded = Uri.encode(record.imagePath)
                    nav.navigate("result?type=${record.result.type}&score=${record.result.score}&path=$encoded")
                }
            )
        }
        // 새로 추가: 미리보기 화면
        composable(
            route = "preview?path={path}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType; nullable = false }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("path")!!
            val decoded = Uri.decode(path)
            PreviewScreen(
                imagePath = decoded,
                onBack = { nav.popBackStack() },
                onAnalyzeClick = { p ->
                    val encoded = Uri.encode(p)
                    nav.navigate("analyzing?path=$encoded")
                }
            )
        }
        composable(
            route = "analyzing?path={path}",
            arguments = listOf(
                navArgument("path") { type = NavType.StringType; nullable = false }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("path")!!
            AnalyzingScreen(
                imagePath = Uri.decode(path),
                onFinished = { result ->
                    val decodedPath = Uri.decode(path)
                    // 분석 완료 시 히스토리에 추가
                    runCatching { AnalysisHistoryRepository.add(File(decodedPath), result) }
                    // 결과 화면으로 이동: 분석 화면은 제거하고, 미리보기는 유지
                    val type = result.type
                    val score = result.score
                    val p = Uri.encode(decodedPath)
                    nav.popBackStack() // analyzing 제거
                    nav.navigate("result?type=$type&score=$score&path=$p")
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = "result?type={type}&score={score}&path={path}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("score") { type = NavType.IntType },
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "INFP"
            val score = backStackEntry.arguments?.getInt("score") ?: 85
            val path = backStackEntry.arguments?.getString("path")
            ResultScreen(
                imagePath = path?.let { Uri.decode(it) },
                type = type,
                score = score,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
