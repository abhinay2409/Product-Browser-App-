package com.adrinova.productbrowser.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.usecase.GetCategoriesUseCase
import com.adrinova.productbrowser.domain.usecase.GetProductsByCategoryUseCase
import com.adrinova.productbrowser.domain.usecase.GetProductsUseCase
import com.adrinova.productbrowser.domain.usecase.SearchProductsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Created by Abhinay on 23/07/26.
 */
class ProductListViewModel(
    private val getProducts: GetProductsUseCase,
    private val searchProducts: SearchProductsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getProductsByCategory: GetProductsByCategoryUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProductListUiState())
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadProducts()
        loadCategories()
    }

    /** Called on every keystroke; the actual API call is debounced. */
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            executeSearch()
        }
    }

    fun onCategorySelected(category: Category?) {
        searchJob?.cancel()
        _uiState.update { it.copy(selectedCategory = category, searchQuery = "") }
        loadProducts()
    }

    fun retry() {
        val query = _uiState.value.searchQuery
        query?.let { if (it.isBlank()) loadProducts() else viewModelScope.launch { executeSearch() } }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val selected = _uiState.value.selectedCategory
            val result = if (selected != null) {
                getProductsByCategory(selected.slug)
            } else {
                getProducts()
            }
            result
                .onSuccess { products ->
                    _uiState.update { it.copy(isLoading = false, products = products) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }


    private fun loadCategories() {
        viewModelScope.launch {
            getCategories().onSuccess { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
            // Category chips are an enhancement; a failure here should not
            // block the main list, so it is silently ignored.
        }
    }

    private suspend fun executeSearch() {
        val query = _uiState.value.searchQuery
        // Searching resets the category filter (documented assumption).
        if (query != null) {
            if (query.isNotBlank() && _uiState.value.selectedCategory != null) {
                _uiState.update { it.copy(selectedCategory = null) }
            }
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        query?.let { searchProducts(it) }
            ?.onSuccess { products ->
                _uiState.update { it.copy(isLoading = false, products = products) }
            }
            ?.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}