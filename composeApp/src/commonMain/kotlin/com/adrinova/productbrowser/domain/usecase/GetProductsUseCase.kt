package com.adrinova.productbrowser.domain.usecase

import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.domain.repository.ProductRepository

/**
 * Created by Abhinay on 20/07/26.
 */
/** Business use case: fetch the product catalog. */
class GetProductsUseCase(private val repository: ProductRepository) {

    suspend operator fun invoke(
        limit: Int = ProductRepository.DEFAULT_PAGE_SIZE,
        skip: Int = 0
    ): Result<List<Product>> = repository.getProducts(limit = limit, skip = skip)
}