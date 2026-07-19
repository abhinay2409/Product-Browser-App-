package com.adrinova.productbrowser.domain.usecase

import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.domain.repository.ProductRepository

/**
 * Created by Abhinay on 20/07/26.
 */
/** Business use case: fetch full detail for a single product. */
class GetProductDetailUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(id: Int): Result<Product> = repository.getProduct(id)
}