package com.adrinova.productbrowser.domain.usecase

import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.domain.repository.ProductRepository

/**
 * Created by Abhinay on 20/07/26.
 */
/**
 * Business use case: keyword search against the remote catalog.
 *
 * Business rules applied here (and unit-tested):
 *  - the query is trimmed before being sent to the API;
 *  - a blank query is treated as "show the full catalog" rather than
 *    issuing a pointless search request.
 */
class SearchProductsUseCase(private val repository: ProductRepository) {

    suspend operator fun invoke(query: String): Result<List<Product>> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            repository.getProducts()
        } else {
            repository.searchProducts(normalized)
        }
    }
}