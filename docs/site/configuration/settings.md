---
title: Settings
description: Every option of data/config.yaml, the rules of the auction house, with defaults.
sidebar:
  order: 1
---

The rules live in `plugins/Artisan/modules/auctions/data/config.yaml`. Edit them in the editor's
**Auctions settings** screen, or by hand. Durations are written like `90s`, `15m`, `48h`, `30d`.

## Default file

```yaml
sales:
  enabled: true
  duration: 48h
  min_price: 1
  max_price: 1000000000
auctions:
  enabled: true
  durations: [1h, 6h, 12h, 24h, 48h]
  default_duration: 24h
  min_increment: { percent: 5, min: 1 }
  anti_snipe: { window: 60s, extend: 60s }
  buyout: false
limits:
  max_active_listings: 10
  listing_fee: { percent: 0, flat: 0 }
  sale_tax: { percent: 5, flat: 0 }
  cancel_auction_with_bids: true
currencies:
  allowed: [vault]
  default: vault
  display:
    vault: "{amount} $"
items:
  blacklist: [BEDROCK, BARRIER, COMMAND_BLOCK]
  blacklist_lore_contains: []
claims:
  retention: 30d
  deliver_directly: true
median:
  enabled: true
  window: { days: 14, max_sales: 100 }
  min_sales: 5
  key: auto
  custom_id_keys: []
  lore_ignore: []
  custom_include_enchants: false
  attributes: ignore
  custom_window: { days: 90, max_sales: 50 }
  custom_min_sales: 3
  include: [sale, auction]
  per_seller_cap: 3
  suggest_on_sell: true
  warn_below: 0.5
  warn_above: 3.0
import:
  currency_map: {}
```

## `sales`: fixed-price sales

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | Players can list at a fixed price. With `auctions.enabled` too, the menus show type tabs. Both off: the house is read-only, claims still work. |
| `duration` | `48h` | Time before an unsold item goes back to the seller's claims. |
| `min_price`, `max_price` | `1`, `1000000000` | Price range accepted. |

## `auctions`: timed auctions

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | Players can start auctions. |
| `durations` | `[1h, 6h, 12h, 24h, 48h]` | Durations offered to the seller. |
| `default_duration` | `24h` | Must be one of `durations`. |
| `min_increment.percent`, `.min` | `5`, `1` | A new bid must beat the current one by this percent, and by at least `min`. |
| `anti_snipe.window`, `.extend` | `60s`, `60s` | A bid in the last `window` pushes the end back to `extend` from now. |
| `buyout` | `false` | Lets the seller add an optional buy-now price. |

A bid is taken from the bidder when placed and held. The bidder it replaces is paid back at once.

## `limits`: limits and fees

| Key | Default | Effect |
|---|---|---|
| `max_active_listings` | `10` | Per player. Raise it per player with the permission `auctions.limit.<n>`, remove it with `auctions.limit.unlimited`. |
| `listing_fee.percent`, `.flat` | `0`, `0` | Charged when listing, kept even if the item does not sell. |
| `sale_tax.percent`, `.flat` | `5`, `0` | Taken from the seller's revenue on a sale. |
| `cancel_auction_with_bids` | `true` | The seller may cancel an auction that has bids; bids are refunded. |

## `currencies`

| Key | Default | Effect |
|---|---|---|
| `allowed` | `[vault]` | `vault` (Vault's economy) or the id of a currency another plugin registers. |
| `default` | `vault` | Currency used when the seller does not pick one. |
| `display` | `vault: "{amount} $"` | How an amount is written, per currency. `{amount}` is replaced. |

## `items`

| Key | Default | Effect |
|---|---|---|
| `blacklist` | `[BEDROCK, BARRIER, COMMAND_BLOCK]` | Materials that cannot be listed. |
| `blacklist_lore_contains` | `[]` | An item whose lore contains one of these texts cannot be listed. |

## `claims`: the claims box

| Key | Default | Effect |
|---|---|---|
| `retention` | `30d` | Unclaimed items older than this are purged, logged with their full data. |
| `deliver_directly` | `true` | A bought item goes straight to an online buyer who has room. |

## `median`: median price

See [Median price](../../guides/median-price/) for how items are grouped.

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | Shows the median on listings and in the sell dialogs. |
| `window.days`, `.max_sales` | `14`, `100` | Sales counted: the most recent, within this many days. |
| `min_sales` | `5` | Sales needed before a median is shown. |
| `key` | `auto` | `auto`: custom id, then name + lore, then material. `vanilla`: material and enchantments only. |
| `custom_id_keys` | `[]` | Extra item id keys, besides Nexo, Oraxen and ItemsAdder, e.g. `myplugin:item_id`. |
| `lore_ignore` | `[]` | Regexes of lore lines that differ between copies (durability, owner). |
| `custom_include_enchants` | `false` | Enchantments change a custom item's identity. |
| `attributes` | `ignore` | `ignore`, `keys` or `values`: how item attributes count. Use `ignore` when they are written in the lore. |
| `custom_window`, `custom_min_sales` | `90` days / `50` sales, `3` | The same, for custom items, which sell less often. |
| `include` | `[sale, auction]` | Which kinds of sale feed the median. |
| `per_seller_cap` | `3` | Sales counted per seller, so one seller cannot set the price alone. |
| `suggest_on_sell` | `true` | Suggests the median as the price when selling. |
| `warn_below`, `warn_above` | `0.5`, `3.0` | A price below or above the median times this asks the seller to confirm. |

## `import`

| Key | Default | Effect |
|---|---|---|
| `currency_map` | `{}` | Currency name in the source plugin → currency id here, e.g. `VAULT: vault`. See [Move from zAuctionHouse](../../guides/move-from-zauctionhouse/). |
