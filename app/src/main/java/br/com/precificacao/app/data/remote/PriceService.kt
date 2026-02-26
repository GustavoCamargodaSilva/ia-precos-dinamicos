package br.com.precificacao.app.data.remote

import android.util.Log
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

data class BanditPriceResponse(
    val impressionId: String,
    val variantId: String,
    val priceCents: Int,
    val validUntilEpochMs: Long
)

data class OutcomeResponse(
    val success: Boolean,
    val reason: String?
)

data class OfferQuoteResponse(
    val offerImpressionId: String,
    val variantId: String,
    val discountPercent: Double,
    val discountCents: Int,
    val finalTotalCents: Int,
    val policy: String,
    val timingDecision: String = "on_view_cart",
    val propBucket: String = "p1"
)

data class OfferSummaryResponse(
    val showsO0: Int, val showsO5: Int, val showsO10: Int,
    val purchasesO0: Int, val purchasesO5: Int, val purchasesO10: Int,
    val rateO0: Double, val rateO5: Double, val rateO10: Double,
    val totalShows: Int, val totalPurchases: Int, val overallRate: Double,
    val netRevenueO0: Int = 0, val netRevenueO5: Int = 0, val netRevenueO10: Int = 0,
    val netRevPerShowO0: Double = 0.0, val netRevPerShowO5: Double = 0.0, val netRevPerShowO10: Double = 0.0
)

data class SalesReportResponse(
    val showsA: Int, val showsB: Int, val showsC: Int,
    val purchasesA: Int, val purchasesB: Int, val purchasesC: Int,
    val rateA: Double, val rateB: Double, val rateC: Double,
    val revenueA: Int, val revenueB: Int, val revenueC: Int,
    val totalShows: Int, val totalPurchases: Int,
    val totalRevenue: Int, val overallRate: Double
)

object PriceService {

    private const val TAG = "PriceService"
    private val functions = Firebase.functions

    suspend fun getPriceBandit(
        installationId: String,
        productId: String,
        basePriceCents: Int,
        contextKey: String
    ): BanditPriceResponse? {
        return try {
            val data = hashMapOf(
                "installationId" to installationId,
                "productId" to productId,
                "basePriceCents" to basePriceCents,
                "contextKey" to contextKey
            )
            val result = functions
                .getHttpsCallable("getPriceBandit")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any> ?: return null
            BanditPriceResponse(
                impressionId = map["impressionId"] as? String ?: "",
                variantId = map["variantId"] as? String ?: "unknown",
                priceCents = (map["priceCents"] as? Number)?.toInt() ?: 0,
                validUntilEpochMs = (map["validUntil"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar getPriceBandit: ${e.message}")
            null
        }
    }

    suspend fun recordOutcome(
        installationId: String,
        productId: String,
        impressionId: String
    ): OutcomeResponse {
        return try {
            val data = hashMapOf(
                "installationId" to installationId,
                "productId" to productId,
                "impressionId" to impressionId
            )
            val result = functions
                .getHttpsCallable("recordOutcomeAddToCart")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any>
            OutcomeResponse(
                success = map?.get("success") as? Boolean ?: false,
                reason = map?.get("reason") as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar recordOutcome: ${e.message}")
            OutcomeResponse(success = false, reason = e.message)
        }
    }

    suspend fun recordBeginCheckout(
        installationId: String,
        impressionId: String
    ): OutcomeResponse {
        return try {
            val data = hashMapOf(
                "installationId" to installationId,
                "impressionId" to impressionId
            )
            val result = functions
                .getHttpsCallable("recordOutcomeBeginCheckout")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any>
            OutcomeResponse(
                success = map?.get("success") as? Boolean ?: false,
                reason = map?.get("reason") as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar recordBeginCheckout: ${e.message}")
            OutcomeResponse(success = false, reason = e.message)
        }
    }

    suspend fun getSalesReport(): SalesReportResponse? {
        return try {
            val result = functions
                .getHttpsCallable("getSalesVariantSummary")
                .call()
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any> ?: return null
            val shows = map["shows"] as? Map<String, Any> ?: emptyMap()
            val purchases = map["purchases"] as? Map<String, Any> ?: emptyMap()
            val purchaseRate = map["purchase_rate"] as? Map<String, Any> ?: emptyMap()
            val revenueCents = map["revenue_cents"] as? Map<String, Any> ?: emptyMap()
            val totals = map["totals"] as? Map<String, Any> ?: emptyMap()

            SalesReportResponse(
                showsA = (shows["A"] as? Number)?.toInt() ?: 0,
                showsB = (shows["B"] as? Number)?.toInt() ?: 0,
                showsC = (shows["C"] as? Number)?.toInt() ?: 0,
                purchasesA = (purchases["A"] as? Number)?.toInt() ?: 0,
                purchasesB = (purchases["B"] as? Number)?.toInt() ?: 0,
                purchasesC = (purchases["C"] as? Number)?.toInt() ?: 0,
                rateA = (purchaseRate["A"] as? Number)?.toDouble() ?: 0.0,
                rateB = (purchaseRate["B"] as? Number)?.toDouble() ?: 0.0,
                rateC = (purchaseRate["C"] as? Number)?.toDouble() ?: 0.0,
                revenueA = (revenueCents["A"] as? Number)?.toInt() ?: 0,
                revenueB = (revenueCents["B"] as? Number)?.toInt() ?: 0,
                revenueC = (revenueCents["C"] as? Number)?.toInt() ?: 0,
                totalShows = (totals["shows"] as? Number)?.toInt() ?: 0,
                totalPurchases = (totals["purchases"] as? Number)?.toInt() ?: 0,
                totalRevenue = (totals["revenue_cents"] as? Number)?.toInt() ?: 0,
                overallRate = (totals["overall_rate"] as? Number)?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar getSalesReport: ${e.message}")
            null
        }
    }

    suspend fun recordPurchase(
        installationId: String,
        impressionId: String,
        valueCents: Int = 0,
        itemsCount: Int = 0
    ): OutcomeResponse {
        return try {
            val data = hashMapOf(
                "installationId" to installationId,
                "impressionId" to impressionId,
                "valueCents" to valueCents,
                "itemsCount" to itemsCount
            )
            val result = functions
                .getHttpsCallable("recordOutcomePurchase")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any>
            OutcomeResponse(
                success = map?.get("success") as? Boolean ?: false,
                reason = map?.get("reason") as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar recordPurchase: ${e.message}")
            OutcomeResponse(success = false, reason = e.message)
        }
    }

    suspend fun getCartOfferBandit(
        installationId: String,
        cartTotalCents: Int,
        offerContextKey: String,
        cartItemsCount: Int = 0,
        numCartOpens: Int = 1,
        timeInCartSec: Int = 0,
        removedItemsCount: Int = 0,
        beginCheckoutClicked: Boolean = false
    ): OfferQuoteResponse? {
        return try {
            val data = hashMapOf(
                "installationId" to installationId,
                "cartTotalCents" to cartTotalCents,
                "offerContextKey" to offerContextKey,
                "cartItemsCount" to cartItemsCount,
                "numCartOpens" to numCartOpens,
                "timeInCartSec" to timeInCartSec,
                "removedItemsCount" to removedItemsCount,
                "beginCheckoutClicked" to beginCheckoutClicked
            )
            val result = functions
                .getHttpsCallable("getCartOfferBandit")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any> ?: return null
            OfferQuoteResponse(
                offerImpressionId = map["offerImpressionId"] as? String ?: "",
                variantId = map["variantId"] as? String ?: "O0",
                discountPercent = (map["discountPercent"] as? Number)?.toDouble() ?: 0.0,
                discountCents = (map["discountCents"] as? Number)?.toInt() ?: 0,
                finalTotalCents = (map["finalTotalCents"] as? Number)?.toInt() ?: 0,
                policy = map["policy"] as? String ?: "unknown",
                timingDecision = map["timingDecision"] as? String ?: "on_view_cart",
                propBucket = map["propBucket"] as? String ?: "p1"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar getCartOfferBandit: ${e.message}")
            null
        }
    }

    suspend fun recordOfferPurchase(
        installationId: String,
        offerImpressionId: String,
        valueCents: Int = 0
    ): OutcomeResponse {
        return try {
            val data = hashMapOf(
                "installationId" to installationId,
                "offerImpressionId" to offerImpressionId,
                "valueCents" to valueCents
            )
            val result = functions
                .getHttpsCallable("recordOfferOutcomePurchase")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any>
            OutcomeResponse(
                success = map?.get("success") as? Boolean ?: false,
                reason = map?.get("reason") as? String
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar recordOfferPurchase: ${e.message}")
            OutcomeResponse(success = false, reason = e.message)
        }
    }

    suspend fun getOfferSummary(): OfferSummaryResponse? {
        return try {
            val result = functions
                .getHttpsCallable("getOfferSummary")
                .call()
                .await()

            @Suppress("UNCHECKED_CAST")
            val map = result.data as? Map<String, Any> ?: return null
            val shows = map["shows"] as? Map<String, Any> ?: emptyMap()
            val purchases = map["purchases"] as? Map<String, Any> ?: emptyMap()
            val purchaseRate = map["purchase_rate"] as? Map<String, Any> ?: emptyMap()
            val totals = map["totals"] as? Map<String, Any> ?: emptyMap()

            OfferSummaryResponse(
                showsO0 = (shows["O0"] as? Number)?.toInt() ?: 0,
                showsO5 = (shows["O5"] as? Number)?.toInt() ?: 0,
                showsO10 = (shows["O10"] as? Number)?.toInt() ?: 0,
                purchasesO0 = (purchases["O0"] as? Number)?.toInt() ?: 0,
                purchasesO5 = (purchases["O5"] as? Number)?.toInt() ?: 0,
                purchasesO10 = (purchases["O10"] as? Number)?.toInt() ?: 0,
                rateO0 = (purchaseRate["O0"] as? Number)?.toDouble() ?: 0.0,
                rateO5 = (purchaseRate["O5"] as? Number)?.toDouble() ?: 0.0,
                rateO10 = (purchaseRate["O10"] as? Number)?.toDouble() ?: 0.0,
                totalShows = (totals["shows"] as? Number)?.toInt() ?: 0,
                totalPurchases = (totals["purchases"] as? Number)?.toInt() ?: 0,
                overallRate = (totals["overall_rate"] as? Number)?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar getOfferSummary: ${e.message}")
            null
        }
    }
}
