package com.adrinova.productbrowser.presentation.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op: iOS has no system back gesture handled here; navigation
    // relies on the top-bar back button.
}
