package br.com.precificacao.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.installationDataStore by preferencesDataStore(name = "installation_prefs")

class InstallationIdRepository(private val context: Context) {

    companion object {
        private val INSTALLATION_ID_KEY = stringPreferencesKey("installation_id")
    }

    suspend fun getInstallationId(): String {
        val prefs = context.installationDataStore.data.first()
        val existing = prefs[INSTALLATION_ID_KEY]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        context.installationDataStore.edit { it[INSTALLATION_ID_KEY] = newId }
        return newId
    }
}
