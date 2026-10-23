---
title: Commands and permissions
description: Every /ah command, its permission and an example.
sidebar:
  order: 1
---

`/ah` also answers to `/auctionhouse` and `/hdv`. Everything a player can do here is also in the menus.

| Command | Permission (default) | Effect |
|---|---|---|
| `/ah` | `auctions.use` (everyone) | Opens the auction house. |
| `/ah search <text>` | `auctions.use` | Opens it filtered: `/ah search diamond sword` |
| `/ah mine` | `auctions.use` | Your active listings. |
| `/ah claim` | `auctions.use` | Your claims box. |
| `/ah sell [price] [currency]` | `auctions.sell` (everyone) | Lists the item in hand: `/ah sell 250`. Without a price, opens the sell dialog. |
| `/ah auction [start] [duration] [buy-now] [currency]` | `auctions.sell` | Starts an auction: `/ah auction 100 24h 500`. Without arguments, opens the dialog. |
| bidding (menus) | `auctions.bid` (everyone) | Bid on auctions. |
| `/ah admin cancel <id>` | `auctions.admin` (op) | Cancels a listing; the item goes to the seller's claims. |
| `/ah admin closeall [player]` | `auctions.admin` | Cancels every listing, or every listing of one player. |
| `/ah admin reindex` | `auctions.admin` | Recomputes the median's item keys after a change to `median.*`. Console allowed. |
| `/ah admin import <source> [confirm]` | `auctions.admin` | Imports from another plugin: a dry run, then `confirm`. Console allowed. |

| Permission | Effect |
|---|---|
| `auctions.limit.<n>` | Up to `n` active listings, instead of `limits.max_active_listings`. |
| `auctions.limit.unlimited` | No limit of active listings. |

## Rename or restrict `/ah`

`/ah` is declared to Artisan as `auctions:ah`. Artisan writes it as a command file of the module's
folder, `plugins/Artisan/modules/auctions/commands/ah.yaml`, whose actions call the module's code
(`module_command: auctions:ah`) and whose `subcommands` are `/ah sell`, `/ah admin reindex` and the
rest. To rename it, add aliases, a cooldown or conditions, edit it in the editor (the folder's
commands) or in that file. While the file is untouched, each new version of the module replaces
it; once edited it stays yours, and Reset in the editor puts the module's version back.
