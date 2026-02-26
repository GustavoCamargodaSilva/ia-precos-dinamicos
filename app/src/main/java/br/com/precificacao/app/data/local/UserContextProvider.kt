package br.com.precificacao.app.data.local

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.first
import java.util.Calendar

data class FullUserContext(
    val regionUf: String,
    val deviceTier: String,
    val dayOfMonth: Int,
    val deviceModel: String,
    val contextKey: String
)

class UserContextProvider(private val contextDataStore: ContextDataStore) {

    companion object {
        private const val TAG = "UserContextProvider"

        fun cartBucket(cartSize: Int): String = when {
            cartSize == 0 -> "cart0"
            cartSize in 1..2 -> "cart1_2"
            else -> "cart3p"
        }
    }

    suspend fun getFullContext(cartSize: Int = 0): FullUserContext {
        val ctx = contextDataStore.getUserContext().first()
        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val deviceModel = Build.MODEL
        val bucket = cartBucket(cartSize)
        val contextKey =
            "${ctx.regionUf}|${ctx.deviceTier}|day_$dayOfMonth|$bucket"

        Log.d(TAG, "context_key=$contextKey, device_model=$deviceModel")

        return FullUserContext(
            regionUf = ctx.regionUf,
            deviceTier = ctx.deviceTier,
            dayOfMonth = dayOfMonth,
            deviceModel = deviceModel,
            contextKey = contextKey
        )
    }
}
