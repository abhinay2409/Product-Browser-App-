package com.adrinova.productbrowser.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.model.Product
import com.adrinova.productbrowser.presentation.components.EmptyView
import com.adrinova.productbrowser.presentation.components.ErrorView
import com.adrinova.productbrowser.presentation.components.LoadingView
import com.adrinova.productbrowser.presentation.components.RatingBar
import com.adrinova.productbrowser.presentation.util.toPriceString
import io.ktor.websocket.Frame
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Created by Abhinay on 28/07/26.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductListViewModel,
    onProductClick: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Product Browser") }) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            state.searchQuery?.let {
                SearchField(
                    query = it,
                    onQueryChange = viewModel::onSearchQueryChange
                )
            }

            if (state.categories.isNotEmpty()) {
                CategoryRow(state = state, onCategorySelected = viewModel::onCategorySelected)
            }

            when {
                state.isLoading -> LoadingView()
                state.error != null -> ErrorView(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::retry
                )

                state.isEmpty -> EmptyView("No products found. Try a different search.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.products, key = { it.id }) { product ->
                        ProductCard(product = product, onClick = { onProductClick(product.id) })
                    }
                }
            }
        }

    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("Search products…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CategoryRow(
    state: ProductListUiState,
    onCategorySelected: (Category?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = state.selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") }
            )
        }
        items(state.categories, key = { it.slug }) { category ->
            FilterChip(
                selected = state.selectedCategory?.slug == category.slug,
                onClick = { onCategorySelected(category) },
                label = { Frame.Text(category.name) }
            )
        }

    }

}

@Composable
private fun ProductCard(
    product: Product,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AsyncImage(
            model = product.thumbnail,
            contentDescription = product.title,
            modifier = Modifier.size(72.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(4.dp))
        RatingBar(rating = product.rating, starSize = 14.dp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = product.price.toPriceString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (product.hasDiscount) {
                Text(
                    text = "-${product.discountPercentage}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

}

@Preview
@Composable
private fun ProductCardPreview() {
    MaterialTheme {
        ProductCard(
            product = Product(
                id = 1,
                title = "Essence Mascara Lash Princess",
                description = "A popular mascara.",
                category = "beauty",
                price = 9.99,
                discountPercentage = 7.17,
                rating = 4.94,
                stock = 5,
                brand = "Essence",
                thumbnail = "",
                images = emptyList()
            ),
            onClick = {}
        )
    }
}