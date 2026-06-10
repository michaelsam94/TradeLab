# TradeLab — Implementation Roadmap

Crypto paper-trading simulator. Kotlin 2.x · Jetpack Compose · Material 3 · Hilt · Room · Retrofit/OkHttp · WorkManager · DataStore. minSdk 26 / targetSdk 35. Package `com.michael.tradelab`.

## Phases

- [x] **P0 — Project scaffold**: Gradle (version catalog, AGP, Kotlin 2.x, Compose BOM), manifest (INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS only), network security config, Hilt app, MainActivity with edge-to-edge.
- [x] **P1 — Terminal Noir design system**: Color.kt, Type.kt (JetBrains Mono + Inter), Shape.kt, Spacing.kt, dark-first themes, shared components (cards, buttons, banners, skeletons, empty states).
- [x] **P2 — Navigation + F7 Disclaimer Gate**: single-activity Navigation Compose, feature graphs, bottom nav (Markets · Trade · Indicators · History · Learn), `tradelab://` deep links, un-skippable scroll-to-accept disclaimer gate backed by DataStore version flag.
- [x] **P3 — Data layer**: Room DB (pairs, tickers, candles, wallet, positions, orders, alerts, indicator_readouts, entitlements), Binance REST (exchangeInfo, klines) + `!miniTicker@arr` WebSocket (lifecycle-bound), repositories, AppError model, offline-first status chip.
- [x] **P4 — F1 Live Markets Watchlist**: ticker list with live prices/24h change, favorites, quote-asset LazyRow filter chips, top movers carousel, pair search.
- [x] **P5 — F4 Candlestick Charts**: Canvas OHLC chart, pinch-zoom/pan, timeframe LazyRow, EMA/RSI/MACD overlays, crosshair with a11y OHLC semantics.
- [x] **P6 — F2 Virtual Paper Trading**: $10k virtual wallet, market/limit order ticket (buy/sell segmented control), tick-driven limit fills, portfolio with live unrealized P/L, reset flow.
- [x] **P7 — F3 Indicator Feed**: RSI/MACD/MA/momentum computation (Dispatchers.Default), ranked feed with bias chips + confidence meter, methodology sheet, backtest screen, permanent non-dismissible warning banner, banned-vocabulary unit test.
- [x] **P8 — F5 History & Analytics**: filled-order history, equity curve, realized P/L, win rate, max drawdown — all "Simulated" labelled.
- [x] **P9 — F6 Alerts**: price/indicator alerts CRUD, foreground tick evaluation + WorkManager 15-min worker, POST_NOTIFICATIONS rationale flow, factual notification copy, deep links to chart.
- [x] **P10 — F8 Pro Unlocks**: Play Billing 7 paywall, entitlement cache, restore purchases (digital goods only).
- [x] **P11 — Learn hub + Settings/Legal**: lesson carousel, settings (theme, legal, erase all data, disclaimer re-view).
- [x] **P12 — Tests + polish**: indicator math fixtures, order use-case unit tests, banned-vocab lint test, disclaimer gate UI test, final lint/build.

Each phase ends with a green `assembleDebug`.
