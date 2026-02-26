package br.com.precificacao.app.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.precificacao.app.data.model.CartItem
import br.com.precificacao.app.ui.catalog.formatPrice

@Composable
fun CartScreen(
    cartViewModel: CartViewModel,
    onCheckout: () -> Unit
) {
    val cartItems by cartViewModel.cartItems.collectAsState()
    val offerQuote by cartViewModel.offerQuote.collectAsState()
    val offerLoading by cartViewModel.offerLoading.collectAsState()
    var offerVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        cartViewModel.markCartScreenEntered()
        cartViewModel.logViewCart()
        cartViewModel.fetchOffer()
    }

    // Handle delayed timing: hide banner, wait 15s, re-fetch
    LaunchedEffect(offerQuote?.timingDecision) {
        if (offerQuote?.timingDecision == "delayed") {
            offerVisible = false
            delay(15_000L)
            cartViewModel.fetchOffer()
            offerVisible = true
        } else {
            offerVisible = true
        }
    }

    if (cartItems.isEmpty()) {
        EmptyCart()
    } else {
        val subtotal = cartViewModel.getCartTotalCents()
        val discount = if (offerVisible) (offerQuote?.discountCents ?: 0) else 0
        val finalTotal = subtotal - discount

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartItems, key = { it.productId }) { item ->
                    CartItemCard(
                        item = item,
                        productName = cartViewModel.productRepository
                            .getProductById(item.productId)?.name ?: item.productId,
                        onIncrement = { cartViewModel.incrementQuantity(item.productId) },
                        onDecrement = { cartViewModel.decrementQuantity(item.productId) },
                        onRemove = { cartViewModel.removeItem(item.productId) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Offer banner
            if (offerLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            offerQuote?.let { offer ->
                if (offer.discountCents > 0 && offerVisible) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cupom: ${(offer.discountPercent * 100).toInt()}% OFF",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Desconto: -${formatPrice(offer.discountCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = "Cupom",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Subtotal (riscado se tem desconto)
            if (discount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtotal",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPrice(subtotal),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Total final
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatPrice(finalTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finalizar Compra")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { cartViewModel.clearCart() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Limpar Carrinho")
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    productName: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatPrice(item.unitPriceCents)} cada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Subtotal: ${formatPrice(item.unitPriceCents * item.quantity)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement) {
                    Icon(Icons.Default.Remove, contentDescription = "Diminuir")
                }
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = onIncrement) {
                    Icon(Icons.Default.Add, contentDescription = "Aumentar")
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remover",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCart() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = "Carrinho vazio",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Adicione produtos do catalogo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
