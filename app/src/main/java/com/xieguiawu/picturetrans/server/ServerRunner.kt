package com.xieguiawu.picturetrans.server

import com.xieguiawu.picturetrans.media.MediaStoreRepository
import com.xieguiawu.picturetrans.net.NetworkUtils
import com.xieguiawu.picturetrans.transfer.TransferTracker
import com.xieguiawu.picturetrans.util.TokenStore
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.BindException

sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState
    data class Running(val port: Int, val token: String, val urls: List<String>) : ServerState
    data class Error(val message: String) : ServerState
}

/**
 * 进程级单例：旋转屏幕/重建 Activity 不中断传输。
 * ViewModel 只负责触发 start/stop。
 */
object ServerRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    val tracker = TransferTracker()

    @Volatile
    private var server: TransferServer? = null

    private var generation = 0

    fun start(context: Context, port: Int) {
        val current = _state.value
        if (current is ServerState.Running || current is ServerState.Starting) return
        val gen = ++generation
        _state.value = ServerState.Starting
        val app = context.applicationContext
        scope.launch {
            try {
                val token = TokenStore.getOrCreate(app)
                val srv = TransferServer(
                    port = TokenStore.clampPort(port),
                    token = token,
                    repository = MediaStoreRepository(app),
                    tracker = tracker,
                )
                val actual = srv.start()
                // stop() 已调用则丢弃本次启动结果（防竞态复活）
                if (gen != generation) {
                    srv.stop()
                    return@launch
                }
                server = srv
                val urls = NetworkUtils.siteLocalIps().map { NetworkUtils.url(it.ip, actual, token) }
                _state.value = ServerState.Running(actual, token, urls)
            } catch (e: Exception) {
                if (gen == generation) {
                    _state.value = ServerState.Error(readable(e, port))
                }
            }
        }
    }

    fun stop() {
        generation++
        server?.stop()
        server = null
        _state.value = ServerState.Stopped
    }

    private fun readable(e: Exception, port: Int): String = when {
        e is BindException || e.message?.contains("already in use", ignoreCase = true) == true ->
            "端口 $port 已被占用，请换一个端口"
        else -> "启动失败：${e.message ?: e.javaClass.simpleName}"
    }
}
