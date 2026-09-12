package com.thisko.qringprint.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * \u6253\u5370\u961f\u5217\uff0c\u591a\u4efb\u52a1\u987a\u5e8f\u6267\u884c\u3002
 *
 * \u4e3a\u4ec0\u4e48\u4e0d\u76f4\u63a5\u5728 PrinterConnection \u91cc\u7d27\u5957\u4e00\u4e2a\u961f\u5217\uff1a
 *   \u6253\u5370\u4efb\u52a1\u53ef\u80fd\u6e90\u4e8e\u4efb\u610f\u7ebf\u7a0b\uff08\u6279\u91cf\u9875 / \u5206\u4eab / \u961f\u5217\uff09\uff0c
 *   \u961f\u5217\u72b6\u6001\u9700\u8981\u5168\u5c40\u53ef\u89c2\u6d4b\uff0cUI \u8981\u80fd\u663e\u793a\u300c\u8fd8\u6709 N \u4efd\u4ee5\u5f85\u6253\u5370 / \u6b63\u5728\u6253\u7b2c i \u4efd\u300d\u3002
 *
 * \u961f\u5217\u4e0e\u8fde\u63a5\u89e3\u8026\uff1a\u4e0d\u8bba\u8fde\u63a5\u662f\u84dd\u7259 / WiFi / USB\uff0c\u961f\u5217\u90fd\u662f\u540c\u4e00\u4e2a\u3002
 */
data class PrintJob(
    val id: String = "job_${System.currentTimeMillis()}_${(0..99999).random()}",
    val title: String,
    val raster: RasterData,
    val copies: Int = 1,
    /**
     * 打印加热浓度（1～4），null 表示不显式下发浓度指令（沿用机器当前值）。
     *
     * 文字 / 图片 / 条码这些页面以前都默认 1，所以这里也用 1 作为兜底。
     * 只有像「自定义打印」这种需要按用户选择动态调整浓度的场景才传非 1。
     */
    val thickness: Int? = 1,
)

sealed class QueueState {
    object Idle : QueueState()
    data class Running(val current: Int, val total: Int, val currentTitle: String) : QueueState()
}

object PrintQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ConcurrentLinkedQueue<PrintJob>()
    private val mutex = Mutex()
    private var loop: Job? = null

    private val _state = MutableStateFlow<QueueState>(QueueState.Idle)
    val state: StateFlow<QueueState> = _state.asStateFlow()

    val pendingCount: Int get() = queue.size

    fun enqueue(job: PrintJob) {
        queue.add(job)
        startLoopIfNeeded()
    }

    fun enqueueAll(jobs: List<PrintJob>) {
        queue.addAll(jobs)
        startLoopIfNeeded()
    }

    fun clear() {
        queue.clear()
    }

    private fun startLoopIfNeeded() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var completed = 0
            while (queue.isNotEmpty()) {
                val job = mutex.withLock { queue.peek() } ?: break
                val total = completed + queue.size
                _state.value = QueueState.Running(
                    current = total - queue.size + 1,
                    total = total,
                    currentTitle = job.title
                )
                var success = true
                var lastError = ""
                repeat(job.copies.coerceAtLeast(1)) { i ->
                    if (!PrinterConnection.isAlive()) {
                        success = false
                        lastError = "连接已断开"
                        return@repeat
                    }
                    val r = PrinterConnection.printRasterNow(job.raster, thickness = job.thickness)
                    if (!r.ok) {
                        success = false
                        lastError = r.message
                    }
                }
                mutex.withLock { queue.poll() }
                if (!success) {
                    queue.clear()
                    _state.value = QueueState.Idle
                    return@launch
                }
                completed++
            }
            _state.value = QueueState.Idle
        }
    }

    fun shutdown() {
        scope.cancel()
        queue.clear()
    }
}
