---
title: Move from zAuctionHouse
description: Import listings, unclaimed items, money owed and sales history from zAuctionHouse v3 or v4.
sidebar:
  order: 1
---

The import copies, from zAuctionHouse:

- active listings;
- items bought or expired but not collected;
- money owed to sellers;
- the sales history, so the median price works from day one.

It reads zAuctionHouse's database (SQLite, MySQL or MariaDB; v3 also JSON) through zAuctionHouse's
own `config.yml`, read-only.

## Steps

1. Stop the server.
2. Remove the zAuctionHouse jar from `plugins/`. **Keep its folder**: the import reads it.
3. Start the server with Artisan and Artisan Auctions.
4. From the console, run a dry run:
   ```
   ah admin import zauctionhouse
   ```
   Use `zauctionhouse-v3` for version 3. The report lists what would be imported and any currency it
   does not know.
5. Map unknown currencies in `import.currency_map` of the [settings](../../configuration/settings/):
   ```yaml
   import:
     currency_map:
       VAULT: vault
   ```
6. Import:
   ```
   ah admin import zauctionhouse confirm
   ```

Running the import again skips what was already imported.

## Limits

- Import from one version only. If zAuctionHouse already migrated your v3 data into v4, use
  `zauctionhouse`.
- Items stored in zAuctionHouse's pre-1.20.5 NBT form are not read. They stay in zAuctionHouse, and the
  report lists them.
