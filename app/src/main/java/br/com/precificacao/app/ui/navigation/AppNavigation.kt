package br.com.precificacao.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.precificacao.app.analytics.AnalyticsLogger
import br.com.precificacao.app.ui.cart.CartScreen
import br.com.precificacao.app.ui.cart.CartViewModel
import br.com.precificacao.app.ui.catalog.CatalogScreen
import br.com.precificacao.app.ui.checkout.CheckoutScreen
import br.com.precificacao.app.ui.detail.DetailScreen
import br.com.precificacao.app.ui.report.ReportScreen
import br.com.precificacao.app.ui.settings.SettingsScreen
import br.com.precificacao.app.ui.settings.SettingsViewModel

object Routes {
    const val CATALOG = "catalog"
    const val DETAIL = "detail/{productId}"
    const val CART = "cart"
    const val CHECKOUT = "checkout"
    const val SETTINGS = "settings"
    const val REPORT = "report"

    fun detailRoute(productId: String) = "detail/$productId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val cartViewModel: CartViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val cartItems by cartViewModel.cartItems.collectAsState()
    val cartSize = cartItems.sumOf { it.quantity }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val title = when {
        currentRoute == Routes.CATALOG -> "Loja Simulada"
        currentRoute?.startsWith("detail") == true -> "Detalhes"
        currentRoute == Routes.CART -> "Carrinho"
        currentRoute == Routes.CHECKOUT -> "Checkout"
        currentRoute == Routes.SETTINGS -> "Configurações"
        currentRoute == Routes.REPORT -> "Relatório de Vendas"
        else -> "Loja Simulada"
    }

    val showBackButton = currentRoute != Routes.CATALOG

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar"
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute == Routes.CATALOG) {
                        IconButton(onClick = {
                            navController.navigate(Routes.REPORT) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = "Relatório"
                            )
                        }
                        IconButton(onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Configurações"
                            )
                        }
                    }
                    if (currentRoute != Routes.CART && currentRoute != Routes.CHECKOUT) {
                        IconButton(onClick = {
                            navController.navigate(Routes.CART) {
                                launchSingleTop = true
                            }
                        }) {
                            BadgedBox(
                                badge = {
                                    if (cartSize > 0) {
                                        Badge { Text("$cartSize") }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = "Carrinho"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CATALOG,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.CATALOG) {
                CatalogScreen(
                    onProductClick = { productId ->
                        navController.navigate(Routes.detailRoute(productId))
                    }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("productId") { type = NavType.StringType })
            ) {
                DetailScreen(
                    onAddToCart = { productId, priceCents, variantId, contextKey, impressionId ->
                        cartViewModel.addItem(productId, priceCents, variantId, contextKey, impressionId)
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.CART) {
                CartScreen(
                    cartViewModel = cartViewModel,
                    onCheckout = {
                        cartViewModel.markCheckoutClicked()
                        cartViewModel.recordBeginCheckout()
                        val offerQuote = cartViewModel.offerQuote.value
                        AnalyticsLogger.logBeginCheckout(
                            cartSize = cartViewModel.getCartSize(),
                            cartTotalCents = cartViewModel.getCartTotalCents(),
                            variantsSummary = cartViewModel.getVariantsSummary(),
                            offerVariantId = offerQuote?.variantId,
                            offerImpressionId = offerQuote?.offerImpressionId
                        )
                        navController.navigate(Routes.CHECKOUT) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.CHECKOUT) {
                val totalCents = cartViewModel.getCartTotalCents()
                val itemsCount = cartViewModel.getCartSize()
                val variantsSummary = cartViewModel.getVariantsSummary()
                val lastImpressionId = cartViewModel.getLastImpressionId()
                val offerQuote = cartViewModel.offerQuote.value
                val discountCents = offerQuote?.discountCents ?: 0
                val finalTotalCents = totalCents - discountCents
                CheckoutScreen(
                    cartTotalCents = totalCents,
                    discountCents = discountCents,
                    finalTotalCents = finalTotalCents,
                    offerVariantId = offerQuote?.variantId,
                    cartSize = itemsCount,
                    onConfirm = {
                        AnalyticsLogger.logPurchaseSimulated(
                            valueCents = finalTotalCents,
                            itemsCount = itemsCount,
                            variantsSummary = variantsSummary,
                            impressionId = lastImpressionId,
                            offerVariantId = offerQuote?.variantId,
                            offerImpressionId = offerQuote?.offerImpressionId,
                            discountCents = discountCents
                        )
                        cartViewModel.recordPurchase(
                            valueCents = finalTotalCents,
                            itemsCount = itemsCount
                        )
                        cartViewModel.recordOfferPurchase(valueCents = finalTotalCents)
                        cartViewModel.clearCart()
                    },
                    onCancel = {
                        navController.popBackStack(Routes.CATALOG, inclusive = false)
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(settingsViewModel = settingsViewModel)
            }

            composable(Routes.REPORT) {
                ReportScreen()
            }
        }
    }
}
