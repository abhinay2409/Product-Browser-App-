package com.adrinova.productbrowser

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform