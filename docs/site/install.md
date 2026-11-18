---
title: Install
description: Put the jar next to Artisan and Vault, start the server, and open the auction house.
sidebar:
  order: 1
---

You get a working auction house in four steps.

1. Install [Artisan](../../artisan/getting-started/install-admin/) with the API 1.1.0 or newer, and Vault with an
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
| `plugins/ArtisanAuctions/artisan_module/originals/` | The module's files as shipped, written by Artisan at each start: `data/config.yml` (the rules, see [Settings](../configuration/settings/)), `menus/` (`ah_main`, `ah_mine`, `ah_lot`, `ah_claims`), `dialogs/` (sell, bid, search, confirm, price warning), `lang/` (`en.yaml`, `fr.yaml`, `de.yaml`, `es.yaml`), `commands/ah.yml` |
| Tables `artisan_auctions_*` | Listings, claims, payments, sales history |

You never edit `originals/`. When you change a file in the editor (or copy one out of `originals/` to
the same path in `artisan_module/`), your copy goes next to it, in `artisan_module/`, and wins over
the shipped one. A new version of the plugin updates `originals/` and merges its changes into your
copies. **Reset** in the editor deletes your copy and goes back to the shipped one.

Everything the plugin keeps on disk is in `plugins/ArtisanAuctions/`: copy that folder to move the module
to another server. A server updated from a development build kept the module's files in
`plugins/Artisan/modules/auctions/`: Artisan moves them to `artisan_module/` once, at startup.

If the plugin disables itself with `ArtisanAuctions needs Artisan with the API 1.1.0 or newer`, update
Artisan.
