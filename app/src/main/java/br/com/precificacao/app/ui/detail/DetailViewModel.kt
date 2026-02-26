package br.com.precificacao.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.precificacao.app.analytics.AnalyticsLogger
import br.com.precificacao.app.data.local.ContextDataStore
import br.com.precificacao.app.data.local.InstallationIdRepository
import br.com.precificacao.app.data.local.UserContextProvider
import br.com.precificacao.app.data.model.PriceQuote
import br.com.precificacao.app.data.model.Product
import br.com.precificacao.app.data.repository.PricingRepository
import br.com.precificacao.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ProductRepository(application)
    private val userContextProvider = UserContextProvider(ContextDataStore(application))
    private val pricingRepository = PricingRepository(
        installationIdRepo = InstallationIdRepository(application),
        userContextProvider = userContextProvider
    )
    private val productId: String = savedStateHandle["productId"] ?: ""

    private val _product = MutableStateFlow<Product?>(null)
    val product: StateFlow<Product?> = _product.asStateFlow()

    private val _priceQuote = MutableStateFlow<PriceQuote?>(null)
    val priceQuote: StateFlow<PriceQuote?> = _priceQuote.asStateFlow()

    init {
        val product = repository.getProductById(productId)
        _product.value = product
        product?.let { p ->
            viewModelScope.launch {
                val quote = pricingRepository.fetchPriceQuote(
                    productId = p.productId,
                    basePriceCents = p.basePriceCents
                )

                val ctx = userContextProvider.getFullContext()

                if (quote != null) {
                    _priceQuote.value = quote

                    AnalyticsLogger.logPriceShown(
                        quote = quote,
                        category = p.category,
                        deviceModel = ctx.deviceModel,
                        dayOfMonth = ctx.dayOfMonth,
                        regionUf = ctx.regionUf,
                        deviceTier = ctx.deviceTier
                    )
                }

                val priceShown = quote?.priceCents ?: p.basePriceCents

                AnalyticsLogger.logViewProduct(
                    productId = p.productId,
                    category = p.category,
                    priceShownCents = priceShown,
                    regionUf = ctx.regionUf,
                    deviceTier = ctx.deviceTier,
                    deviceModel = ctx.deviceModel,
                    dayOfMonth = ctx.dayOfMonth
                )
            }
        }
    }
}
