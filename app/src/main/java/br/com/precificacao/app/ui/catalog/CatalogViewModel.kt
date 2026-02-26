package br.com.precificacao.app.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import br.com.precificacao.app.data.model.Product
import br.com.precificacao.app.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductRepository(application)

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    init {
        _products.value = repository.getProducts()
    }
}
