package com.adrinova.productbrowser.domain.usecase

import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.domain.repository.ProductRepository

/**
 * Created by Abhinay on 16/08/26.
 */

/** Hand-rolled test double: records calls and returns configurable results. */
class FakeProductRepository : ProductRepository {

    var productsResult: Result<List<Product>> = Result.success(emptyList())
    var searchResult: Result<List<Product>> = Result.success(emptyList())

    var getProductsCallCount = 0
        private set
    val receivedSearchQueries = mutableListOf<String>()

    override suspend fun getProducts(limit: Int, skip: Int): Result<List<Product>> {
        getProductsCallCount++
        return productsResult
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> {
        receivedSearchQueries += query
        return searchResult
    }

    override suspend fun getProduct(id: Int): Result<Product> =
        productsResult.map { it.first { p -> p.id == id } }

    override suspend fun getCategories(): Result<List<Category>> = Result.success(emptyList())

    override suspend fun getProductsByCategory(categorySlug: String): Result<List<Product>> =
        productsResult

    companion object {
        fun product(id: Int, title: String = "Product $id") = Product(
            id = id,
            title = title,
            description = "description",
            category = "smartphones",
            price = 100.0,
            discountPercentage = 10.0,
            rating = 4.5,
            stock = 10,
            brand = "Brand",
            thumbnail = "thumb.jpg",
            images = listOf("image.jpg")
        )
    }

}