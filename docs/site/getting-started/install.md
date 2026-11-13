---
title: Install
description: Install Artisan Auctions next to Artisan, start the server and open the auction house.
sidebar:
  order: 1
---

## 1. Add the jars

Put both jars in `plugins/`:

- the Artisan jar (see [Install Artisan](https://artisanmc.net/docs/artisan/getting-started/install-admin/))
- `ArtisanAuctions-<version>.jar`

Add Vault and an economy plugin if you sell for in-game money.

## 2. Start the server

On first start the plugin creates its folder, `plugins/ArtisanAuctions/`:

| Path | Content | Edited from |
|---|---|---|
| `artisan_module/` | Rules (`data/config.yml`), menus, dialogs, texts (`lang/`) | The Artisan editor |
| `config.yml` | This server's id in a shared database | A text editor |

Everything the plugin stores on disk is in that folder: copy it to move the module to another
server. A server updated from an earlier version had `artisan_module/` in
`plugins/Artisan/modules/auctions/`: Artisan moves it once at startup.

Artisan keeps the module's menus, dialogs, texts and settings in that folder: a file you delete
is written again on the next start, a file you never edited follows each new version of the
plugin, and a file you edited stays yours until you reset it in the editor.

## 3. Open the house

In game, run `/ah`. The aliases `/auctionhouse` and `/hdv` open it too. Hold an item and click
*Sell* to list it.

## 4. Adjust the rules

Open the Artisan editor (`/artisan editor`), then the **Auctions settings** screen. It edits
`data/config.yaml`; every field is described in [Settings](../../configuration/settings/).

## Updating

Stop the server, replace `ArtisanAuctions-<version>.jar`, start it again. The module's folder and the
database are kept.
