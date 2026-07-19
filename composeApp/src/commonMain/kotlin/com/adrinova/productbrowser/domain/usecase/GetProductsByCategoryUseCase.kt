package com.adrinova.productbrowser.domain.usecase

import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.domain.repository.ProductRepository

/**
 * Created by Abhinay on 20/07/26.
 */
/** Business use case: fetch products belonging to a single category. */
private class GetProductsByCategoryUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(categorySlug: String): Result<List<Product>> =
        repository.getProductsByCategory(categorySlug)
}