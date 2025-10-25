package com.sweetapps.facembti.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 단일 분석 기록을 나타내는 데이터 클래스.
 * @param imagePath 분석된 이미지의 로컬 파일 경로.
 * @param result 분석 결과(MBTI 유형 및 점수).
 * @param timestamp 분석이 완료된 시간.
 */
data class AnalysisRecord(
    val imagePath: String,
    val result: MbtiResult,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 분석 기록을 메모리 내에서 관리하는 싱글톤 저장소.
 * 앱이 실행되는 동안 최근 5개의 분석 기록을 보관합니다.
 */
object AnalysisHistoryRepository {
    private const val MAX_HISTORY_SIZE = 5

    // 최근 분석 목록을 StateFlow로 관리하여 UI가 관찰할 수 있도록 함
    private val _history = MutableStateFlow<List<AnalysisRecord>>(emptyList())
    val history = _history.asStateFlow()

    /**
     * 새 분석 기록을 추가합니다.
     * 목록의 맨 앞에 새 기록을 추가하고, 최대 크기(5개)를 초과하면 가장 오래된 기록을 제거합니다.
     * @param file 분석된 이미지 파일.
     * @param result 분석 결과.
     */
    fun add(file: File, result: MbtiResult) {
        val newRecord = AnalysisRecord(imagePath = file.absolutePath, result = result)
        val updatedList = (listOf(newRecord) + _history.value).take(MAX_HISTORY_SIZE)
        _history.value = updatedList
    }
}

