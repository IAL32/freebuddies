package com.freebuddies.app.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.freebuddies.app.FreeBudsConnectionManager
import com.freebuddies.app.R
import com.freebuddies.app.protocol.AncMode
import com.freebuddies.app.protocol.SoundControl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class AncTileService : TileService() {
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        listeningJob = scope.launch {
            combine(
                FreeBudsConnectionManager.isConnected,
                FreeBudsConnectionManager.soundControl
            ) { connected, sc -> connected to sc }.collect { (connected, sc) ->
                updateTileState(connected, sc)
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

        val current = FreeBudsConnectionManager.soundControl.value?.mode ?: AncMode.OFF
        val next = cycleAnc(current)

        // Optimistic update
        val intensity = FreeBudsConnectionManager.preferredNcIntensity.value
        updateTileState(true, SoundControl(next, intensity))
        FreeBudsConnectionManager.setAncMode(next, intensity)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun cycleAnc(current: AncMode): AncMode = when (current) {
        AncMode.OFF -> AncMode.NOISE_CANCELLING
        AncMode.NOISE_CANCELLING -> AncMode.AWARENESS
        AncMode.AWARENESS -> AncMode.OFF
        AncMode.UNKNOWN -> AncMode.OFF
    }

    private fun updateTileState(connected: Boolean, sc: SoundControl?) {
        val tile = qsTile ?: return
        if (!connected) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Noise control"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Not connected"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_anc_off)
        } else {
            val mode = sc?.mode ?: AncMode.OFF
            tile.state = if (mode == AncMode.OFF) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.label = "Noise control"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = when (mode) {
                    AncMode.OFF -> "Off"
                    AncMode.NOISE_CANCELLING -> "Noise cancelling"
                    AncMode.AWARENESS -> "Awareness"
                    AncMode.UNKNOWN -> "Unknown"
                }
            }
            tile.icon = Icon.createWithResource(this, when (mode) {
                AncMode.NOISE_CANCELLING -> R.drawable.ic_tile_anc_nc
                AncMode.AWARENESS -> R.drawable.ic_tile_anc_aware
                else -> R.drawable.ic_tile_anc_off
            })
        }
        tile.updateTile()
    }
}
