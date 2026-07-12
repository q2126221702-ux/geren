package com.xuzheng.tiyuengine.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class AppMessenger(private val snackbarHostState: SnackbarHostState, private val scope: kotlinx.coroutines.CoroutineScope) {
    fun show(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
}

val LocalAppMessenger = compositionLocalOf<AppMessenger> {
    error("AppMessenger not provided")
}

@Composable
fun rememberAppMessenger(snackbarHostState: SnackbarHostState): AppMessenger {
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) { AppMessenger(snackbarHostState, scope) }
}
