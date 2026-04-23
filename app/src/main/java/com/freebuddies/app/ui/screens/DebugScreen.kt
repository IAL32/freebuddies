package com.freebuddies.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freebuddies.app.DebugLog
import com.freebuddies.app.LogEntry
import com.freebuddies.app.LogTag
import com.freebuddies.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DebugScreen() {
    val entries by DebugLog.entries.collectAsState()
    val listState = rememberLazyListState()

    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (entries.size - 2).coerceAtLeast(0)
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
            DebugLogRow(entry)
        }
    }
}

@Composable
private fun DebugLogRow(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val time = remember(entry.timestamp) { timeFormat.format(Date(entry.timestamp)) }
    val color = when (entry.tag) {
        LogTag.APP -> AccentBlue
        LogTag.BUDS -> AccentTeal
        LogTag.TX -> Warning
        LogTag.RX -> AccentPurple
    }
    val style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = time,
            style = style,
            color = OnDarkFaint,
            modifier = Modifier.width(58.dp)
        )
        Text(
            text = "(${entry.tag.name.lowercase()})",
            style = style,
            color = color,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = entry.message,
            style = style,
            color = OnDark.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}
