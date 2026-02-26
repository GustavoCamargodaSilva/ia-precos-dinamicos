package br.com.precificacao.app.analytics

import android.os.Bundle
import android.util.Log
import br.com.precificacao.app.data.model.PriceQuote
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object AnalyticsLogger {

    private const val TAG = "AnalyticsLogger"
    private val firebaseAnalytics: FirebaseAnalytics = Firebase.analytics

    fun logPriceShown(
        quote: PriceQuote,
        category: String?,
        deviceModel: String?,
        dayOfMonth: Int?,
        regionUf: String?,
        deviceTier: String?
    ) {
        Log.d(TAG, "price_shown | product_id=${quote.productId}, " +
                "price_shown_cents=${quote.priceCents}, variant_id=${quote.variantId}, " +
                "context_key=${quote.contextKey}, impression_id=${quote.impressionId}, " +
                "category=$category")
        firebaseAnalytics.logEvent("price_shown", Bundle().apply {
            putString("product_id", quote.productId)
            putLong("price_shown_cents", quote.priceCents.toLong())
            putString("variant_id", quote.variantId)
            putString("context_key", quote.contextKey)
            putString("impression_id", quote.impressionId)
            category?.let { putString("category", it) }
            deviceModel?.let { putString("device_model", it) }
            dayOfMonth?.let { putLong("day_of_month", it.toLong()) }
            regionUf?.let { putString("region_uf", it) }
            deviceTier?.let { putString("device_tier", it) }
        })
    }

    fun logViewProduct(
        productId: String,
        category: String,
        priceShownCents: Int,
        regionUf: String,
        deviceTier: String,
        deviceModel: String,
        dayOfMonth: Int
    ) {
        Log.d(TAG, "view_product | product_id=$productId, category=$category, " +
                "price_shown_cents=$priceShownCents, region_uf=$regionUf, " +
                "device_tier=$deviceTier, device_model=$deviceModel, day_of_month=$dayOfMonth")
        firebaseAnalytics.logEvent("view_product", Bundle().apply {
            putString("product_id", productId)
            putString("category", category)
            putLong("price_shown_cents", priceShownCents.toLong())
            putString("region_uf", regionUf)
            putString("device_tier", deviceTier)
            putString("device_model", deviceModel)
            putLong("day_of_month", dayOfMonth.toLong())
        })
    }

    fun logAddToCart(
        productId: String,
        quantity: Int,
        unitPriceCents: Int,
        cartSize: Int,
        variantId: String? = null,
        contextKey: String? = null,
        impressionId: String? = null
    ) {
        Log.d(TAG, "add_to_cart | product_id=$productId, quantity=$quantity, " +
                "unit_price_cents=$unitPriceCents, cart_size=$cartSize, " +
                "variant_id=$variantId, context_key=$contextKey, " +
                "impression_id=$impressionId")
        firebaseAnalytics.logEvent("add_to_cart", Bundle().apply {
            putString("product_id", productId)
            putLong("quantity", quantity.toLong())
            putLong("unit_price_cents", unitPriceCents.toLong())
            putLong("cart_size", cartSize.toLong())
            variantId?.let { putString("variant_id", it) }
            contextKey?.let { putString("context_key", it) }
            impressionId?.let { putString("impression_id", it) }
        })
    }

    fun logRemoveFromCart(
        productId: String,
        quantity: Int,
        cartSize: Int
    ) {
        Log.d(TAG, "remove_from_cart | product_id=$productId, quantity=$quantity, cart_size=$cartSize")
        firebaseAnalytics.logEvent("remove_from_cart", Bundle().apply {
            putString("product_id", productId)
            putLong("quantity", quantity.toLong())
            putLong("cart_size", cartSize.toLong())
        })
    }

    fun logViewCart(
        cartSize: Int,
        cartTotalCents: Int
    ) {
        Log.d(TAG, "view_cart | cart_size=$cartSize, cart_total_cents=$cartTotalCents")
        firebaseAnalytics.logEvent("view_cart", Bundle().apply {
            putLong("cart_size", cartSize.toLong())
            putLong("cart_total_cents", cartTotalCents.toLong())
        })
    }

    fun logBeginCheckout(
        cartSize: Int,
        cartTotalCents: Int,
        variantsSummary: String? = null,
        offerVariantId: String? = null,
        offerImpressionId: String? = null
    ) {
        Log.d(TAG, "begin_checkout | cart_size=$cartSize, " +
                "cart_total_cents=$cartTotalCents, variants_summary=$variantsSummary, " +
                "offer_variant_id=$offerVariantId, offer_impression_id=$offerImpressionId")
        firebaseAnalytics.logEvent("begin_checkout", Bundle().apply {
            putLong("cart_size", cartSize.toLong())
            putLong("cart_total_cents", cartTotalCents.toLong())
            variantsSummary?.let { putString("variants_summary", it) }
            offerVariantId?.let { putString("offer_variant_id", it) }
            offerImpressionId?.let { putString("offer_impression_id", it) }
        })
    }

    fun logPurchaseSimulated(
        valueCents: Int,
        itemsCount: Int,
        variantsSummary: String? = null,
        impressionId: String? = null,
        offerVariantId: String? = null,
        offerImpressionId: String? = null,
        discountCents: Int? = null
    ) {
        Log.d(TAG, "purchase | value_cents=$valueCents, " +
                "items_count=$itemsCount, variants_summary=$variantsSummary, " +
                "impression_id=$impressionId, offer_variant_id=$offerVariantId, " +
                "offer_impression_id=$offerImpressionId, discount_cents=$discountCents")
        firebaseAnalytics.logEvent("purchase", Bundle().apply {
            putLong("value_cents", valueCents.toLong())
            putLong("items_count", itemsCount.toLong())
            variantsSummary?.let { putString("variants_summary", it) }
            impressionId?.let { putString("impression_id", it) }
            offerVariantId?.let { putString("offer_variant_id", it) }
            offerImpressionId?.let { putString("offer_impression_id", it) }
            discountCents?.let { putLong("discount_cents", it.toLong()) }
        })
    }

    fun logOfferShown(
        offerImpressionId: String,
        offerVariantId: String,
        discountPercent: Double,
        discountCents: Int,
        cartTotalCents: Int,
        offerContextKey: String
    ) {
        Log.d(TAG, "offer_shown | offer_impression_id=$offerImpressionId, " +
                "offer_variant_id=$offerVariantId, discount_percent=$discountPercent, " +
                "discount_cents=$discountCents, cart_total_cents=$cartTotalCents, " +
                "offer_context_key=$offerContextKey")
        firebaseAnalytics.logEvent("offer_shown", Bundle().apply {
            putString("offer_impression_id", offerImpressionId)
            putString("offer_variant_id", offerVariantId)
            putDouble("discount_percent", discountPercent)
            putLong("discount_cents", discountCents.toLong())
            putLong("cart_total_cents", cartTotalCents.toLong())
            putString("offer_context_key", offerContextKey)
        })
    }
}
