# Artisan Auctions

Auction house add-on for the [Artisan](https://artisanmc.net) suite: fixed-price sales,
timed auctions, or both. Menus, dialogs and texts are files of the module's folder,
`plugins/Artisan/modules/auctions/`, the admin edits in the Artisan editor; the plugin brings the rules, the money and the storage.

## For server owners

- **Kinds of listing.** `sales.enabled` and `auctions.enabled` in the **Settings** screen
  (`data/config.yaml` of the module's folder). Both on: the menus show type tabs. Both off: the house
  is read-only and claims still work.
- **Selling.** Hold the item, click *Sell* in `/ah` (or `/ah sell <price>`,
  `/ah auction <opening bid> [duration] [buy-now]`). The listing fee is kept, the sale tax is
  taken from the seller's revenue.
- **Auctions.** A bid is taken from the bidder and held; the bidder it replaces is paid back
  at once. A bid in the last `anti_snipe.window` pushes the end back. With `buyout`, a seller
  can add a buy-now price.
- **Claims box.** Bought items go straight to an online buyer with room, everything else
  (expired, withdrawn, won while offline, refunds) waits in `/ah claim`. Money owed is paid
  on join. Unclaimed items are purged after `claims.retention`, logged with their full data.
- **Median price.** Shown on each listing and in the sell dialogs, per item and currency.
  Custom items are recognised by an item plugin id (Nexo, Oraxen, ItemsAdder or
  `median.custom_id_keys`), else by `item_name` or name + lore. Put lore lines that differ
  per copy (durability, owner) in `median.lore_ignore`, then run `/ah admin reindex`.
  A price far from the median asks the seller to confirm.
- **Currencies.** `vault` (Vault's economy) and any currency another plugin registers
  (`currencies.allowed`); how an amount is written is `currencies.display`.
- **Menus.** A lot menu (`ah_lot`) repaints when its listing changes, the claims box when
  its content does. The paged grids (`ah_main`, `ah_mine`) show changes on their next render:
  a repaint would send their viewers back to the first page.
- **Several servers on one database.** Each server keeps its own house, tagged by
  `server-id` in `plugins/ArtisanAuctions/config.yml`.

`/ah` is declared to Artisan (`auctions:ah`): rename it, add aliases, a cooldown or conditions
with a `command_override/v1` file in `overrides/command/` of the module's folder.

| Command | Permission |
|---|---|
| `/ah`, `/ah search <text>`, `/ah mine`, `/ah claim` | `auctions.use` (everyone) |
| `/ah sell …`, `/ah auction …` | `auctions.sell` (everyone) |
| bidding | `auctions.bid` (everyone) |
| more active listings | `auctions.limit.<n>`, `auctions.limit.unlimited` |
| `/ah admin cancel <id>`, `closeall [player]`, `reindex`, `import …` | `auctions.admin` (op) |

### Moving from zAuctionHouse

Stop the server, remove the zAuctionHouse jar from `plugins/` (keep its folder), start, then
from the console: `/ah admin import zauctionhouse` (v4) or `/ah admin import zauctionhouse-v3`
for a dry run, and the same with `confirm` to import. The connection comes from
zAuctionHouse's own `config.yml` (SQLite, MySQL or MariaDB; v3 also JSON), opened read-only.

Active listings, items bought or expired but not collected, money owed to sellers, and the
sales history (which feeds the median from day one) are copied. Running it again skips what
was already imported. Import from one version only: if zAuctionHouse already migrated your v3
data into v4, use `zauctionhouse`. A currency the dry run reports as unknown is mapped in
`import.currency_map`. Items stored in zAuctionHouse's pre-1.20.5 NBT form are not read: they
stay in zAuctionHouse and the report lists them.

## For developers

Depend on `net.artisanmc:artisan-auctions-api` (pure Java) and `ArtisanAuctions` in
`plugin.yml`. To offer a currency, register an `AuctionCurrency` (id, balance, withdraw,
deposit in `BigDecimal`) in Bukkit's `ServicesManager`.

```java
AuctionsService auctions = Bukkit.getServicesManager().load(AuctionsService.class);
auctions.medianPrice(stack, "vault").ifPresent(m -> ...);
```

Every action fires a cancellable event first, on the main thread, before any money or item
moves: `AuctionPreListEvent` (price, opening bid and duration can be changed; the known
median is given), `AuctionPrePurchaseEvent`, `AuctionPreBidEvent`, `AuctionPreCancelEvent`.
`deny(Component)` cancels with a message for the player. Outcomes follow:
`AuctionListedEvent`, `AuctionSoldEvent`, `AuctionBidPlacedEvent`, `AuctionOutbidEvent`,
`AuctionExpiredEvent`, `AuctionCancelledEvent`, `AuctionClaimedEvent`.

Menus can read `auctions:listings` (params `tab`, `sort`, `search`), `auctions:my_listings`,
`auctions:claims`, `auctions:lot`, `auctions:summary`, `auctions:price` and
`auctions:settings`, and call `auctions:sell_start`, `sell`, `buy`, `bid`, `cancel`, `claim`.

## Layout

| Module | What it is |
|---|---|
| `api/` | `artisan-auctions-api`: `AuctionsService`, `AuctionCurrency`, `AuctionListing`, `MedianPrice`, the events. |
| `plugin/` | The Paper plugin `ArtisanAuctions` (Kotlin). `depend: [Artisan]`. |

Inside the plugin, Bukkit stays at the edge so the rules are tested without a server:

- `market/`: the rules (`Market`), claims delivery and the ports it needs (threads, wallets,
  items). Money and items always move in an order where a failure ends in the claims box:
  a buyer's charge is journaled (`artisan_auctions_payments`) before it is taken, every close is a
  compare-and-swap on the listing's version, and a claim is marked taken before it is given.
- `median/`: price keys, the median and its cache. `importer/`: replayable imports.
- `db/`: SQL and forward-only migrations of the `artisan_auctions_*` tables (Artisan's `database.table-prefix` +
  `auctions_`), in the suite's shared database (`api.database`).
- `paper/`: the Paper side: item serialization, currencies, events, menu repaints, `/ah`,
  data sources and module commands.
- `src/main/resources/bootstrap/`: the default files, written once to the module's folder
  `plugins/Artisan/modules/auctions/` when it is empty (files left in
  `plugins/ArtisanAuctions/editor/` by an earlier version are moved there once). List new files in `bootstrap.index`.

## Build

Requires Artisan 2 with the API 2.9.0 (table prefix, module folders, player commands). Paper only: the
plugin is not marked for Folia. Until the API is released:

```bash
cd ../artisan/plugin && ./gradlew :api:publishToMavenLocal -PapiVersion=2.9.0
cd ../../artisan-auctions && ./gradlew build   # → plugin/build/libs/ArtisanAuctions-<version>.jar
```

Kotlin's stdlib is not shaded: it comes from Artisan, which ships it under its own names.
Keep the Kotlin version equal to Artisan's (2.0.21).
