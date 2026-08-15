package com.adrinova.productbrowser

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName") // called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController { App() }