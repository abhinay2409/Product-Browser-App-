package com.adrinova.productbrowser.data.remote

import com.adrinova.productbrowser.data.remote.dto.CategoryDto
import com.adrinova.productbrowser.data.remote.dto.ProductDto
import com.adrinova.productbrowser.data.remote.dto.ProductsResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Created by Abhinay on 20/07/26.
 */
/**
 * Thin, typed wrapper around the DummyJSON REST endpoints.
 * Knows nothing about caching or domain models.
 */
class ProductApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL
) {
    suspend fun getProducts(limit: Int, skip: Int): ProductsResponseDto =
        client.get("$baseUrl/products") {
            parameter("limit", limit)
            parameter("skip", skip)
        }.body()

    suspend fun searchProducts(query: String): ProductsResponseDto =
        client.get("$baseUrl/products/search") {
            parameter("q", query)
        }.body()

    suspend fun getProduct(id: Int): ProductDto =
        client.get("$baseUrl/products/$id").body()

    suspend fun getCategories(): List<CategoryDto> =
        client.get("$baseUrl/products/categories").body()

    suspend fun getProductsByCategory(slug: String): ProductsResponseDto =
        client.get("$baseUrl/products/category/$slug").body()

    companion object {
        const val BASE_URL = "https://dummyjson.com"
    }

}