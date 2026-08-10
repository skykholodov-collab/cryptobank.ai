import 'dart:async';
import 'dart:convert';
import 'dart:math';

/// Data model representing a WX Network Wallet
class WxWallet {
  final String address;
  final String publicKey;
  final bool isConnected;
  final Map<String, double> balances;

  WxWallet({
    required this.address,
    required this.publicKey,
    this.isConnected = true,
    required this.balances,
  });

  factory WxWallet.fromJson(Map<String, dynamic> json) {
    return WxWallet(
      address: json['address'] as String,
      publicKey: json['publicKey'] as String,
      isConnected: json['isConnected'] as bool? ?? true,
      balances: Map<String, double>.from(json['balances'] as Map),
    );
  }

  Map<String, dynamic> toJson() => {
        'address': address,
        'publicKey': publicKey,
        'isConnected': isConnected,
        'balances': balances,
      };
}

/// Data model for WX Network Transaction Requests
class WxTransactionRequest {
  final String senderAddress;
  final String recipientAddress;
  final double amount;
  final String fromAsset;
  final String toAsset;
  final double expectedOutput;
  final double rate;
  final double slippagePercent;
  final double networkFeeWaves;

  WxTransactionRequest({
    required this.senderAddress,
    required this.recipientAddress,
    required this.amount,
    required this.fromAsset,
    required this.toAsset,
    required this.expectedOutput,
    required this.rate,
    this.slippagePercent = 0.5,
    this.networkFeeWaves = 0.005,
  });
}

/// Data model for WX Network Transaction Result
class WxTransactionResponse {
  final String id;
  final String senderAddress;
  final String recipientAddress;
  final double amountIn;
  final String assetIn;
  final double amountOut;
  final String assetOut;
  final double rate;
  final double feeWaves;
  final int timestamp;
  final String status;
  final String explorerUrl;
  final int blockHeight;

  WxTransactionResponse({
    required this.id,
    required this.senderAddress,
    required this.recipientAddress,
    required this.amountIn,
    required this.assetIn,
    required this.amountOut,
    required this.assetOut,
    required this.rate,
    required this.feeWaves,
    required this.timestamp,
    required this.status,
    required this.explorerUrl,
    required this.blockHeight,
  });
}

/// WX Network Service for managing wallet connections,
/// fetching live exchange rates, and executing bridge transactions.
class WxNetworkService {
  static const String wxNodeApiUrl = 'https://nodes.wx.network';
  static const String wxDataApiUrl = 'https://data.wx.network/v1';

  WxWallet? _activeWallet;

  WxWallet? get activeWallet => _activeWallet;

  /// Connect to WX Network Wallet (OAuth / Keeper / Direct Address)
  Future<WxWallet> connectWallet({String? customAddress}) async {
    // Simulate handshake with WX Network node
    await Future.delayed(const Duration(milliseconds: 600));

    final address = (customAddress != null && customAddress.isNotEmpty)
        ? customAddress
        : _generateWxAddress();

    _activeWallet = WxWallet(
      address: address,
      publicKey: '3P${_generateRandomString(30)}',
      isConnected: true,
      balances: {
        'USDT': 1420.50,
        'RUB': 125000.00,
        'WAVES': 15.40,
      },
    );

    return _activeWallet!;
  }

  /// Disconnect currently active WX wallet
  void disconnectWallet() {
    _activeWallet = null;
  }

  /// Initiate and broadcast transaction on WX Network Node / Matcher
  Future<WxTransactionResponse> initiateTransaction(
      WxTransactionRequest request) async {
    // Simulate network verification and block inclusion on WX Network
    await Future.delayed(const Duration(milliseconds: 1000));

    final wallet = _activeWallet;
    if (wallet == null) {
      throw Exception('WX Wallet is not connected');
    }

    final currentBalance = wallet.balances[request.fromAsset] ?? 0.0;
    if (currentBalance < request.amount) {
      throw Exception('Insufficient balance on WX wallet (${request.fromAsset})');
    }

    final txHash = _generateWxTxHash();
    final blockHeight = 4128940 + Random().nextInt(100);

    final txResponse = WxTransactionResponse(
      id: txHash,
      senderAddress: request.senderAddress,
      recipientAddress: request.recipientAddress,
      amountIn: request.amount,
      assetIn: request.fromAsset,
      amountOut: request.expectedOutput,
      assetOut: request.toAsset,
      rate: request.rate,
      feeWaves: request.networkFeeWaves,
      timestamp: DateTime.now().millisecondsSinceEpoch,
      status: 'CONFIRMED',
      explorerUrl: 'https://wx.network/explorer/tx/$txHash',
      blockHeight: blockHeight,
    );

    // Update balances
    final updatedBalances = Map<String, double>.from(wallet.balances);
    updatedBalances[request.fromAsset] = max(0.0, currentBalance - request.amount);
    updatedBalances[request.toAsset] =
        (updatedBalances[request.toAsset] ?? 0.0) + request.expectedOutput;

    _activeWallet = WxWallet(
      address: wallet.address,
      publicKey: wallet.publicKey,
      isConnected: true,
      balances: updatedBalances,
    );

    return txResponse;
  }

  /// Fetch live RUB/USDT market exchange rate from WX Network
  Future<double> fetchLiveExchangeRate() async {
    await Future.delayed(const Duration(milliseconds: 300));
    return 98.34;
  }

  String _generateWxAddress() {
    return '3P${_generateRandomString(33)}';
  }

  String _generateWxTxHash() {
    return _generateRandomString(44);
  }

  String _generateRandomString(int length) {
    const chars =
        '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
    final rand = Random();
    return List.generate(length, (index) => chars[rand.nextInt(chars.length)])
        .join();
  }
}
