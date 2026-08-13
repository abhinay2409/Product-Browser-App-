package com.adrinova.productbrowser

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.adrinova.productbrowser.di.AppContainer
import com.adrinova.productbrowser.presentation.detail.ProductDetailScreen
import com.adrinova.productbrowser.presentation.detail.ProductDetailViewModel
import com.adrinova.productbrowser.presentation.list.ProductListScreen
import com.adrinova.productbrowser.presentation.list.ProductListViewModel
import com.adrinova.productbrowser.presentation.navigation.PlatformBackHandler


/** The two destinations of this small app. */
private sealed interface Screen {
    data object List : Screen
    data class Detail(val productId: Int) : Screen
}

@Composable
fun App(container: AppContainer = remember { AppContainer() }) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    MaterialTheme {
        var screen: Screen by remember { mutableStateOf(Screen.List) }
        // Kept at the root so list state (scroll, query, filter) survives
        // navigating to a detail screen and back.
        val listViewModel: ProductListViewModel = viewModel {
            ProductListViewModel(
                getProducts = container.getProducts,
                searchProducts = container.searchProducts,
                getCategories = container.getCategories,
                getProductsByCategory = container.getProductsByCategory
            )
        }

        when (val current = screen) {
            is Screen.List -> ProductListScreen(
                viewModel = listViewModel,
                onProductClick = { id -> screen = Screen.Detail(id) }
            )

            is Screen.Detail -> {
                PlatformBackHandler(enabled = true) { screen = Screen.List }
                val detailViewModel: ProductDetailViewModel = viewModel(
                    key = "product_detail_${current.productId}"
                ) {
                    ProductDetailViewModel(
                        getProductDetail = container.getProductDetail,
                        productId = current.productId
                    )
                }
                ProductDetailScreen(
                    viewModel = detailViewModel,
                    onBack = { screen = Screen.List }
                )
            }
        }
    }
}