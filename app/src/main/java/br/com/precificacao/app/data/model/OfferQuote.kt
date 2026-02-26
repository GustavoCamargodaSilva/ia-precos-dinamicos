package br.com.precificacao.app.data.model

data class OfferQuote(
    val offerImpressionId: String,
    val variantId: String,
    val discountPercent: Double,
    val discountCents: Int,
    val finalTotalCents: Int,
    val policy: String
)
