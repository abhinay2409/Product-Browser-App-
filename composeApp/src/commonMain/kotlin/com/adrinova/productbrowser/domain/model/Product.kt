package com.adrinova.productbrowser.domain.model

/**
 * Domain representation of a product. Deliberately free of any
 * serialization / network concerns so the domain layer has zero
 * framework dependencies.
 */

data class Product(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    val discountPercentage: Double,
    val rating: Double,
    val stock: Int,
    val brand: String?,
    val thumbnail: String,
    val images: List<String>
) {
    val hasDiscount: Boolean get() = discountPercentage > 0.0

    /** Price before the discount was applied, derived for display purposes. */
    val originalPrice: Double
        get() = if (hasDiscount) price / (1 - discountPercentage / 100) else price
}
