package br.com.precificacao.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val productId: String,
    val quantity: Int,
    val unitPriceCents: Int,
    val variantId: String = "unknown",
    val contextKey: String = "",
    val impressionId: String = "",
    val addedAtEpochMs: Long
)
