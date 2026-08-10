package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BridgeUiState(
    val wallet: WxWallet? = null,
    val isConnectingWallet: Boolean = false,
    val liveRate: Double = 98.34,
    val rateChange24h: Double = +0.42,
    val slippagePercent: Double = 0.5,
    val isLoadingRate: Boolean = false,
    val isProcessingTx: Boolean = false,
    val lastTransaction: WxTransactionResponse? = null,
    val txHistory: List<WxTransactionResponse> = emptyList(),
    val orderBook: WxOrderBook? = null,
    val networkStatus: WxNetworkStatus = WxNetworkStatus(),
    val errorMessage: String? = null
)

class BridgeViewModel(
    private val wxService: WxNetworkService = WxNetworkService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BridgeUiState())
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    init {
        // Auto fetch initial exchange rate, orderbook, network status and connect default WX wallet
        refreshRate()
        fetchOrderBook()
        checkNetworkStatus()
        connectWallet()
    }

    fun setSlippage(slippage: Double) {
        _uiState.value = _uiState.value.copy(slippagePercent = slippage)
    }

    fun fetchOrderBook() {
        viewModelScope.launch {
            val ob = wxService.fetchOrderBook(_uiState.value.liveRate)
            _uiState.value = _uiState.value.copy(orderBook = ob)
        }
    }

    fun checkNetworkStatus() {
        viewModelScope.launch {
            val status = wxService.getNetworkStatus()
            _uiState.value = _uiState.value.copy(networkStatus = status)
        }
    }

    fun connectWallet(customAddress: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnectingWallet = true, errorMessage = null)
            val result = wxService.connectWallet(customAddress)
            result.onSuccess { wallet ->
                _uiState.value = _uiState.value.copy(
                    wallet = wallet,
                    isConnectingWallet = false
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isConnectingWallet = false,
                    errorMessage = err.localizedMessage ?: "Failed to connect WX Wallet"
                )
            }
        }
    }

    fun disconnectWallet() {
        wxService.disconnectWallet()
        _uiState.value = _uiState.value.copy(wallet = null)
    }

    fun refreshRate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRate = true)
            val result = wxService.fetchLiveExchangeRate()
            result.onSuccess { rateObj ->
                _uiState.value = _uiState.value.copy(
                    liveRate = rateObj.rate,
                    rateChange24h = rateObj.change24h,
                    isLoadingRate = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoadingRate = false)
            }
        }
    }

    fun executeTransaction(
        rubToUsdt: Boolean,
        amount: Double,
        expectedResult: Double,
        onSuccess: (WxTransactionResponse) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingTx = true, errorMessage = null)

            val wallet = _uiState.value.wallet
            if (wallet == null) {
                _uiState.value = _uiState.value.copy(
                    isProcessingTx = false,
                    errorMessage = "Пожалуйста, подключите WX кошелек"
                )
                return@launch
            }

            val fromAsset = if (rubToUsdt) "RUB" else "USDT"
            val toAsset = if (rubToUsdt) "USDT" else "RUB"

            val req = WxTransactionRequest(
                senderAddress = wallet.address,
                recipientAddress = "3P8xWXNetworkBridgeVault092312",
                amount = amount,
                fromAsset = fromAsset,
                toAsset = toAsset,
                expectedOutput = expectedResult,
                rate = _uiState.value.liveRate,
                slippagePercent = _uiState.value.slippagePercent
            )

            val res = wxService.initiateTransaction(req)
            res.onSuccess { tx ->
                val updatedWallet = wxService.getActiveWallet()
                _uiState.value = _uiState.value.copy(
                    isProcessingTx = false,
                    wallet = updatedWallet,
                    lastTransaction = tx,
                    txHistory = wxService.getHistory()
                )
                onSuccess(tx)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isProcessingTx = false,
                    errorMessage = err.localizedMessage ?: "Ошибка выполнения транзакции WX Network"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
