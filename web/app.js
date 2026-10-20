(function () {
  "use strict";

  var RATE = 98.34;
  var DEMO_ADDRESS = "3P5Kq2wR7tN4mB8xL1cV6yH9dF3sJ5aQ2eZ9XyZ";
  var DEMO_BALANCES = { RUB: 18450, USDT: 520, WAVES: 12.45 };

  var state = {
    direction: "rub-usdt",
    amount: "",
    slippage: 0.5,
    rate: RATE,
    connected: true,
    address: DEMO_ADDRESS,
    balances: Object.assign({}, DEMO_BALANCES),
    view: "home",
    history: [],
    lastTx: null
  };

  function $(id) { return document.getElementById(id); }
  function all(selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); }

  function parseAmount(value) {
    var normalized = String(value).replace(/\s/g, "").replace(",", ".");
    var parsed = parseFloat(normalized);
    return isFinite(parsed) && parsed > 0 ? parsed : 0;
  }

  function formatMoney(value, decimals) {
    var places = typeof decimals === "number" ? decimals : 2;
    var fixed = Number(value || 0).toFixed(places);
    var parts = fixed.split(".");
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, " ");
    return parts.join(".");
  }

  function round2(value) { return Math.round(value * 100) / 100; }

  function shortAddress(address) {
    if (!address) return "Отключен";
    return address.slice(0, 4) + "..." + address.slice(-4);
  }

  function fromAsset() { return state.direction === "rub-usdt" ? "RUB" : "USDT"; }
  function toAsset() { return state.direction === "rub-usdt" ? "USDT" : "RUB"; }

  function resultValue() {
    var amount = parseAmount(state.amount);
    return state.direction === "rub-usdt" ? amount / state.rate : amount * state.rate;
  }

  function availableBalance() {
    if (!state.connected) return 0;
    return state.balances[fromAsset()] || 0;
  }

  function toast(message) {
    var node = $("toast");
    node.textContent = message;
    node.classList.remove("is-hidden");
    clearTimeout(toast.timer);
    toast.timer = setTimeout(function () { node.classList.add("is-hidden"); }, 2600);
  }

  function render() {
    var connected = state.connected;
    var amount = parseAmount(state.amount);
    var result = resultValue();

    $("walletChip").classList.toggle("off", !connected);
    $("walletChipLabel").textContent = connected ? shortAddress(state.address) : "Отключен";

    $("walletBanner").classList.toggle("off", !connected);
    $("walletBannerTitle").textContent = connected ? "WX Wallet подключен" : "WX Network Wallet";
    $("walletBannerSub").textContent = connected ? shortAddress(state.address) : "Нажмите для авторизации";
    $("walletBannerAction").textContent = connected ? "Инфо" : "Connect Wallet";

    $("available").textContent = formatMoney(availableBalance()) + " " + fromAsset();
    $("fromCurrency").textContent = fromAsset();
    $("toCurrency").textContent = toAsset();
    $("resultValue").textContent = formatMoney(result);
    $("rateLabel").textContent = "1 USDT = " + state.rate.toFixed(2) + " RUB";
    $("heroRate").textContent = state.rate.toFixed(2);

    var submit = $("submitBtn");
    submit.disabled = connected ? amount <= 0 : false;
    submit.textContent = connected ? "ПРОДОЛЖИТЬ" : "ПОДКЛЮЧИТЬ WX КОШЕЛЕК";

    all(".segment").forEach(function (button) {
      var active = button.getAttribute("data-direction") === state.direction;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
    });

    all(".slip").forEach(function (button) {
      button.classList.toggle("active", Number(button.getAttribute("data-slip")) === state.slippage);
    });

    renderWalletModal();
  }

  function renderWalletModal() {
    $("walletConnected").classList.toggle("is-hidden", !state.connected);
    $("walletDisconnected").classList.toggle("is-hidden", state.connected);
    var addressNode = $("walletConnected").querySelector(".address");
    if (addressNode) addressNode.textContent = state.address || DEMO_ADDRESS;
  }

  function showView(view) {
    state.view = view;
    $("homeView").classList.toggle("is-hidden", view !== "home");
    $("confirmView").classList.toggle("is-hidden", view !== "confirm");
    $("successView").classList.toggle("is-hidden", view !== "success");
    if (view !== "home") window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function openModal(id) {
    $(id).classList.remove("is-hidden");
    document.body.style.overflow = "hidden";
  }

  function closeModal(id) {
    $(id).classList.add("is-hidden");
    if (!all(".modal-backdrop").some(function (modal) { return !modal.classList.contains("is-hidden"); })) {
      document.body.style.overflow = "";
    }
  }

  function closeAllModals() {
    all(".modal-backdrop").forEach(function (modal) { modal.classList.add("is-hidden"); });
    document.body.style.overflow = "";
  }

  function openWallet() {
    renderWalletModal();
    openModal("walletModal");
  }

  function openHistory() {
    var list = $("historyList");
    list.innerHTML = "";
    if (state.history.length === 0) {
      $("historyEmpty").classList.remove("is-hidden");
      list.classList.add("is-hidden");
    } else {
      $("historyEmpty").classList.add("is-hidden");
      list.classList.remove("is-hidden");
      state.history.forEach(function (tx) {
        var item = document.createElement("div");
        item.className = "history-item";
        item.innerHTML =
          '<div class="history-top"><span>' + formatMoney(tx.amountIn) + " " + tx.assetIn +
          " → " + formatMoney(tx.amountOut) + " " + tx.assetOut +
          '</span><span class="status-pill">CONFIRMED</span></div>' +
          "<small>Tx ID: " + tx.id.slice(0, 8) + "..." + tx.id.slice(-8) + "</small>" +
          "<small>" + tx.date + "</small>";
        list.appendChild(item);
      });
    }
    openModal("historyModal");
  }

  function updateConfirm() {
    var amount = parseAmount(state.amount);
    var result = resultValue();
    $("confirmPair").textContent = fromAsset() + " → " + toAsset();
    $("confirmSend").textContent = formatMoney(amount) + " " + fromAsset();
    $("confirmReceive").textContent = formatMoney(result) + " " + toAsset();
    $("confirmSender").textContent = state.connected ? shortAddress(state.address) : "—";
    $("confirmSlip").textContent = state.slippage + "%";
  }

  function submit() {
    var amount = parseAmount(state.amount);
    if (!state.connected) {
      openWallet();
      return;
    }
    if (amount <= 0) return;
    if (amount > availableBalance() + 0.0000001) {
      toast("Недостаточно средств на WX кошельке");
      return;
    }
    updateConfirm();
    showView("confirm");
  }

  function makeId() {
    var chars = "0123456789abcdef";
    var id = "";
    for (var i = 0; i < 64; i++) id += chars[Math.floor(Math.random() * chars.length)];
    return id;
  }

  function confirmTransaction() {
    var button = $("confirmBtn");
    button.classList.add("busy");
    button.textContent = "ОТПРАВКА В WX NETWORK...";

    setTimeout(function () {
      var amount = parseAmount(state.amount);
      var result = round2(resultValue());
      var tx = {
        id: makeId(),
        amountIn: amount,
        amountOut: result,
        assetIn: fromAsset(),
        assetOut: toAsset(),
        blockHeight: 3842917 + Math.floor(Math.random() * 240),
        date: new Date().toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" })
      };
      state.lastTx = tx;
      state.history.unshift(tx);

      $("successExchange").textContent = formatMoney(tx.amountIn) + " " + tx.assetIn + " → " + formatMoney(tx.amountOut) + " " + tx.assetOut;
      $("successHeight").textContent = "#" + String(tx.blockHeight).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
      $("successHash").textContent = tx.id.slice(0, 8) + "..." + tx.id.slice(-6);

      button.classList.remove("busy");
      button.textContent = "ПОДТВЕРДИТЬ ТРАНЗАКЦИЮ";
      showView("success");
    }, 950);
  }

  function resetToHome() {
    state.amount = "";
    $("amountInput").value = "";
    showView("home");
    render();
  }

  all(".segment").forEach(function (button) {
    button.addEventListener("click", function () {
      state.direction = button.getAttribute("data-direction");
      state.amount = "";
      $("amountInput").value = "";
      render();
    });
  });

  all(".slip").forEach(function (button) {
    button.addEventListener("click", function () {
      state.slippage = Number(button.getAttribute("data-slip"));
      render();
    });
  });

  $("amountInput").addEventListener("input", function (event) {
    var cleaned = event.target.value.replace(/[^\d.,]/g, "");
    if (cleaned !== event.target.value) event.target.value = cleaned;
    state.amount = cleaned;
    render();
  });

  $("maxBtn").addEventListener("click", function () {
    if (!state.connected) {
      openWallet();
      return;
    }
    state.amount = String(availableBalance());
    $("amountInput").value = formatMoney(availableBalance());
    render();
  });

  $("submitBtn").addEventListener("click", submit);
  $("walletChip").addEventListener("click", openWallet);
  $("walletBannerAction").addEventListener("click", function () {
    if (state.connected) openWallet();
    else openWallet();
  });
  $("walletBanner").addEventListener("click", function (event) {
    if (event.target === $("walletBannerAction")) return;
    if (!state.connected) openWallet();
  });
  $("historyBtn").addEventListener("click", openHistory);
  $("backToHome").addEventListener("click", function () { showView("home"); });
  $("backHome").addEventListener("click", resetToHome);
  $("confirmBtn").addEventListener("click", confirmTransaction);

  $("disconnectBtn").addEventListener("click", function () {
    state.connected = false;
    state.address = "";
    closeAllModals();
    render();
    toast("WX кошелек отключен");
  });

  $("connectBtn").addEventListener("click", function () {
    var input = $("walletAddress");
    var address = input.value.trim();
    if (!address) {
      toast("Пожалуйста, введите адрес кошелька WX Network");
      input.focus();
      return;
    }
    state.connected = true;
    state.address = address;
    state.balances = Object.assign({}, DEMO_BALANCES);
    input.value = "";
    closeAllModals();
    render();
    toast("WX кошелек подключен");
  });

  $("walletAddress").addEventListener("keydown", function (event) {
    if (event.key === "Enter") $("connectBtn").click();
  });

  $("refreshRate").addEventListener("click", function () {
    var button = $("refreshRate");
    button.classList.add("spin");
    state.rate = Math.max(90, Math.min(110, round2(state.rate + (Math.random() - 0.5) * 0.12)));
    render();
    setTimeout(function () { button.classList.remove("spin"); }, 460);
  });

  $("copyHash").addEventListener("click", function () {
    if (!state.lastTx) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(state.lastTx.id).then(function () {
        toast("Tx Hash скопирован!");
      }).catch(function () { toast("Tx Hash: " + state.lastTx.id); });
    } else {
      toast("Tx Hash: " + state.lastTx.id);
    }
  });

  all(".close-modal").forEach(function (button) {
    button.addEventListener("click", function () {
      closeAllModals();
    });
  });

  all(".modal-backdrop").forEach(function (backdrop) {
    backdrop.addEventListener("click", function (event) {
      if (event.target === backdrop) closeAllModals();
    });
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") closeAllModals();
  });

  all('.nav a, .footer a').forEach(function (link) {
    link.addEventListener("click", function () {
      all(".nav a").forEach(function (item) { item.classList.remove("active"); });
      if (link.closest(".nav")) link.classList.add("active");
    });
  });

  render();
})();
