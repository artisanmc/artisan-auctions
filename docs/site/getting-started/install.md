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

On first start the plugin creates two folders:

| Folder | Content | Edited from |
|---|---|---|
| `plugins/Artisan/modules/auctions/` | Rules (`data/config.yaml`), menus, dialogs, texts (`lang/`) | The Artisan editor |
| `plugins/ArtisanAuctions/` | `config.yml`: this server's id in a shared database | A text editor |

The default files are written once, when the module's folder is empty. A file you delete is not
written again.

## 3. Open the house

In game, run `/ah`. The aliases `/auctionhouse` and `/hdv` open it too. Hold an item and click
*Sell* to list it.

## 4. Adjust the rules

Open the Artisan editor (`/artisan editor`), then the **Auctions settings** screen. It edits
`data/config.yaml`; every field is described in [Settings](../../configuration/settings/).

## Updating

Stop the server, replace `ArtisanAuctions-<version>.jar`, start it again. The module's folder and the
database are kept.
