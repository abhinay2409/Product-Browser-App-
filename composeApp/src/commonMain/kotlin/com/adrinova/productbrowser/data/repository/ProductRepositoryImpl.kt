package com.adrinova.productbrowser.data.repository

import com.adrinova.productbrowser.data.cache.InMemoryCache
import com.adrinova.productbrowser.data.mapper.toDomain
import com.adrinova.productbrowser.data.remote.ProductApi
import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.domain.repository.ProductRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes

/**
 * Created by Abhinay on 21/07/26.
 */
/**
 * Data-layer implementation of [ProductRepository]:
 *  - talks to the network through [ProductApi];
 *  - maps DTOs to domain models;
 *  - adds a small TTL cache in front of list/detail/category calls
 *    (search results are always fetched live);
 *  - converts transport exceptions into readable [Result] failures.
 */
class ProductRepositoryImpl(
    private val api: ProductApi,
    private val listCache: InMemoryCache<String, List<Product>> = InMemoryCache(ttl = 5.minutes),
    private val productCache: InMemoryCache<Int, Product> = InMemoryCache(ttl = 5.minutes),
    private val categoryCache: InMemoryCache<Unit, List<Category>> = InMemoryCache(ttl = 30.minutes)
) : ProductRepository {
    override suspend fun getProducts(
        limit: Int,
        skip: Int
    ): Result<List<Product>> = safeCall {
        val cacheKey = "all:$limit:$skip"
        listCache.get(cacheKey) ?: api.getProducts(limit = limit, skip = skip)
            .products.map { it.toDomain() }
            .also { listCache.put(cacheKey, it) }
    }

    override suspend fun searchProducts(query: String): Result<List<Product>> = safeCall {
        api.searchProducts(query).products.map { it.toDomain() }
    }

    override suspend fun getProduct(id: Int): Result<Product> = safeCall {
        productCache.get(id) ?: api.getProduct(id)
            .toDomain()
            .also { productCache.put(id, it) }
    }

    override suspend fun getCategories(): Result<List<Category>> = safeCall {
        categoryCache.get(Unit) ?: api.getCategories()
            .map { it.toDomain() }
            .also { categoryCache.put(Unit, it) }
    }

    override suspend fun getProductsByCategory(categorySlug: String): Result<List<Product>> =
        safeCall {
            val cacheKey = "category:$categorySlug"
            listCache.get(cacheKey) ?: api.getProductsByCategory(categorySlug)
                .products.map { it.toDomain() }
                .also { listCache.put(cacheKey, it) }
        }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            Result.failure(DataException(e.toReadableMessage(), e))
        }

    private fun Exception.toReadableMessage(): String = when (this) {
        is HttpRequestTimeoutException -> "The request timed out. Please try again."
        is ResponseException -> "Server error (${response.status.value}). Please try again."
        is IOException -> "Couldn't reach the server. Check your internet connection."
        else -> message ?: "Something went wrong. Please try again."
    }
}

/** Data-layer exception carrying a user-presentable message. */
class DataException(message: String, cause: Throwable? = null) : Exception(message, cause)