package com.freebuddies.app.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.freebuddies.app.FreeBudsConnectionManager
import com.freebuddies.app.R
import com.freebuddies.app.protocol.RingingStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class FindBudsTileService : TileService() {
    private var listeningJob: Job? = null
    private var autoStopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        listeningJob = scope.launch {
            combine(
                FreeBudsConnectionManager.isConnected,
                FreeBudsConnectionManager.ringingStatus
            ) { connected, rs -> connected to rs }.collect { (connected, rs) ->
                updateTileState(connected, rs)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onClick() {
        super.onClick()
        if (!FreeBudsConnectionManager.isConnected.value) {
            Toast.makeText(this, "FreeBuds not connected", Toast.LENGTH_SHORT).show()
            return
        }

        val rs = FreeBudsConnectionManager.ringingStatus.value
        val anyRinging = rs.left || rs.right

        if (anyRinging) {
            FreeBudsConnectionManager.setRinging(0, false)
            FreeBudsConnectionManager.setRinging(1, false)
            autoStopJob?.cancel()
            autoStopJob = null
            // Optimistic update — buds may not send a stop confirmation
            updateTileState(true, RingingStatus(false, false))
        } else {
            FreeBudsConnectionManager.setRinging(0, true)
            FreeBudsConnectionManager.setRinging(1, true)
            // Optimistic update
            updateTileState(true, RingingStatus(true, true))
            autoStopJob?.cancel()
            autoStopJob = scope.launch {
                delay(45_000)
                FreeBudsConnectionManager.setRinging(0, false)
                FreeBudsConnectionManager.setRinging(1, false)
                updateTileState(true, RingingStatus(false, false))
            }
        }
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTileState(connected: Boolean, rs: RingingStatus) {
        val tile = qsTile ?: return
        if (!connected) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Find my buds"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Not connected"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_find_idle)
        } else {
            val ringing = rs.left || rs.right
            tile.state = if (ringing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Find my buds"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (ringing) "Ringing..." else "Tap to ring"
            }
            tile.icon = Icon.createWithResource(this,
                if (ringing) R.drawable.ic_tile_find_active else R.drawable.ic_tile_find_idle)
        }
        tile.updateTile()
    }
}
