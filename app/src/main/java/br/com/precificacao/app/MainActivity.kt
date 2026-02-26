package br.com.precificacao.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.precificacao.app.ui.navigation.AppNavigation
import br.com.precificacao.app.ui.theme.LojaSimuladaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LojaSimuladaTheme {
                AppNavigation()
            }
        }
    }
}
