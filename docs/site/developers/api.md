---
title: API
description: Read listings and median prices, add a currency, and react to auction events from another plugin.
sidebar:
  order: 1
---

Depend on `net.artisanmc:artisan-auctions-api` (pure Java) and add `ArtisanAuctions` to `depend` in your
`plugin.yml`.

## Read a median price

```java
AuctionsService auctions = Bukkit.getServicesManager().load(AuctionsService.class);
auctions.medianPrice(stack, "vault").ifPresent(median -> ...);
```

## Add a currency

Register an `AuctionCurrency` (id, balance, withdraw, deposit, in `BigDecimal`) in Bukkit's
`ServicesManager`. Its id can then be listed in `currencies.allowed`.

## Events

Every action fires a cancellable event first, on the main thread, before any money or item moves.
`deny(Component)` cancels it with a message for the player.

| Before (cancellable) | Can change |
|---|---|
| `AuctionPreListEvent` | price, opening bid, duration; gives the known median |
| `AuctionPrePurchaseEvent` | |
| `AuctionPreBidEvent` | |
| `AuctionPreCancelEvent` | |

Outcomes: `AuctionListedEvent`, `AuctionSoldEvent`, `AuctionBidPlacedEvent`, `AuctionOutbidEvent`,
`AuctionExpiredEvent`, `AuctionCancelledEvent`, `AuctionClaimedEvent`.

## Menus

Artisan menus can read the data sources `auctions:listings` (params `tab`, `sort`, `search`),
`auctions:my_listings`, `auctions:claims`, `auctions:lot`, `auctions:summary`, `auctions:price` and
`auctions:settings`, and call the actions `auctions:sell_start`, `sell`, `buy`, `bid`, `cancel`, `claim`.
