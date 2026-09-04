package com.adrinova.productbrowser.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Created by Abhinay on 05/09/26.
 */
class SearchProductsUseCaseTest {

    private val repository = FakeProductRepository()
    private val useCase = SearchProductsUseCase(repository)

    @Test
    fun nonBlankQueryIsTrimmedAndDelegatedToSearch() = runTest {
        val expected = listOf(FakeProductRepository.product(1, "iPhone 9"))
        repository.searchResult = Result.success(expected)

        val result = useCase("  phone  ")
        assertEquals(listOf("phone"), repository.receivedSearchQueries)
        assertEquals(0, repository.getProductsCallCount)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun blankQueryFallsBackToFullCatalog() = runTest {
        val expected = listOf(
            FakeProductRepository.product(1),
            FakeProductRepository.product(2)
        )
        repository.productsResult = Result.success(expected)

        val result = useCase("   ")

        assertEquals(1, repository.getProductsCallCount)
        assertTrue(repository.receivedSearchQueries.isEmpty())
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun repositoryFailureIsPropagated() = runTest {
        val error = IllegalStateException("network down")
        repository.searchResult = Result.failure(error)

        val result = useCase("phone")

        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
    }

}