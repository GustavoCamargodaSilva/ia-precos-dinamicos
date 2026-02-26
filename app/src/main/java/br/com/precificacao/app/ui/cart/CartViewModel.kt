package br.com.precificacao.app.ui.cart

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.precificacao.app.analytics.AnalyticsLogger
import br.com.precificacao.app.data.local.CartDataStore
import br.com.precificacao.app.data.local.ContextDataStore
import br.com.precificacao.app.data.local.InstallationIdRepository
import br.com.precificacao.app.data.model.CartItem
import br.com.precificacao.app.data.remote.OfferQuoteResponse
import br.com.precificacao.app.data.remote.PriceService
import br.com.precificacao.app.data.repository.CartRepository
import br.com.precificacao.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class CartViewModel(application: Application) : AndroidViewModel(application) {

    private val cartDataStore = CartDataStore(application)
    private val cartRepository = CartRepository(cartDataStore)
    private val contextDataStore = ContextDataStore(application)
    private val installationIdRepo = InstallationIdRepository(application)
    val productRepository = ProductRepository(application)

    val cartItems: StateFlow<List<CartItem>> = cartRepository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Offer bandit state
    private val _offerQuote = MutableStateFlow<OfferQuoteResponse?>(null)
    val offerQuote: StateFlow<OfferQuoteResponse?> = _offerQuote.asStateFlow()

    private val _offerLoading = MutableStateFlow(false)
    val offerLoading: StateFlow<Boolean> = _offerLoading.asStateFlow()

    // Intent / behavioral tracking
    private var cartViewCount = 0
    private var hasClickedCheckout = false
    private var removedItemsCount = 0
    private var cartScreenEnteredAt = 0L

    init {
        viewModelScope.launch {
            cartRepository.loadCart()
        }
    }

    fun addItem(
        productId: String,
        unitPriceCents: Int,
        variantId: String = "unknown",
        contextKey: String = "",
        impressionId: String = ""
    ) {
        viewModelScope.launch {
            cartRepository.addItem(productId, unitPriceCents, variantId, contextKey, impressionId)
            AnalyticsLogger.logAddToCart(
                productId = productId,
                quantity = 1,
                unitPriceCents = unitPriceCents,
                cartSize = cartRepository.getCartSize(),
                variantId = variantId,
                contextKey = contextKey,
                impressionId = impressionId
            )

            if (impressionId.isNotEmpty()) {
                val installationId = installationIdRepo.getInstallationId()
                PriceService.recordOutcome(
                    installationId = installationId,
                    productId = productId,
                    impressionId = impressionId
                )
            }
        }
    }

    fun removeItem(productId: String) {
        viewModelScope.launch {
            removedItemsCount++
            val item = cartItems.value.find { it.productId == productId }
            cartRepository.removeItem(productId)
            AnalyticsLogger.logRemoveFromCart(
                productId = productId,
                quantity = item?.quantity ?: 0,
                cartSize = cartRepository.getCartSize()
            )
        }
    }

    fun incrementQuantity(productId: String) {
        viewModelScope.launch {
            val item = cartItems.value.find { it.productId == productId } ?: return@launch
            cartRepository.updateQuantity(productId, item.quantity + 1)
        }
    }

    fun decrementQuantity(productId: String) {
        viewModelScope.launch {
            val item = cartItems.value.find { it.productId == productId } ?: return@launch
            cartRepository.updateQuantity(productId, item.quantity - 1)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            cartRepository.clearCart()
            _offerQuote.value = null
            cartViewCount = 0
            hasClickedCheckout = false
            removedItemsCount = 0
            cartScreenEnteredAt = 0L
        }
    }

    fun logViewCart() {
        AnalyticsLogger.logViewCart(
            cartSize = cartRepository.getCartSize(),
            cartTotalCents = cartRepository.getCartTotalCents()
        )
    }

    fun getCartTotalCents(): Int = cartRepository.getCartTotalCents()

    fun getCartSize(): Int = cartRepository.getCartSize()

    fun getVariantsSummary(): String {
        return cartItems.value
            .groupBy { it.variantId }
            .map { (variant, items) -> "$variant:${items.sumOf { it.quantity }}" }
            .sorted()
            .joinToString(",")
    }

    fun getLastImpressionId(): String {
        return cartItems.value
            .filter { it.impressionId.isNotEmpty() }
            .maxByOrNull { it.addedAtEpochMs }
            ?.impressionId ?: ""
    }

    fun recordBeginCheckout() {
        val impressionId = getLastImpressionId()
        if (impressionId.isEmpty()) return
        viewModelScope.launch {
            val installationId = installationIdRepo.getInstallationId()
            val result = PriceService.recordBeginCheckout(
                installationId = installationId,
                impressionId = impressionId
            )
            Log.d("CartViewModel", "recordBeginCheckout: success=${result.success}, reason=${result.reason}")
        }
    }

    fun recordPurchase(valueCents: Int, itemsCount: Int) {
        val impressionId = getLastImpressionId()
        if (impressionId.isEmpty()) return
        viewModelScope.launch {
            val installationId = installationIdRepo.getInstallationId()
            val result = PriceService.recordPurchase(
                installationId = installationId,
                impressionId = impressionId,
                valueCents = valueCents,
                itemsCount = itemsCount
            )
            Log.d("CartViewModel", "recordPurchase: success=${result.success}, reason=${result.reason}")
        }
    }

    // ============================================================
    // Offer Bandit with Propensity
    // ============================================================

    fun markCartScreenEntered() {
        if (cartScreenEnteredAt == 0L) {
            cartScreenEnteredAt = System.currentTimeMillis()
        }
    }

    fun getTimeInCartSec(): Int {
        if (cartScreenEnteredAt == 0L) return 0
        return ((System.currentTimeMillis() - cartScreenEnteredAt) / 1000).toInt()
    }

    fun fetchOffer() {
        val totalCents = getCartTotalCents()
        if (totalCents <= 0) {
            _offerQuote.value = null
            return
        }

        viewModelScope.launch {
            _offerLoading.value = true
            try {
                val installationId = installationIdRepo.getInstallationId()
                val contextKey = buildOfferContextKey()
                val quote = PriceService.getCartOfferBandit(
                    installationId = installationId,
                    cartTotalCents = totalCents,
                    offerContextKey = contextKey,
                    cartItemsCount = getCartSize(),
                    numCartOpens = cartViewCount + 1,
                    timeInCartSec = getTimeInCartSec(),
                    removedItemsCount = removedItemsCount,
                    beginCheckoutClicked = hasClickedCheckout
                )
                _offerQuote.value = quote
                if (quote != null) {
                    Log.d("CartViewModel",
                        "Offer: ${quote.variantId} discount=${quote.discountCents} " +
                        "policy=${quote.policy} timing=${quote.timingDecision} " +
                        "propBucket=${quote.propBucket}")
                }
            } catch (e: Exception) {
                Log.e("CartViewModel", "Erro ao buscar oferta: ${e.message}")
                _offerQuote.value = null
            } finally {
                _offerLoading.value = false
                cartViewCount++
            }
        }
    }

    fun markCheckoutClicked() {
        hasClickedCheckout = true
    }

    fun recordOfferPurchase(valueCents: Int) {
        val offer = _offerQuote.value ?: return
        if (offer.offerImpressionId.isEmpty()) return
        viewModelScope.launch {
            val installationId = installationIdRepo.getInstallationId()
            val result = PriceService.recordOfferPurchase(
                installationId = installationId,
                offerImpressionId = offer.offerImpressionId,
                valueCents = valueCents
            )
            Log.d("CartViewModel", "recordOfferPurchase: success=${result.success}, reason=${result.reason}")
        }
    }

    fun getDiscountCents(): Int = _offerQuote.value?.discountCents ?: 0

    fun getFinalTotalCents(): Int = getCartTotalCents() - getDiscountCents()

    private suspend fun buildOfferContextKey(): String {
        val userContext = contextDataStore.getUserContext().first()
        val uf = userContext.regionUf
        val tier = userContext.deviceTier
        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val totalCents = getCartTotalCents()
        val cartValueBucket = when {
            totalCents < 1000 -> "v0"
            totalCents < 5000 -> "v1"
            else -> "v2"
        }

        // prop_bucket e adicionado pelo servidor
        return "$uf|$tier|day_$dayOfMonth|$cartValueBucket"
    }
}
