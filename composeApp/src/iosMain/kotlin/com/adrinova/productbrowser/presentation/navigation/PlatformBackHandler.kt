package com.adrinova.productbrowser.presentation.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back gesture to intercept on iOS; the top bar's
    // back button drives navigation instead.
}
