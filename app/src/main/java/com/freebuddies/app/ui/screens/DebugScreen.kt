package com.freebuddies.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebuddies.app.DebugLog
import com.freebuddies.app.LogEntry
import com.freebuddies.app.LogTag
import com.freebuddies.app.ui.theme.*

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

@Composable
fun DebugScreen() {
    val entries by DebugLog.entries.collectAsState()
    val listState = rememberLazyListState()

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

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            DebugLogRow(entry.time, entry.tag, entry.message, tagColors[entry.tag] ?: OnDarkMuted)
        }
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
