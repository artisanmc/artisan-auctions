---
title: Move from AuctionHouse
description: Import listings, collection bins, money owed and the sales history from AuctionHouse by Kiranhart.
sidebar:
  order: 4
---

You replace AuctionHouse (Kiranhart / Tweetzy) without losing a listing or a coin owed to a seller.
Requires Artisan with the API 1.7.0 or later.

1. Let the auctions someone bid on end in AuctionHouse: the bid's money is AuctionHouse's, so those
   listings are not imported.
2. Stop the server. Remove the AuctionHouse jar from `plugins/` and keep its folder: the import reads
   its `config.yml` for the connection (`plugins/AuctionHouse/auctionhouse.db`, or the MySQL database of
   `database.use database`), read-only.
3. Start the server with Artisan Auctions. From the console, a dry run:

   ```text
   artisan import auctions:auctionhouse
   ```

4. A currency the report calls unknown goes in `import.currency_map` of the Settings. AuctionHouse
   names its currencies `Plugin/Currency`; the import reads `Vault/Vault` as `vault` and
   `CoinsEngine/gems` as `gems`.
5. Import:

   ```text
   artisan import auctions:auctionhouse confirm
   ```

   `artisan import rollback <batch>` undoes it, keeping what players changed since.

| In AuctionHouse | In Artisan Auctions |
|---|---|
| A listing with a buy-now price | An active listing at that price, same end |
| An auction with no buy-now price and no bid | Its item in the seller's claims |
| The collection bin (expired, cancelled, won) | Items in the owner's claims |
| Money waiting for a player | An amount in their claims |
| Transactions | The sales history, which feeds the median |

Left in AuctionHouse and listed by the report: auctions with a bid, buy requests, the server's own
and infinite listings, and anything priced in an item currency. Running the import again skips what
was already imported.
