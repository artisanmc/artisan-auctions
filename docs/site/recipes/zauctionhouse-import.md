---
title: Move from zAuctionHouse
description: Import listings, unclaimed items, money owed and the sales history from zAuctionHouse.
sidebar:
  order: 3
---

You replace zAuctionHouse without losing a listing or a coin owed to a seller.

1. Stop the server. Remove the zAuctionHouse jar from `plugins/` and keep its folder: the import reads
   its `config.yml` for the connection (SQLite, MySQL or MariaDB; v3 also JSON), read-only.
2. Start the server with Artisan Auctions.
3. From the console, a dry run:

   ```text
   artisan import auctions:zauctionhouse
   ```

   Use `auctions:zauctionhouse-v3` for a v3 install. If zAuctionHouse already migrated your v3 data into v4,
   import `auctions:zauctionhouse` only. The report lists what would be imported and what stays in
   zAuctionHouse, and why.
4. A currency the report calls unknown goes in `import.currency_map` of the Settings:

   ```yaml
   import:
     currency_map:
       VAULT: vault
   ```

5. Import:

   ```text
   artisan import auctions:zauctionhouse confirm
   ```

   The report ends with a batch id. `artisan import rollback <batch>` then undoes the import, keeping
   what players changed since (a listing bought, an item claimed).

Active listings, items bought or expired but not collected, money owed to sellers and the sales history
(which feeds the median from day one) are copied. Running it again skips what was already imported.
Items stored in zAuctionHouse's pre-1.20.5 NBT form are not read: they stay in zAuctionHouse and the
report lists them.

With Artisan older than API 1.7.0, `ah admin import zauctionhouse [confirm]` does the same, without
the history or the rollback. With a newer one, it hands over to `artisan import`, which needs the
permission `artisan.admin.import`.
