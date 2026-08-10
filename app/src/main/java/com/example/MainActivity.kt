package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.service.WxTransactionResponse
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryDarkBlue
import com.example.ui.theme.PrimaryMediumBlue
import com.example.ui.theme.RubBridgeTheme
import com.example.ui.theme.TextDark
import com.example.viewmodel.BridgeViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      RubBridgeTheme {
        val navController = rememberNavController()
        val viewModel: BridgeViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        NavHost(navController = navController, startDestination = "home") {
          composable("home") {
            HomePage(navController = navController, viewModel = viewModel)
          }
          composable(
            "confirmation/{rubToUsdt}/{amount}/{result}",
            arguments = listOf(
              navArgument("rubToUsdt") { type = NavType.BoolType },
              navArgument("amount") { type = NavType.FloatType },
              navArgument("result") { type = NavType.FloatType }
            )
          ) { backStackEntry ->
            val rubToUsdt = backStackEntry.arguments?.getBoolean("rubToUsdt") ?: true
            val amount = backStackEntry.arguments?.getFloat("amount") ?: 0f
            val result = backStackEntry.arguments?.getFloat("result") ?: 0f
            ConfirmationPage(
              navController = navController,
              viewModel = viewModel,
              rubToUsdt = rubToUsdt,
              amount = amount,
              result = result
            )
          }
          composable("success") {
            SuccessPage(navController = navController, viewModel = viewModel)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(navController: NavController, viewModel: BridgeViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var rubToUsdt by remember { mutableStateOf(true) }
  var amountText by remember { mutableStateOf("") }
  var showWalletSheet by remember { mutableStateOf(false) }
  var showHistorySheet by remember { mutableStateOf(false) }

  val rate = uiState.liveRate
  val amount = amountText.replace(",", ".").toDoubleOrNull() ?: 0.0
  val result = if (rubToUsdt) amount / rate else amount * rate

  val activeWallet = uiState.wallet
  val selectedAsset = if (rubToUsdt) "RUB" else "USDT"
  val availableBalance = activeWallet?.balances?.get(selectedAsset) ?: 0.0

  val context = LocalContext.current

  // Show error dialog if any
  uiState.errorMessage?.let { msg ->
    AlertDialog(
      onDismissRequest = { viewModel.clearError() },
      title = { Text("Ошибка WX Network", fontWeight = FontWeight.Bold) },
      text = { Text(msg) },
      confirmButton = {
        TextButton(onClick = { viewModel.clearError() }) {
          Text("ОК")
        }
      }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { 
          Text(
            "RUB BRIDGE", 
            fontWeight = FontWeight.ExtraBold,
            color = TextDark
          ) 
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        ),
        actions = {
          // Wallet Status Button
          Surface(
            onClick = { showWalletSheet = true },
            shape = RoundedCornerShape(20.dp),
            color = if (activeWallet != null) Color(0xFFE3F2FD) else Color(0xFFFFEBEE),
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .background(
                    if (activeWallet != null) Color(0xFF4CAF50) else Color(0xFFF44336),
                    CircleShape
                  )
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (activeWallet != null) 
                  "${activeWallet.address.take(4)}...${activeWallet.address.takeLast(4)}" 
                else "WX Wallet",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
              )
            }
          }

          IconButton(onClick = { showHistorySheet = true }) {
            Icon(Icons.Default.History, contentDescription = "History", tint = TextDark)
          }
        }
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        "Мост между фиатом\nи цифровыми активами",
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextDark,
        lineHeight = 32.sp
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        "Прямой обмен через WX Network Smart Contracts",
        fontSize = 14.sp,
        color = Color.Gray
      )
      Spacer(modifier = Modifier.height(18.dp))

      // WX Network Wallet Status Banner / Connect Wallet Button
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (activeWallet != null) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .background(
                  if (activeWallet != null) Color(0xFF2E7D32) else PrimaryBlue,
                  CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                if (activeWallet != null) Icons.Default.AccountBalanceWallet else Icons.Default.Link,
                contentDescription = "Wallet",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                if (activeWallet != null) "WX Wallet Подключен" else "WX Network Wallet",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextDark
              )
              Text(
                if (activeWallet != null)
                  "${activeWallet.address.take(6)}...${activeWallet.address.takeLast(6)}"
                else
                  "Нажмите для авторизации",
                fontSize = 12.sp,
                color = Color.Gray
              )
            }
          }

          if (activeWallet != null) {
            TextButton(onClick = { showWalletSheet = true }) {
              Text("Инфо", fontWeight = FontWeight.Bold, color = PrimaryBlue)
            }
          } else {
            Button(
              onClick = { viewModel.connectWallet() },
              enabled = !uiState.isConnectingWallet,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
              if (uiState.isConnectingWallet) {
                CircularProgressIndicator(
                  color = Color.White,
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp
                )
              } else {
                Text("Connect Wallet", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Direction selector
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color.White, RoundedCornerShape(16.dp))
          .padding(4.dp)
      ) {
        DirectionButton(
          title = "RUB → USDT",
          selected = rubToUsdt,
          onClick = {
            rubToUsdt = true
            amountText = ""
          },
          modifier = Modifier.weight(1f)
        )
        DirectionButton(
          title = "USDT → RUB",
          selected = !rubToUsdt,
          onClick = {
            rubToUsdt = false
            amountText = ""
          },
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Main Card
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(22.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Вы отправляете", color = Color.Gray, fontSize = 14.sp)
            if (activeWallet != null) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                  amountText = String.format(Locale.US, "%.2f", availableBalance)
                }
              ) {
                Text(
                  "Доступно: ${String.format(Locale.US, "%.2f", availableBalance)} $selectedAsset",
                  fontSize = 12.sp,
                  color = PrimaryBlue,
                  fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = PrimaryBlue.copy(alpha = 0.1f)
                ) {
                  Text(
                    "МАКС",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("0.00") },
            trailingIcon = {
              Text(
                if (rubToUsdt) "RUB" else "USDT",
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(end = 16.dp)
              )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.background,
              unfocusedContainerColor = MaterialTheme.colorScheme.background,
              focusedBorderColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent,
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold)
          )

          Spacer(modifier = Modifier.height(24.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = PrimaryBlue)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Вы получите", color = Color.Gray, fontSize = 14.sp)
          }
          Spacer(modifier = Modifier.height(8.dp))
          
          val formattedResult = String.format(Locale.US, "%.2f", result)
          val resultCurrency = if (rubToUsdt) "USDT" else "RUB"
          Text(
            "$formattedResult $resultCurrency",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextDark
          )

          Spacer(modifier = Modifier.height(16.dp))
          
          // Live WX Rate Banner
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Курс WX Network", color = Color.Gray, fontSize = 13.sp)
              Spacer(modifier = Modifier.width(6.dp))
              if (uiState.isLoadingRate) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
              } else {
                IconButton(
                  onClick = { 
                    viewModel.refreshRate() 
                    viewModel.fetchOrderBook()
                    viewModel.checkNetworkStatus()
                  },
                  modifier = Modifier.size(20.dp)
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
              }
            }
            Text(
              "1 USDT = ${String.format(Locale.US, "%.2f", rate)} RUB", 
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Slippage Tolerance Selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Проскальзывание (Slippage):", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(0.1, 0.5, 1.0).forEach { slip ->
                val isSel = uiState.slippagePercent == slip
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isSel) PrimaryBlue else Color(0xFFE8EAF6),
                  modifier = Modifier.clickable { viewModel.setSlippage(slip) }
                ) {
                  Text(
                    "$slip%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) Color.White else TextDark,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(20.dp))
          
          Button(
            onClick = {
              if (activeWallet == null) {
                showWalletSheet = true
              } else if (amount > availableBalance) {
                Toast.makeText(context, "Недостаточно средств на WX кошельке", Toast.LENGTH_SHORT).show()
              } else {
                navController.navigate("confirmation/$rubToUsdt/$amount/$result")
              }
            },
            enabled = amount > 0 || activeWallet == null,
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            Text(
              if (activeWallet == null) "ПОДКЛЮЧИТЬ WX КОШЕЛЕК" else "ПРОДОЛЖИТЬ", 
              fontWeight = FontWeight.Bold, 
              color = Color.White
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(30.dp))
      Text("Как это работает", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
      Spacer(modifier = Modifier.height(14.dp))

      StepItem("1", "Выберите направление", "RUB → USDT или USDT → RUB")
      StepItem("2", "Укажите сумму", "Расчет по курсу WX Network Matcher")
      StepItem("3", "Подтвердите операцию", "Авторизуйте транзакцию на WX Network")
      StepItem("4", "Получите актив", "Зачисление USDT/RUB на ваш WX кошелек")
      
      Spacer(modifier = Modifier.height(20.dp))
      
      // Bridge Diagram Card
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(Brush.horizontalGradient(listOf(PrimaryDarkBlue, PrimaryMediumBlue)))
          .padding(20.dp)
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("RUB BRIDGE", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Powered by wx.network API", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
          Spacer(modifier = Modifier.height(18.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            BridgeIconItem(Icons.Default.AccountBalance, "FIAT")
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            BridgeIconItem(Icons.Default.CurrencyRuble, "RUB")
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            BridgeIconItem(Icons.Default.WifiTethering, "WX DEX")
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            BridgeIconItem(Icons.Default.Toll, "USDT")
          }
        }
      }
      
      Spacer(modifier = Modifier.height(40.dp))
    }
  }

  // WX Wallet Modal Sheet
  if (showWalletSheet) {
    ModalBottomSheet(onDismissRequest = { showWalletSheet = false }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
      ) {
        Text("WX Network Wallet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(modifier = Modifier.height(16.dp))

        if (activeWallet != null) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text("Подключенный адрес:", fontSize = 12.sp, color = Color.Gray)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                activeWallet.address, 
                fontWeight = FontWeight.Bold, 
                fontSize = 14.sp, 
                color = TextDark
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text("Балансы WX Network:", fontSize = 12.sp, color = Color.Gray)
              Spacer(modifier = Modifier.height(6.dp))
              activeWallet.balances.forEach { (asset, bal) ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(asset, fontWeight = FontWeight.Medium)
                  Text("${String.format(Locale.US, "%.2f", bal)} $asset", fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(20.dp))
          OutlinedButton(
            onClick = {
              viewModel.disconnectWallet()
              showWalletSheet = false
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          ) {
            Text("Отключить кошелек", color = Color.Red)
          }
        } else {
          var customAddressInput by remember { mutableStateOf("") }
          Text(
            "Подключите WX кошелек для прямых транзакций в блокчейне Waves/WX.",
            fontSize = 14.sp,
            color = Color.Gray
          )
          Spacer(modifier = Modifier.height(14.dp))
          OutlinedTextField(
            value = customAddressInput,
            onValueChange = { customAddressInput = it },
            placeholder = { Text("Ввести WX / Waves адрес (3P...)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.background,
              unfocusedContainerColor = MaterialTheme.colorScheme.background
            )
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = {
              viewModel.connectWallet(customAddressInput.ifBlank { null })
              showWalletSheet = false
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            if (uiState.isConnectingWallet) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
              Text(
                if (customAddressInput.isNotBlank()) "Подключить данный адрес" else "Авторизовать WX Account", 
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(30.dp))
      }
    }
  }

  // WX Tx History Sheet
  if (showHistorySheet) {
    ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
      ) {
        Text("История транзакций WX Network", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(modifier = Modifier.height(16.dp))

        val history = uiState.txHistory
        if (history.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("У вас пока нет проведенных транзакций", color = Color.Gray)
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 350.dp)
          ) {
            items(history) { tx ->
              TxHistoryItem(tx)
              HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
            }
          }
        }
        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }
}

@Composable
fun TxHistoryItem(tx: WxTransactionResponse) {
  val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
  val formattedDate = dateFormat.format(Date(tx.timestamp))

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        "${String.format(Locale.US, "%.2f", tx.amountIn)} ${tx.assetIn} → ${String.format(Locale.US, "%.2f", tx.amountOut)} ${tx.assetOut}",
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = TextDark
      )
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE8F5E9)
      ) {
        Text(
          "CONFIRMED",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF2E7D32),
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text("Tx ID: ${tx.id.take(8)}...${tx.id.takeLast(8)}", fontSize = 12.sp, color = Color.Gray)
    Text(formattedDate, fontSize = 11.sp, color = Color.Gray)
  }
}

@Composable
fun DirectionButton(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (selected) PrimaryBlue else Color.Transparent)
      .clickable { onClick() }
      .padding(vertical = 15.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      title,
      color = if (selected) Color.White else TextDark,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun StepItem(number: String, title: String, description: String) {
  Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .background(PrimaryBlue, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text(number, color = Color.White, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(title, fontWeight = FontWeight.Bold, color = TextDark)
      Text(description, color = Color.Gray, fontSize = 13.sp)
    }
  }
}

@Composable
fun BridgeIconItem(icon: ImageVector, title: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    Spacer(modifier = Modifier.height(4.dp))
    Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationPage(
  navController: NavController,
  viewModel: BridgeViewModel,
  rubToUsdt: Boolean,
  amount: Float,
  result: Float
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val from = if (rubToUsdt) "RUB" else "USDT"
  val to = if (rubToUsdt) "USDT" else "RUB"
  val wallet = uiState.wallet

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Подтверждение WX", fontWeight = FontWeight.Bold, color = TextDark) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
          IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextDark)
          }
        }
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(20.dp)
    ) {
      Spacer(modifier = Modifier.height(20.dp))
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(25.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(60.dp))
          Spacer(modifier = Modifier.height(16.dp))
          Text("$from → $to", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
          Spacer(modifier = Modifier.height(24.dp))
          
          ConfirmationRow("Вы отправляете", "${String.format(Locale.US, "%.2f", amount)} $from")
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
          ConfirmationRow("Вы получите", "${String.format(Locale.US, "%.2f", result)} $to")
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
          ConfirmationRow("Отправитель (WX)", wallet?.address?.take(12) + "...")
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
          ConfirmationRow("Сеть / DEX", "WX Network Node")
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
          ConfirmationRow("Допустимый сдвиг (Slippage)", "${uiState.slippagePercent}%")
          HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))
          ConfirmationRow("Комиссия сети", "0.005 WAVES (~0.01$)")
          
          Spacer(modifier = Modifier.height(30.dp))

          Button(
            onClick = {
              viewModel.executeTransaction(
                rubToUsdt = rubToUsdt,
                amount = amount.toDouble(),
                expectedResult = result.toDouble(),
                onSuccess = {
                  navController.navigate("success") {
                    popUpTo("home")
                  }
                }
              )
            },
            enabled = !uiState.isProcessingTx,
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
          ) {
            if (uiState.isProcessingTx) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Отправка в WX Network...", fontWeight = FontWeight.Bold)
              }
            } else {
              Text("ПОДТВЕРДИТЬ ТРАНЗАКЦИЮ", fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }
    }
  }
}

@Composable
fun ConfirmationRow(title: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(title, color = Color.Gray, fontSize = 14.sp)
    Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
fun SuccessPage(navController: NavController, viewModel: BridgeViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val lastTx = uiState.lastTransaction
  val context = LocalContext.current

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(25.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF4CAF50), modifier = Modifier.size(80.dp))
      Spacer(modifier = Modifier.height(16.dp))
      Text("Успешно!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
      Spacer(modifier = Modifier.height(8.dp))
      Text("Транзакция успешно подтверждена WX Network", color = Color.Gray, textAlign = TextAlign.Center)

      if (lastTx != null) {
        Spacer(modifier = Modifier.height(24.dp))
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("Детали операции:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Spacer(modifier = Modifier.height(8.dp))
            ConfirmationRow(
              "Обменяли", 
              "${String.format(Locale.US, "%.2f", lastTx.amountIn)} ${lastTx.assetIn} → ${String.format(Locale.US, "%.2f", lastTx.amountOut)} ${lastTx.assetOut}"
            )
            Spacer(modifier = Modifier.height(6.dp))
            ConfirmationRow("Блокчейн Высота", "#${lastTx.blockHeight}")
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Hash ID", color = Color.Gray, fontSize = 14.sp)
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  "${lastTx.id.take(8)}...${lastTx.id.takeLast(6)}", 
                  fontWeight = FontWeight.Bold, 
                  fontSize = 14.sp, 
                  color = PrimaryBlue
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("WX Tx Hash", lastTx.id)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Tx Hash скопирован!", Toast.LENGTH_SHORT).show()
                  },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
      
      Spacer(modifier = Modifier.height(36.dp))
      Button(
        onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
      ) {
        Text("НА ГЛАВНУЮ", fontWeight = FontWeight.Bold, color = Color.White)
      }
    }
  }
}

