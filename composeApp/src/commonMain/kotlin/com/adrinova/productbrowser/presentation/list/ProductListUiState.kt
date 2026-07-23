package com.adrinova.productbrowser.presentation.list

import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.model.Product

/**
 * Created by Abhinay on 23/07/26.
 */
data class ProductListUiState(
    val isLoading: Boolean = false,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val searchQuery: String? = "",
    val error: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && products.isEmpty()
}