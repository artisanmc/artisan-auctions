# Artisan Auctions

Auction house add-on for the [Artisan](https://artisanmc.net) suite: fixed-price sales,
timed auctions, or both. Menus, dialogs and texts are files of the module's folder,
`plugins/ArtisanAuctions/artisan_module/`, the admin edits in the Artisan editor; the plugin brings the rules, the money and the storage.

## For server owners

- **Kinds of listing.** `sales.enabled` and `auctions.enabled` in the **Settings** screen
  (`data/config.yaml` of the module's folder). Both on: the main menu shows a filter button (all, sales, auctions). Both off: the house
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

`/ah` is declared by the auctions module (`auctions:ah`), and Artisan writes it as the command
file `commands/ah.yaml` of the module's folder (`plugins/ArtisanAuctions/artisan_module/`): rename it,
add aliases, a cooldown or conditions from that folder in the editor, or in that file. Turning the
module off removes the command.

| Command | Permission |
|---|---|
| `/ah`, `/ah search <text>`, `/ah mine`, `/ah claim` | `auctions.use` (everyone) |
| `/ah sell …`, `/ah auction …` | `auctions.sell` (everyone) |
| bidding | `auctions.bid` (everyone) |
| more active listings | `auctions.limit.<n>`, `auctions.limit.unlimited` |
| `/ah admin cancel <id>`, `closeall [player]`, `reindex`, `import …` | `auctions.admin` (op) |

### Moving from another auction plugin

Stop the server, remove the other plugin's jar from `plugins/` (keep its folder), start, then from
the console a dry run, and the same with `confirm` to import:

| From | Command (Artisan API 1.7.0+) |
|---|---|
| zAuctionHouse v4 / v3 | `/artisan import auctions:zauctionhouse` / `auctions:zauctionhouse-v3` |
| AuctionHouse (Kiranhart) | `/artisan import auctions:auctionhouse` |
| anything else | `/artisan import auctions:json <file>`, the `auctions/1` file of `docs/site/recipes/import-file.md` |

The source is opened read-only from its own `config.yml`. The report lists what would be imported
and what stays in the source, and why. Running it again skips what was already imported. A batch
can be rolled back (`/artisan import rollback <batch>`); what players changed since is kept. On an
older Artisan, `/ah admin import <source> [confirm]` imports without the history or the rollback.
A currency the dry run reports as unknown is mapped in `import.currency_map`. Recipes:
`docs/site/recipes/`.

## For developers

Depend on `net.artisanmc:artisan-auctions-api` (pure Java) and `ArtisanAuctions` in
`plugin.yml`. To offer a currency, register an `AuctionCurrency` (id, balance, withdraw,
deposit in `BigDecimal`) in Bukkit's `ServicesManager`.

```java
AuctionsService auctions = Bukkit.getServicesManager().load(AuctionsService.class);
auctions.medianPrice(stack, "vault").ifPresent(m -> ...);
```

Every action fires a cancellable event first, before any money or item moves (on Folia,
`AuctionPreListEvent` on the seller's region thread, the others on the global region thread): `AuctionPreListEvent` (price, opening bid and duration can be changed; the known
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
- `median/`: price keys, the median and its cache. `importer/`: replayable imports (readers of each
  source, the `auctions/1` file, SNBT items), registered with Artisan's `/artisan import` by
  `paper/ArtisanImports`; each record carries its batch in `import_map` for the rollback.
- `db/`: SQL and forward-only migrations of the `artisan_auctions_*` tables (Artisan's `database.table-prefix` +
  `auctions_`), in the suite's shared database (`api.database`).
- `paper/`: the Paper side: item serialization, currencies, events, menu repaints, `/ah`,
  data sources and module commands.
- `src/main/resources/module/`: the menus, dialogs, texts and settings of the module's folder
  `plugins/ArtisanAuctions/artisan_module/`, a system template Artisan keeps there (`files/ModuleFiles`):
  a missing file is written again, an untouched one follows each new version, an edited one
  stays the admin's until Reset. List a new file in `ModuleFiles.PATHS` and raise
  `ModuleFiles.VERSION` when one changes. Files left in `plugins/ArtisanAuctions/editor/` by an
  earlier version are moved to the folder once.

## Build

Requires Artisan with the API 1.1.0 or later. Paper, Folia or Spigot. On Paper and Folia, items are stored
with Paper's byte serialization; on Spigot, with Bukkit's YAML serialization (`yaml:` prefix).
A server moving from Paper to Spigot cannot read the items stored on Paper. `artisan-core-api` comes
from `repo.artisanmc.net/releases`; for an unreleased version, publish it locally first
(`cd ../artisan/plugin && ./gradlew :artisan-api:publishToMavenLocal -PapiVersion=<version>`).

```bash
./gradlew build   # → plugin/build/libs/ArtisanAuctions-<version>.jar
```

Before a release, play it in game on Paper and on Folia: `e2e/run.sh paper <Artisan jar>`, then
`e2e/run.sh folia <Artisan jar>` (see [`e2e/`](e2e/README.md)).

Kotlin's stdlib is not shaded: it comes from Artisan, which ships it under its own names.
Keep the Kotlin version equal to Artisan's (2.0.21).

## Bedrock

A Bedrock player (Geyser) only sends one click, the tap. `ah_main` puts actions on the right and
shift clicks, so it ships in a `bedrock` variant, shown to Bedrock players when the menu opens
(`variants.yaml` of the module's folder, editable under **View as → Display rules…** in the
editor): a button of the bottom bar picks what a tap on a listing does (buy or bid, buy an auction
now, see the lot's contents) and another clears the search. It needs no resource pack.

The three other menus only use the left click: Bedrock players get them as they are.
