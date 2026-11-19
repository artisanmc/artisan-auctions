---
title: Commands and permissions
description: The /ah command, its sub-commands, the admin commands and every permission.
sidebar:
  order: 3
---

`/ah` has the aliases `/auctionhouse` and `/hdv`.

| Command | Permission | Effect | Example |
|---|---|---|---|
| `/ah` | `auctions.use` (everyone) | Opens the auction house. | `/ah` |
| `/ah search <text>` | `auctions.use` | Opens it filtered by text. | `/ah search diamond sword` |
| `/ah mine` | `auctions.use` | Your active listings. | `/ah mine` |
| `/ah claim` | `auctions.use` | Your claims box: items bought, won, expired, withdrawn, refunds. | `/ah claim` |
| `/ah sell [price] [currency]` | `auctions.sell` (everyone) | Sells the item in hand at a fixed price. Without a price, opens the sell dialog. | `/ah sell 250` |
| `/ah auction [opening bid] [duration] [buy-now] [currency]` | `auctions.sell` | Puts the item in hand up for auction. Without arguments, opens the dialog. | `/ah auction 100 24h 500` |
| bidding, from the lot menu | `auctions.bid` (everyone) | Bid on an auction. | |
| `/ah admin cancel <id>` | `auctions.admin` (op) | Cancels a listing; the item goes to the seller's claims. | `/ah admin cancel 3f2a` |
| `/ah admin closeall [player]` | `auctions.admin` | Cancels every listing, or every listing of a player. | `/ah admin closeall Steve` |
| `/ah admin reindex` | `auctions.admin` | Recomputes the median price keys in the background, after a change of `median.*`. | `/ah admin reindex` |
| `/ah admin import <zauctionhouse\|zauctionhouse-v3\|auctionhouse> [confirm]` | `auctions.admin` | Imports another auction plugin; a dry run without `confirm`. With Artisan API 1.7.0, the same as `/artisan import auctions:<source>` (permission `artisan.admin.import`), which also takes `auctions:json <file>` and `rollback <batch>`. See [zAuctionHouse](../recipes/zauctionhouse-import/), [AuctionHouse](../recipes/auctionhouse-import/), [any plugin](../recipes/import-file/). | `/artisan import auctions:auctionhouse confirm` |

| Permission | Default | Allows |
|---|---|---|
| `auctions.use` | everyone | Open the house, search, own listings, claims |
| `auctions.sell` | everyone | Sell and start auctions |
| `auctions.bid` | everyone | Bid |
| `auctions.limit.<n>` | nobody | Up to `n` active listings instead of `limits.max_active_listings` |
| `auctions.limit.unlimited` | nobody | No limit of active listings |
| `auctions.admin` | op | The `/ah admin` commands |

## Rename the command

Artisan writes `/ah` as `commands/ah.yml` of the module's folder (`plugins/ArtisanAuctions/artisan_module/`,
shipped under `originals/`). Change its name, aliases, permission, cooldown or conditions in the editor
(the **auctions** folder, its commands): your copy goes in `artisan_module/commands/ah.yml` and stays
yours; **Reset** in the editor puts the module's version back.

## In menus

| Name | Kind | Use |
|---|---|---|
| `auctions:listings` | data source | The listings; params `tab`, `sort`, `search` |
| `auctions:my_listings`, `auctions:claims` | data sources | The viewer's listings and claims |
| `auctions:lot`, `auctions:summary`, `auctions:price`, `auctions:settings` | data sources | One listing, counters, the median of an item, the rules |
| `auctions:sell_start`, `sell`, `buy`, `bid`, `cancel`, `claim` | actions | What the shipped menus' buttons call |
