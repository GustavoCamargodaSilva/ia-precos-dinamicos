package br.com.precificacao.app.data.repository

import br.com.precificacao.app.data.local.CartDataStore
import br.com.precificacao.app.data.model.CartItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class CartRepository(private val cartDataStore: CartDataStore) {

    private val _items = MutableStateFlow<List<CartItem>>(emptyList())
    val items: Flow<List<CartItem>> = _items.asStateFlow()

    suspend fun loadCart() {
        _items.value = cartDataStore.getCartItems().first()
    }

    suspend fun addItem(
        productId: String,
        unitPriceCents: Int,
        variantId: String = "unknown",
        contextKey: String = "",
        impressionId: String = ""
    ) {
        val current = _items.value.toMutableList()
        val existing = current.find { it.productId == productId }
        if (existing != null) {
            val index = current.indexOf(existing)
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(
                CartItem(
                    productId = productId,
                    quantity = 1,
                    unitPriceCents = unitPriceCents,
                    variantId = variantId,
                    contextKey = contextKey,
                    impressionId = impressionId,
                    addedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        _items.value = current
        cartDataStore.saveCartItems(current)
    }

    suspend fun removeItem(productId: String) {
        val current = _items.value.filter { it.productId != productId }
        _items.value = current
        cartDataStore.saveCartItems(current)
    }

    suspend fun updateQuantity(productId: String, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeItem(productId)
            return
        }
        val current = _items.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = newQuantity)
            _items.value = current
            cartDataStore.saveCartItems(current)
        }
    }

    suspend fun clearCart() {
        _items.value = emptyList()
        cartDataStore.saveCartItems(emptyList())
    }

    fun getCartSize(): Int = _items.value.sumOf { it.quantity }

    fun getCartTotalCents(): Int = _items.value.sumOf { it.unitPriceCents * it.quantity }
}
