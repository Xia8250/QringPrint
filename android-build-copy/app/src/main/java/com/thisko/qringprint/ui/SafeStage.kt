package com.thisko.qringprint.ui

// Minimal stub. Compose does not allow try-catch around Composable invocations,
// so SafePage can only provide a small layout helper. The real crash protection
// comes from individual pages handling their own state and from the global
// Thread.setDefaultUncaughtExceptionHandler in QringPrintApp.installCrashHandler.
// This file is kept so existing imports of SafePage still resolve.
// SafePage is now a transparent passthrough that just renders content.

import androidx.compose.runtime.Composable

@Composable
fun SafePage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Transparent passthrough. Each page is responsible for its own error handling.
    content()
}
