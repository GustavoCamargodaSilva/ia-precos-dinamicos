package br.com.precificacao.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PriceQuote(
    val productId: String,
    val priceCents: Int,
    val variantId: String,
    val validUntilEpochMs: Long,
    val contextKey: String,
    val impressionId: String = ""
)
