package br.com.precificacao.app.data.repository

import br.com.precificacao.app.data.local.InstallationIdRepository
import br.com.precificacao.app.data.local.UserContextProvider
import br.com.precificacao.app.data.model.PriceQuote
import br.com.precificacao.app.data.remote.PriceService

class PricingRepository(
    private val installationIdRepo: InstallationIdRepository,
    private val userContextProvider: UserContextProvider
) {

    suspend fun fetchPriceQuote(
        productId: String,
        basePriceCents: Int,
        cartSize: Int = 0
    ): PriceQuote? {
        val installationId = installationIdRepo.getInstallationId()
        val ctx = userContextProvider.getFullContext(cartSize)

        val response = PriceService.getPriceBandit(
            installationId = installationId,
            productId = productId,
            basePriceCents = basePriceCents,
            contextKey = ctx.contextKey
        ) ?: return null

        return PriceQuote(
            productId = productId,
            priceCents = response.priceCents,
            variantId = response.variantId,
            validUntilEpochMs = response.validUntilEpochMs,
            contextKey = ctx.contextKey,
            impressionId = response.impressionId
        )
    }

    suspend fun recordAddToCartOutcome(
        productId: String,
        impressionId: String
    ): Boolean {
        val installationId = installationIdRepo.getInstallationId()
        val result = PriceService.recordOutcome(
            installationId = installationId,
            productId = productId,
            impressionId = impressionId
        )
        return result.success
    }
}
