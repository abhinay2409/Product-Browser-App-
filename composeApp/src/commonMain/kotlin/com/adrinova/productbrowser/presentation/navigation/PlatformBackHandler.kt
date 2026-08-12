package com.adrinova.productbrowser.presentation.navigation

import androidx.compose.runtime.Composable

/**
 * Created by Abhinay on 12/08/26.
 */

/**
 * Intercepts the system back gesture where one exists (Android).
 * The iOS actual is a no-op — navigation there relies on the
 * top-bar back button.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)