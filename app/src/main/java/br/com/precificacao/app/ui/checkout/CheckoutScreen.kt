package br.com.precificacao.app.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.precificacao.app.ui.catalog.formatPrice

@Composable
fun CheckoutScreen(
    cartTotalCents: Int,
    discountCents: Int = 0,
    finalTotalCents: Int = cartTotalCents,
    offerVariantId: String? = null,
    cartSize: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var purchased by remember { mutableStateOf(false) }

    if (purchased) {
        // Tela de sucesso
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Compra realizada!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Obrigado pela sua compra simulada.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Voltar ao Catálogo")
            }
        }
    } else {
        // Tela de confirmação
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCartCheckout,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Confirmar Compra",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "$cartSize ${if (cartSize == 1) "item" else "itens"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (discountCents > 0) {
                Text(
                    text = "Subtotal: ${formatPrice(cartTotalCents)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )

                val discountLabel = offerVariantId?.let {
                    val pct = when (it) {
                        "O5" -> "5%"
                        "O10" -> "10%"
                        else -> ""
                    }
                    if (pct.isNotEmpty()) "Cupom $pct: " else "Desconto: "
                } ?: "Desconto: "

                Text(
                    text = "$discountLabel-${formatPrice(discountCents)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Total: ${formatPrice(finalTotalCents)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onConfirm()
                    purchased = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar Pagamento (Simulado)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar")
            }
        }
    }
}
