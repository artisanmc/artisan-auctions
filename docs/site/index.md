---
title: Artisan Auctions
description: An auction house for Paper and Spigot servers, with fixed-price sales, timed auctions, a claims box and median prices.
---

Artisan Auctions adds an auction house to your server: players sell at a fixed price, run timed
auctions, or both, from `/ah`. Its menus, dialogs and texts are files you restyle in the
[Artisan](https://artisanmc.net/modules/artisan/) editor; the plugin handles the rules, the money and
the storage.

## What players get

- `/ah` opens the house: browse, search, buy, bid.
- *Sell* in the menu (or `/ah sell 250`) lists the item in hand.
- Every listing shows the item's median price, so a player sees at a glance whether a price is fair.
- Bought, expired and withdrawn items wait in a claims box (`/ah claim`) when they cannot be given
  directly. Money owed to an offline seller is paid when they join.

## Requirements

| | |
|---|---|
| Server | Paper or Spigot 1.21+. Folia is not supported. |
| Java | 21 |
| Artisan | 2.x with API 2.10.0 or later |
| Economy | [Vault](https://www.spigotmc.org/resources/vault.34315/) with an economy plugin, or a currency another plugin registers |

## Next

- [Install](getting-started/install/)
- [Settings](configuration/settings/): every option of the auction house
- [Move from zAuctionHouse](guides/move-from-zauctionhouse/)
