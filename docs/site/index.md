---
title: Artisan Auctions
description: An auction house with fixed-price sales, timed auctions, or both, edited in Artisan.
sidebar:
  order: 0
---

Artisan Auctions gives your players an auction house: they sell with `/ah`, bid on timed auctions and
collect what they bought or won from a claims box. A median price is shown on every listing, and one
command imports an existing zAuctionHouse.

## Requirements

| | |
|---|---|
| Server | Paper 1.21 or newer (not Folia) |
| Java | 21 |
| Artisan | API 1.0.0 or newer (Artisan 1.0.0) |
| Optional | Vault and an economy plugin, for the `vault` currency |

Artisan provides the menus, the dialogs, the editor and the database (H2, MySQL or MariaDB, set in
`plugins/Artisan/environment.yml`).

## Where to go next

- [Install it](install/).
- [The rules: sales, auctions, fees, median](configuration/settings/), and
  [the local config.yml](configuration/config-yml/).
- [Commands and permissions](commands/).
- Worked cases: [auctions only](recipes/auctions-only/), [VIP listing limits](recipes/vip-limits/),
  [move from zAuctionHouse](recipes/zauctionhouse-import/).
