package com.adrinova.productbrowser.domain.repository

import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.model.Product

/**
 * Created by Abhinay on 20/07/26.
 */
/**
 * Contract the data layer must fulfil. The domain layer only knows this
 * interface — the Ktor/caching implementation lives in the data layer.
 *
 * All operations return [Result] so callers handle failures explicitly
 * instead of relying on exceptions crossing layer boundaries.
 */
interface ProductRepository {

    suspend fun getProducts(limit: Int = DEFAULT_PAGE_SIZE, skip: Int = 0): Result<List<Product>>

    suspend fun searchProducts(query: String): Result<List<Product>>

    suspend fun getProduct(id: Int): Result<Product>

    suspend fun getCategories(): Result<List<Category>>

    suspend fun getProductsByCategory(categorySlug: String): Result<List<String>>

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}

