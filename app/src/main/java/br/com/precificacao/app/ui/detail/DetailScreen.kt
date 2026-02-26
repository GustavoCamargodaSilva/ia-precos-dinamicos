package br.com.precificacao.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.precificacao.app.ui.catalog.formatPrice

@Composable
fun DetailScreen(
    onAddToCart: (productId: String, priceCents: Int, variantId: String, contextKey: String, impressionId: String) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val product by viewModel.product.collectAsState()
    val priceQuote by viewModel.priceQuote.collectAsState()

    product?.let { p ->
        val displayPrice = priceQuote?.priceCents ?: p.basePriceCents
        val currentVariantId = priceQuote?.variantId ?: "unknown"
        val currentContextKey = priceQuote?.contextKey ?: ""
        val currentImpressionId = priceQuote?.impressionId ?: ""
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Imagem do produto",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = p.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = p.category.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = p.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = formatPrice(displayPrice),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onAddToCart(p.productId, displayPrice, currentVariantId, currentContextKey, currentImpressionId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Adicionar ao Carrinho")
            }
        }
    } ?: run {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Produto não encontrado")
        }
    }
}
