package br.com.precificacao.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.precificacao.app.data.remote.PriceService
import br.com.precificacao.app.data.remote.SalesReportResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReportViewModel : ViewModel() {

    private val _report = MutableStateFlow<SalesReportResponse?>(null)
    val report: StateFlow<SalesReportResponse?> = _report

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadReport()
    }

    fun loadReport() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = PriceService.getSalesReport()
            if (result != null) {
                _report.value = result
            } else {
                _error.value = "Erro ao carregar relatório"
            }
            _loading.value = false
        }
    }
}
