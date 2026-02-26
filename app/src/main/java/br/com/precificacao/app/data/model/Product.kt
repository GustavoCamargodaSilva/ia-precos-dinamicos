package br.com.precificacao.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val productId: String,
    val name: String,
    val description: String,
    val basePriceCents: Int,
    val imageUrl: String? = null,
    val category: String
)
