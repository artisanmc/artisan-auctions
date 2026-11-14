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
   ah admin import zauctionhouse
   ```

   Use `zauctionhouse-v3` for a v3 install. If zAuctionHouse already migrated your v3 data into v4,
   import `zauctionhouse` only.
4. A currency the report calls unknown goes in `import.currency_map` of the Settings:

   ```yaml
   import:
     currency_map:
       VAULT: vault
   ```

5. Import:

   ```text
   ah admin import zauctionhouse confirm
   ```

Active listings, items bought or expired but not collected, money owed to sellers and the sales history
(which feeds the median from day one) are copied. Running it again skips what was already imported.
Items stored in zAuctionHouse's pre-1.20.5 NBT form are not read: they stay in zAuctionHouse and the
report lists them.
