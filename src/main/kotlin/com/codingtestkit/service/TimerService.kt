package com.codingtestkit.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * 카운트다운 타이머 상태 서비스 (이슈 #16).
 * 타이머 탭, 문제 탭 하단 타이머 바, 상태바 위젯이 같은 타이머를 공유·관찰한다.
 * Swing Timer 기반이므로 모든 호출과 콜백은 EDT에서 일어난다.
 */
@Service(Service.Level.PROJECT)
class TimerService {

    var totalMs: Long = DEFAULT_TOTAL_MS
        private set
    var remainingMs: Long = DEFAULT_TOTAL_MS
        private set
    var isRunning: Boolean = false
        private set

    /** 중간에 일시정지된 상태 (시작/재개 라벨, 필드 활성화 판단용) */
    val isPausedMidway: Boolean
        get() = !isRunning && remainingMs in 1 until totalMs

    private var endTime: Long = 0
    private var ticker: javax.swing.Timer? = null
    private val listeners = mutableListOf<() -> Unit>()
    private val timeUpListeners = mutableListOf<() -> Unit>()

    /** 총 시간 설정 (남은 시간도 함께 리셋). 실행 중에는 무시. */
    fun setTotal(ms: Long) {
        if (isRunning) return
        totalMs = ms.coerceAtLeast(0)
        remainingMs = totalMs
        notifyListeners()
    }

    /** 시작 또는 재개. 남은 시간이 없으면 총 시간부터 다시 시작. */
    fun start() {
        if (isRunning) return
        if (remainingMs <= 0) remainingMs = totalMs
        if (remainingMs <= 0) return
        isRunning = true
        endTime = System.currentTimeMillis() + remainingMs
        ticker = javax.swing.Timer(33) {
            remainingMs = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
            if (remainingMs <= 0) finish() else notifyListeners()
        }.apply { start() }
        notifyListeners()
    }

    fun pause() {
        if (!isRunning) return
        remainingMs = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
        stopTicker()
        notifyListeners()
    }

    fun reset() {
        stopTicker()
        remainingMs = totalMs
        notifyListeners()
    }

    private fun finish() {
        stopTicker()
        remainingMs = 0
        notifyListeners()
        timeUpListeners.toList().forEach { it() }
    }

    private fun stopTicker() {
        ticker?.stop()
        ticker = null
        isRunning = false
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    /** 시간 종료 시 1회 호출되는 리스너 (알림음·다이얼로그 등) */
    fun addTimeUpListener(listener: () -> Unit) { timeUpListeners.add(listener) }

    private fun notifyListeners() { listeners.toList().forEach { it() } }

    /** 남은 시간 표기: h:mm:ss 또는 mm:ss (문제 탭 바, 상태바 공용) */
    fun formatRemaining(): String {
        val totalSec = remainingMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    companion object {
        private const val DEFAULT_TOTAL_MS = 30 * 60 * 1000L

        fun getInstance(project: Project): TimerService =
            project.getService(TimerService::class.java)
    }
}
