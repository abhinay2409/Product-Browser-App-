package com.adrinova.productbrowser.data.mapper

import com.adrinova.productbrowser.data.remote.dto.CategoryDto
import com.adrinova.productbrowser.data.remote.dto.ProductDto
import com.adrinova.productbrowser.domain.model.Category
import com.adrinova.productbrowser.domain.model.Product

/**
 * Created by Abhinay on 21/07/26.
 */
fun ProductDto.toDomain(): Product = Product(
    id = id,
    title = title,
    description = description,
    category = category,
    price = price,
    discountPercentage = discountPercentage,
    rating = rating,
    stock = stock,
    brand = brand,
    thumbnail = thumbnail,
    images = images
)

fun CategoryDto.toDomain(): Category = Category(
    slug = slug,
    name = name
)