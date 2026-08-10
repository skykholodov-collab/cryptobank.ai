import re

with open('app/src/main/java/com/example/service/WxNetworkService.kt', 'r') as f:
    content = f.read()

# Replace connectWallet
connect_wallet_pattern = r"suspend fun connectWallet\(customAddress: String\? = null\): Result<WxWallet> = withContext\(Dispatchers\.IO\) \{.*?\n    \}"
connect_wallet_replacement = """suspend fun connectWallet(customAddress: String? = null): Result<WxWallet> = withContext(Dispatchers.IO) {
        try {
            val address = customAddress?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(Exception("Пожалуйста, введите адрес кошелька WX Network"))

            // Verify address and fetch WAVES balance
            val request = Request.Builder()
                .url("$wxNodeApiUrl/addresses/balance/$address")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Ошибка сети или неверный адрес"))
                }
                
                val json = JSONObject(body)
                if (json.has("error")) {
                    return@withContext Result.failure(Exception("Неверный адрес WX Network: " + json.optString("message")))
                }
                
                val wavesBalance = json.optLong("balance", 0L) / 100_000_000.0

                // Also fetch assets to find USDT and RUB (or similar)
                val assetBalances = fetchBalancesFromNode(address)
                
                val balancesMap = mutableMapOf<String, Double>()
                balancesMap["WAVES"] = wavesBalance
                
                // Map asset balances
                for (asset in assetBalances) {
                    if (asset.assetId == "34N9YcEETLWn93qYQ64EsP1x89tSruJU44RrEMSXXEPJ" || asset.symbol.contains("USDT")) {
                        balancesMap["USDT"] = asset.amount
                    }
                    if (asset.symbol.contains("RUB")) {
                        balancesMap["RUB"] = asset.amount
                    }
                }
                
                if (!balancesMap.containsKey("USDT")) balancesMap["USDT"] = 0.0
                if (!balancesMap.containsKey("RUB")) balancesMap["RUB"] = 0.0

                val wallet = WxWallet(
                    address = address,
                    publicKey = "",
                    isConnected = true,
                    balances = balancesMap
                )

                activeWallet = wallet
                Result.success(wallet)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchBalancesFromNode(address: String): List<WxAssetBalance> {
        val resultBalances = mutableListOf<WxAssetBalance>()
        try {
            val assetsReq = Request.Builder().url("$wxNodeApiUrl/assets/balance/$address").get().build()
            client.newCall(assetsReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val balancesArray = json.optJSONArray("balances")
                        if (balancesArray != null) {
                            for (i in 0 until balancesArray.length()) {
                                val item = balancesArray.getJSONObject(i)
                                val balance = item.optLong("balance", 0L)
                                val issueTx = item.optJSONObject("issueTransaction")
                                val assetId = item.optString("assetId", "")
                                
                                if (issueTx != null) {
                                    val name = issueTx.optString("name", "Unknown")
                                    val decimals = issueTx.optInt("decimals", 8)
                                    val symbol = if (name.length > 6) name.take(6).uppercase() else name.uppercase()
                                    
                                    val actualBalance = balance / Math.pow(10.0, decimals.toDouble())
                                    resultBalances.add(WxAssetBalance(assetId, name, symbol, actualBalance, decimals))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultBalances
    }"""

content = re.sub(connect_wallet_pattern, connect_wallet_replacement, content, flags=re.DOTALL)

fetch_balances_pattern = r"suspend fun fetchBalances\(address: String\): Result<List<WxAssetBalance>> = withContext\(Dispatchers\.IO\) \{.*?\n    \}"
fetch_balances_replacement = """suspend fun fetchBalances(address: String): Result<List<WxAssetBalance>> = withContext(Dispatchers.IO) {
        try {
            val addressToQuery = address.ifBlank { activeWallet?.address ?: return@withContext Result.failure(Exception("No address")) }
            
            val resultBalances = mutableListOf<WxAssetBalance>()
            
            // 1. Fetch WAVES balance
            val wavesReq = Request.Builder().url("$wxNodeApiUrl/addresses/balance/$addressToQuery").get().build()
            client.newCall(wavesReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val wavesBalance = json.optLong("balance", 0L) / 100_000_000.0
                        resultBalances.add(WxAssetBalance("WAVES", "Waves Token", "WAVES", wavesBalance, 8))
                    }
                }
            }

            // 2. Fetch Assets
            resultBalances.addAll(fetchBalancesFromNode(addressToQuery))
            
            Result.success(resultBalances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }"""
content = re.sub(fetch_balances_pattern, fetch_balances_replacement, content, flags=re.DOTALL)

fetch_live_exchange_pattern = r"suspend fun fetchLiveExchangeRate\(\): Result<WxExchangeRate> = withContext\(Dispatchers\.IO\) \{.*?\n    \}"
fetch_live_exchange_replacement = """suspend fun fetchLiveExchangeRate(): Result<WxExchangeRate> = withContext(Dispatchers.IO) {
        try {
            // Attempt to reach Binance API for real USDTRUB exchange rate
            val request = Request.Builder()
                .url("https://api.binance.com/api/v3/ticker/24hr?symbol=USDTRUB")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val lastPrice = json.optDouble("lastPrice", 0.0)
                    val priceChangePercent = json.optDouble("priceChangePercent", 0.0)
                    val volume = json.optDouble("volume", 0.0)
                    
                    if (lastPrice > 0) {
                        return@withContext Result.success(
                            WxExchangeRate(
                                pair = "RUB/USDT",
                                rate = lastPrice,
                                change24h = priceChangePercent,
                                volume24h = volume,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
            Result.failure(Exception("Failed to fetch exchange rate"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }"""
content = re.sub(fetch_live_exchange_pattern, fetch_live_exchange_replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/service/WxNetworkService.kt', 'w') as f:
    f.write(content)

print("Patched WxNetworkService.kt")
