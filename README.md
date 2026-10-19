# Artisan Auctions

Auction house add-on for the [Artisan](https://artisanmc.net) suite: fixed-price sales,
time-limited auctions, or both. Menus, dialogs and texts are an Artisan Project the admin
edits in the Artisan editor; the plugin brings the rules, the money and the storage.

Status: foundation only (module, Project, settings, database). Sales, auctions, the
zAuctionHouse import and the median price come next.

## Layout

| Module | What it is |
|---|---|
| `api/` | `artisan-auctions-api`, pure Java: `AuctionsService` and, later, the cancellable events other plugins hook into. |
| `plugin/` | The Paper plugin `ArtisanAuctions` (Kotlin). `depend: [Artisan]`. |

Inside the plugin:

- `AuctionsPlugin` gets the Artisan API, migrates the database, registers the Project
  root `auctions_addon` (`plugins/ArtisanAuctions/editor/`, written once from
  `src/main/resources/bootstrap/`) and the module.
- `AuctionsModule` declares the `auctions:config` model, the **Settings** screen and the
  `auctions:settings` data source, and reloads `data/config.yaml` on every Artisan reload.
  A config with errors is refused and the previous one stays in force.
- `db/` holds the forward-only migrations of the `auc_*` tables, in the suite's shared
  database (`api.database`).

## Build

Requires the Artisan API 1.8.0 (`api.economy`, `api.menus`). Until it is released:

```bash
cd ../artisan/plugin && ./gradlew publishToMavenLocal -PapiVersion=1.8.0
cd ../../artisan-auctions && ./gradlew build   # → plugin/build/libs/ArtisanAuctions-<version>.jar
```

Kotlin's stdlib is not shaded: it comes from Artisan, which ships it under its own names.
Keep the Kotlin version equal to Artisan's (2.0.21).

## Local settings

`plugins/ArtisanAuctions/config.yml` only holds `server-id`, which tags this server's
listings in the shared database. Everything else is in the Project.
