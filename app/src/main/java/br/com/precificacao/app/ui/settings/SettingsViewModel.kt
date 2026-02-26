package br.com.precificacao.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.precificacao.app.data.local.ContextDataStore
import br.com.precificacao.app.data.local.UserContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val contextDataStore = ContextDataStore(application)

    val userContext: StateFlow<UserContext> = contextDataStore.getUserContext()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserContext())

    fun updateRegionUf(uf: String) {
        viewModelScope.launch {
            contextDataStore.saveRegionUf(uf)
        }
    }

    fun updateDeviceTier(tier: String) {
        viewModelScope.launch {
            contextDataStore.saveDeviceTier(tier)
        }
    }
}
