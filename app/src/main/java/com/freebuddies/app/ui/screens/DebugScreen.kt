package com.freebuddies.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebuddies.app.DebugLog
import com.freebuddies.app.LogEntry
import com.freebuddies.app.LogTag
import com.freebuddies.app.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val logStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

private val tagColors = mapOf(
    LogTag.APP to AccentBlue,
    LogTag.BUDS to AccentTeal,
    LogTag.TX to Warning,
    LogTag.RX to AccentPurple,
)

private val dimWhite = OnDark.copy(alpha = 0.85f)

private fun entriesToText(entries: List<LogEntry>): String =
    entries.joinToString("\n") { "${it.time} (${it.tag.name.lowercase()}) ${it.message}" }

@Composable
fun DebugScreen() {
    val entries by DebugLog.entries.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= (entries.size - 2).coerceAtLeast(0)
        }
    }

    LaunchedEffect(entries.size) {
        if (isAtBottom && entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { copyToClipboard(context, entries) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "copy to clipboard", tint = OnDarkMuted)
            }
            IconButton(onClick = { saveToFile(context, entries) }) {
                Icon(Icons.Default.FileDownload, contentDescription = "save to file", tint = OnDarkMuted)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                DebugLogRow(entry.time, entry.tag, entry.message, tagColors[entry.tag] ?: OnDarkMuted)
            }
        }
    }
}

private fun copyToClipboard(context: Context, entries: List<LogEntry>) {
    val text = entriesToText(entries)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("FreeBuds debug log", text))
    Toast.makeText(context, "Copied ${entries.size} entries", Toast.LENGTH_SHORT).show()
}

private fun saveToFile(context: Context, entries: List<LogEntry>) {
    val text = entriesToText(entries)
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val filename = "freebuddies_$timestamp.log"
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    try {
        val file = File(dir, filename)
        file.writeText(text)
        Toast.makeText(context, "Saved to Downloads/$filename", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DebugLogRow(time: String, tag: LogTag, message: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = time, style = logStyle, color = OnDarkFaint, modifier = Modifier.width(58.dp))
        Text(text = "(${tag.name.lowercase()})", style = logStyle, color = color, modifier = Modifier.width(48.dp))
        Text(text = message, style = logStyle, color = dimWhite, modifier = Modifier.weight(1f))
    }
}
