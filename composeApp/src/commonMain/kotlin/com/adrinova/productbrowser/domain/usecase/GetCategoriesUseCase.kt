package com.adrinova.productbrowser.domain.usecase

import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.repository.ProductRepository

/**
 * Created by Abhinay on 20/07/26.
 */
/** Business use case: list all product categories (used for filtering). */
class GetCategoriesUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(): Result<List<Category>> = repository.getCategories()
}