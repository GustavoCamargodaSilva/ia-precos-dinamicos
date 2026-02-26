package br.com.precificacao.app.data.repository

import android.content.Context
import br.com.precificacao.app.data.model.Product
import kotlinx.serialization.json.Json

class ProductRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedProducts: List<Product>? = null

    fun getProducts(): List<Product> {
        cachedProducts?.let { return it }
        val jsonString = context.assets.open("products.json")
            .bufferedReader()
            .use { it.readText() }
        val products = json.decodeFromString<List<Product>>(jsonString)
        cachedProducts = products
        return products
    }

    fun getProductById(productId: String): Product? {
        return getProducts().find { it.productId == productId }
    }
}
