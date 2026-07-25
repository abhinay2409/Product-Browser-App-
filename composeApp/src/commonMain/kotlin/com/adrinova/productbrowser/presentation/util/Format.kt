package com.adrinova.productbrowser.presentation.util

import kotlin.math.roundToInt

/**
 * Created by Abhinay on 25/07/26.
 */
/** Multiplatform-safe "$12.99" formatting (no java.text on iOS). */
fun Double.toPriceString(): String {
    val cents = (this * 100).roundToInt()
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "$$whole.$fraction"
}

fun Double.toRatingString(): String {
    val tenths = (this * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}