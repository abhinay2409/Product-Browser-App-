package com.adrinova.productbrowser.di

import com.adrinova.productbrowser.data.remote.HttpClientFactory
import com.adrinova.productbrowser.data.remote.ProductApi
import com.adrinova.productbrowser.data.repository.ProductRepositoryImpl
import com.adrinova.productbrowser.domain.repository.ProductRepository
import com.adrinova.productbrowser.domain.usecase.GetCategoriesUseCase
import com.adrinova.productbrowser.domain.usecase.GetProductDetailUseCase
import com.adrinova.productbrowser.domain.usecase.GetProductsByCategoryUseCase
import com.adrinova.productbrowser.domain.usecase.GetProductsUseCase
import com.adrinova.productbrowser.domain.usecase.SearchProductsUseCase

/**
 * Created by Abhinay on 22/07/26.
 */
/**
 * Manual dependency injection (the task allows manual DI).
 * One instance is created at the app root and passed down; everything is
 * wired against interfaces so swapping implementations (e.g. for tests)
 * is trivial.
 */

class AppContainer {
    private val httpClient = HttpClientFactory.create()
    private val api = ProductApi(httpClient)
    private val repository: ProductRepository = ProductRepositoryImpl(api)

    val getProducts = GetProductsUseCase(repository)
    val searchProducts = SearchProductsUseCase(repository)
    val getProductDetail = GetProductDetailUseCase(repository)
    val getCategories = GetCategoriesUseCase(repository)
    val getProductsByCategory = GetProductsByCategoryUseCase(repository)
}