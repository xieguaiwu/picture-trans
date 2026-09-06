package com.xieguiawu.picturetrans

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xieguiawu.picturetrans.media.MediaAccess
import com.xieguiawu.picturetrans.media.MediaPermissions
import com.xieguiawu.picturetrans.server.ServerRunner
import com.xieguiawu.picturetrans.server.ServerState
import com.xieguiawu.picturetrans.transfer.HistoryEntry
import com.xieguiawu.picturetrans.transfer.TransferProgress
import com.xieguiawu.picturetrans.util.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val serverState: StateFlow<ServerState> = ServerRunner.state
    val activeTransfers: StateFlow<List<TransferProgress>> = ServerRunner.tracker.active
    val history: StateFlow<List<HistoryEntry>> = ServerRunner.tracker.history

    private val _mediaAccess = MutableStateFlow(MediaPermissions.access(app))
    val mediaAccess: StateFlow<MediaAccess> = _mediaAccess.asStateFlow()

    fun defaultPort(): Int = TokenStore.port(getApplication())

    fun startServer(port: Int) {
        ServerRunner.start(getApplication(), port)
    }

    fun stopServer() = ServerRunner.stop()

    fun refreshPermission() {
        _mediaAccess.value = MediaPermissions.access(getApplication())
    }
}
