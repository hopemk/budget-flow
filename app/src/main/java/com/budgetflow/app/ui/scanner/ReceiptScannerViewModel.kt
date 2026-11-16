package com.budgetflow.app.ui.scanner

import androidx.lifecycle.ViewModel
import com.budgetflow.app.domain.model.ParsedReceipt
import com.budgetflow.app.domain.usecase.ReceiptTextParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReceiptScannerViewModel @Inject constructor(
    private val receiptTextParser: ReceiptTextParser
) : ViewModel() {

    private val _statusText = MutableStateFlow("Align receipt in frame and tap Capture")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun setStatus(message: String) {
        _statusText.value = message
    }

    fun parseReceiptText(rawText: String): ParsedReceipt = receiptTextParser.parse(rawText)
}
