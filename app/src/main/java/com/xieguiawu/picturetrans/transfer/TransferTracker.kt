package com.xieguiawu.picturetrans.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** 从手机视角看传输方向：UPLOAD = PC 推到手机（接收），DOWNLOAD = PC 从手机拉（发送）。 */
enum class Direction { UPLOAD, DOWNLOAD }

data class TransferProgress(
    val id: Long,
    val direction: Direction,
    val fileName: String,
    val bytesDone: Long = 0,
    val bytesTotal: Long = -1,
    val speedBps: Long = 0,
    val startedAtMs: Long = System.currentTimeMillis(),
)

data class HistoryEntry(
    val id: Long,
    val direction: Direction,
    val fileName: String,
    val bytes: Long,
    val ok: Boolean,
    val finishedAtMs: Long = System.currentTimeMillis(),
    val message: String? = null,
)

/**
 * 传输进度追踪。线程安全：服务器 IO 线程直接调用；
 * 发射节流（250ms）防止大文件逐 64KB 刷爆 StateFlow。
 */
class TransferTracker {

    private val _active = MutableStateFlow<List<TransferProgress>>(emptyList())
    val active: StateFlow<List<TransferProgress>> = _active.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val counter = AtomicLong()
    private val throttleMs = 250L

    fun begin(direction: Direction, fileName: String, bytesTotal: Long): Handle {
        val h = Handle(counter.incrementAndGet(), direction, fileName, bytesTotal)
        _active.update { it + h.snapshot() }
        return h
    }

    inner class Handle internal constructor(
        val id: Long,
        val direction: Direction,
        val fileName: String,
        val bytesTotal: Long,
    ) {
        private var bytesDone = 0L
        private var lastEmitMs = 0L
        private val startedAtMs = System.currentTimeMillis()

        fun addBytes(n: Int) {
            if (n <= 0) return
            synchronized(this) {
                bytesDone += n
                maybeEmit(force = false)
            }
        }

        private var finished = false

        /** 幂等：writeTo 异常路径与调用方都可能触发，只记一次。 */
        fun finish(ok: Boolean, message: String? = null) {
            synchronized(this) {
                if (finished) return
                finished = true
                val done = bytesDone
                _active.update { list -> list.filterNot { it.id == id } }
                _history.update { h ->
                    (listOf(HistoryEntry(id, direction, fileName, done, ok, message = message)) + h).take(50)
                }
            }
        }

        private fun maybeEmit(force: Boolean) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmitMs < throttleMs) return
            lastEmitMs = now
            val elapsed = now - startedAtMs
            val speed = if (elapsed > 0) bytesDone * 1000 / elapsed else 0L
            _active.update { list ->
                list.map { if (it.id == id) it.copy(bytesDone = bytesDone, speedBps = speed) else it }
            }
        }

        fun snapshot(): TransferProgress = TransferProgress(
            id = id, direction = direction, fileName = fileName,
            bytesDone = bytesDone, bytesTotal = bytesTotal,
            startedAtMs = startedAtMs,
        )
    }

}
