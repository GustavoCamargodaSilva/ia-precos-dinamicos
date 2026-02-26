package br.com.precificacao.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.precificacao.app.data.model.CartItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "cart_prefs")

class CartDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val CART_KEY = stringPreferencesKey("cart_items")
    }

    fun getCartItems(): Flow<List<CartItem>> {
        return context.dataStore.data.map { prefs ->
            val raw = prefs[CART_KEY] ?: "[]"
            json.decodeFromString<List<CartItem>>(raw)
        }
    }

    suspend fun saveCartItems(items: List<CartItem>) {
        context.dataStore.edit { prefs ->
            prefs[CART_KEY] = json.encodeToString(items)
        }
    }
}
