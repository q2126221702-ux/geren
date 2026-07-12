package com.xuzheng.tiyuengine.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuzheng.tiyuengine.data.AppUpdater
import com.xuzheng.tiyuengine.data.NetworkMonitor
import com.xuzheng.tiyuengine.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun QuizAppShell(
    screen: Screen,
    isOnline: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenUpdate: () -> Unit,
    content: @Composable (Screen) -> Unit,
) {
    var startupUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var startupChecked by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val appUpdater = remember(context) { AppUpdater(context) }

    LaunchedEffect(Unit) {
        if (!startupChecked && NetworkMonitor.isOnline(context)) {
            startupChecked = true
            runCatching { withContext(Dispatchers.IO) { appUpdater.checkForUpdate() } }
                .onSuccess { update -> if (update != null) startupUpdate = update }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = appColors().pageBackground,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!isOnline) {
                Surface(color = appColors().offlineBanner, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "当前无网络，题库同步与 AI 功能可能不可用",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = appColors().offlineBannerText,
                        fontSize = 13.sp,
                    )
                }
            }
            AnimatedContent(
                targetState = screen,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (isOnline) 0.dp else 40.dp),
                transitionSpec = {
                    (slideInHorizontally { it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                },
                label = "screen",
            ) { targetScreen ->
                content(targetScreen)
            }
        }
    }

    startupUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { startupUpdate = null },
            title = { Text("发现新版本 ${update.versionName}", fontWeight = FontWeight.Bold) },
            text = { Text(update.notes, lineHeight = 22.sp) },
            confirmButton = {
                Button(onClick = { startupUpdate = null; onOpenUpdate() }) { Text("前往更新") }
            },
            dismissButton = {
                TextButton(onClick = { startupUpdate = null }) { Text("稍后") }
            },
        )
    }
}