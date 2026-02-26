package br.com.precificacao.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.contextDataStore by preferencesDataStore(name = "context_prefs")

data class UserContext(
    val regionUf: String = "SP",
    val deviceTier: String = "mid"
)

class ContextDataStore(private val context: Context) {

    companion object {
        private val REGION_UF_KEY = stringPreferencesKey("region_uf")
        private val DEVICE_TIER_KEY = stringPreferencesKey("device_tier")

        val UF_OPTIONS = listOf(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
        )

        val DEVICE_TIER_OPTIONS = listOf("low", "mid", "high")
    }

    fun getUserContext(): Flow<UserContext> {
        return context.contextDataStore.data.map { prefs ->
            UserContext(
                regionUf = prefs[REGION_UF_KEY] ?: "SP",
                deviceTier = prefs[DEVICE_TIER_KEY] ?: "mid"
            )
        }
    }

    suspend fun saveRegionUf(uf: String) {
        context.contextDataStore.edit { prefs ->
            prefs[REGION_UF_KEY] = uf
        }
    }

    suspend fun saveDeviceTier(tier: String) {
        context.contextDataStore.edit { prefs ->
            prefs[DEVICE_TIER_KEY] = tier
        }
    }
}
