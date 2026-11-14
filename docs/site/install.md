---
title: Install
description: Put the jar next to Artisan and Vault, start the server, and open the auction house.
sidebar:
  order: 1
---

You get a working auction house in four steps.

1. Install [Artisan](../../artisan/getting-started/install-admin/) 1.0.0 or newer, and Vault with an
   economy plugin.
2. Drop `ArtisanAuctions-<version>.jar` into `plugins/`.
3. Start the server. The console shows the module's database migrations on the first start:

   ```text
   [Auctions] database migrated to version 1 (…)
   ```

4. In game, type `/ah`: the auction house opens.

## What the first start writes

| Path | What |
|---|---|
| `plugins/ArtisanAuctions/config.yml` | This server's id in the shared database ([config.yml](../configuration/config-yml/)) |
| `plugins/Artisan/modules/auctions/data/config.yaml` | The rules ([Settings](../configuration/settings/)) |
| `plugins/Artisan/modules/auctions/menus/` | `ah_main`, `ah_mine`, `ah_lot`, `ah_claims` |
| `plugins/Artisan/modules/auctions/dialogs/` | Sell, bid, search, confirm and price-warning dialogs |
| `plugins/Artisan/modules/auctions/lang/` | The texts, `en.yaml` and `fr.yaml` |
| `plugins/Artisan/modules/auctions/commands/ah.yml` | The `/ah` command, written by Artisan |
| Tables `artisan_auctions_*` | Listings, claims, payments, sales history |

Menus, dialogs, texts and the command are system elements: Artisan writes them again when they are
missing and replaces them with each new version while you have not edited them. Once edited, they stay
yours; **Reset** in the editor puts the module's version back.

If the plugin disables itself with `ArtisanAuctions needs Artisan with the API 1.0.0 or newer`, update
Artisan.
