package com.freebuddies.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.freebuddies.app.FreeBudsViewModel
import com.freebuddies.app.ui.theme.AccentBlue
import com.freebuddies.app.ui.theme.OnDark
import com.freebuddies.app.ui.theme.OnDarkMuted
import com.freebuddies.app.ui.theme.SurfaceVariant

@Composable
fun AboutScreen(@Suppress("UNUSED_PARAMETER") vm: FreeBudsViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("freebuddies", style = MaterialTheme.typography.displayLarge, color = OnDark)
        Text("v1.0", style = MaterialTheme.typography.bodyMedium, color = OnDarkMuted)
        Spacer(Modifier.height(24.dp))
        Text(
            "an open-source companion app for huawei freebuds pro 4.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = OnDark
        )
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/IAL32/freebuddies".toUri())
                context.startActivity(intent)
            },
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)
        ) {
            Text("github repository", color = OnDark)
        }
        
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/IAL32".toUri())
            context.startActivity(intent)
        }) {
            Text("author: IAL32", style = MaterialTheme.typography.bodyMedium, color = AccentBlue)
        }
    }
}
