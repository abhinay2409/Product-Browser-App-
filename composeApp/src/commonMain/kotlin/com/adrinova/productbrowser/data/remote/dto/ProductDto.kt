package com.adrinova.productbrowser.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire format of https://dummyjson.com/products. Kept separate from the
 * domain model so API changes never leak past the data layer.
 */
@Serializable
data class ProductDto(
    val id: Int,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val rating: Double = 0.0,
    val stock: Int = 0,
    val brand: String? = null, // absent for some products in the API
    val thumbnail: String = "",
    val images: List<String> = emptyList()
)

@Serializable
data class ProductsResponseDto(
    val products: List<ProductDto> = emptyList(),
    val total: Int = 0,
    val skip: Int = 0,
    val limit: Int = 0
)

@Serializable
data class CategoryDto(
    val slug: String,
    val name: String,
    val url: String? = null
)
