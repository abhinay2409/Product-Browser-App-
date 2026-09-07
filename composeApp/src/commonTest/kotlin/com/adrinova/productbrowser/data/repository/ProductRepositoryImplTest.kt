package com.adrinova.productbrowser.data.repository

import com.adrinova.productbrowser.data.remote.HttpClientFactory
import com.adrinova.productbrowser.data.remote.ProductApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Created by Abhinay on 07/09/26.
 */
class ProductRepositoryImplTest {
    private val productsJson = """
        {
          "products": [
            {
              "id": 1,
              "title": "iPhone 9",
              "description": "An apple mobile which is nothing like apple",
              "category": "smartphones",
              "price": 549.0,
              "discountPercentage": 12.96,
              "rating": 4.69,
              "stock": 94,
              "brand": "Apple",
              "thumbnail": "https://cdn.dummyjson.com/1/thumbnail.jpg",
              "images": ["https://cdn.dummyjson.com/1/1.jpg"]
            },
            {
              "id": 2,
              "title": "Handmade Soap",
              "description": "No brand field on this one",
              "category": "beauty",
              "price": 9.99,
              "discountPercentage": 0.0,
              "rating": 4.1,
              "stock": 3,
              "thumbnail": "https://cdn.dummyjson.com/2/thumbnail.jpg",
              "images": []
            }
          ],
          "total": 2,
          "skip": 0,
          "limit": 30
        }
    """.trimIndent()

    private fun repositoryWith(engine: MockEngine): ProductRepositoryImpl =
        ProductRepositoryImpl(ProductApi(HttpClientFactory.create(engine)))

    private fun successEngine() = MockEngine(
        MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler {
                respond(
                    content = productsJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    )

    @Test
    fun productsAreFetchedParsedAndMapped() = runTest {
        val repository = repositoryWith(successEngine())

        val result = repository.getProducts()
        val products = result.getOrNull()

        assertTrue (result.isSuccess)
        assertEquals(2, products?.size)
        assertEquals("iPhone 9", products?.first()?.title)
        assertEquals("Apple", products?.first()?.brand)
        assertEquals(null, products?.get(1)?.brand)
        assertEquals(9.99, products?.get(1)?.price)
    }

    @Test
    fun secondIdenticalRequestIsServedFromCache() = runTest {
        val engine = successEngine()
        val repository = repositoryWith(engine)

        repository.getProducts()
        repository.getProducts()

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun httpErrorIsConvertedIntoReadableFailure() = runTest {
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler { respondError(HttpStatusCode.InternalServerError) }
            }
        )
        val repository = repositoryWith(engine)

        val result = repository.getProducts()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("500"))
    }
}