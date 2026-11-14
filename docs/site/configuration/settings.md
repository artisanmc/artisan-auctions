---
title: Settings (config.yaml)
description: The auction house rules, edited in the editor's Settings screen; every key, its default and its effect.
sidebar:
  order: 1
---

`plugins/Artisan/modules/auctions/data/config.yaml` holds the rules players see: kinds of listing,
durations, fees, currencies, forbidden items, the claims box and the median price. Edit it in the editor
(the **auctions** folder, **Settings**); a publish applies it.

## The shipped file

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

Durations are written `90s`, `15m`, `48h`, `30d`, or combined (`1h 30m`).

## Sales and auctions

| Key | Type | Default | Effect |
|---|---|---|---|
| `sales.enabled` | boolean | `true` | Fixed-price sales. With auctions on too, the menus show type tabs; both off, the house is read-only and claims still work. |
| `sales.duration` | duration | `48h` | Time before an unsold item goes back to the seller's claims. |
| `sales.min_price`, `sales.max_price` | number | `1`, `1000000000` | Price bounds. |
| `auctions.enabled` | boolean | `true` | Timed auctions. |
| `auctions.durations` | list | `1h` … `48h` | Durations offered to the seller. |
| `auctions.default_duration` | duration | `24h` | Must be one of `durations`. |
| `auctions.min_increment.percent`, `.min` | number | `5`, `1` | A raise is at least this percent of the current bid, and at least `min`. |
| `auctions.anti_snipe.window`, `.extend` | duration | `60s`, `60s` | A bid this close to the end pushes the end back by `extend`. |
| `auctions.buyout` | boolean | `false` | Lets the seller add an optional buy-now price. |

## Limits and fees

| Key | Type | Default | Effect |
|---|---|---|---|
| `limits.max_active_listings` | integer | `10` | Per player; raised by `auctions.limit.<n>` or `auctions.limit.unlimited`. |
| `limits.listing_fee.percent`, `.flat` | number | `0`, `0` | Taken when listing, kept. |
| `limits.sale_tax.percent`, `.flat` | number | `5`, `0` | Taken from the seller's revenue. |
| `limits.cancel_auction_with_bids` | boolean | `true` | The seller may cancel an auction that has bids; the bids are refunded. |

## Currencies and items

| Key | Type | Default | Effect |
|---|---|---|---|
| `currencies.allowed` | list | `[vault]` | `vault` (Vault's economy) or the id of a currency another plugin registers. |
| `currencies.default` | string | `vault` | Used when the seller names none. |
| `currencies.display` | map | `vault: "{amount} $"` | How an amount is written, per currency. |
| `items.blacklist` | list | `BEDROCK`, `BARRIER`, `COMMAND_BLOCK` | Materials that cannot be sold. |
| `items.blacklist_lore_contains` | list | `[]` | Items whose lore contains one of these texts cannot be sold. |

## Claims box

| Key | Type | Default | Effect |
|---|---|---|---|
| `claims.retention` | duration | `30d` | Unclaimed items older than this are purged, and logged with their full data. |
| `claims.deliver_directly` | boolean | `true` | A bought item goes straight to an online buyer with room; otherwise it waits in `/ah claim`. |

## Median price

| Key | Type | Default | Effect |
|---|---|---|---|
| `median.enabled` | boolean | `true` | Shows the median on listings and in the sell dialogs, per item and currency. |
| `median.window.days`, `.max_sales` | integer | `14`, `100` | Sales counted. |
| `median.min_sales` | integer | `5` | Sales needed before a median shows. |
| `median.key` | `auto` / `vanilla` | `auto` | `auto`: custom id, then name + lore, then material. `vanilla`: material and enchantments only. |
| `median.custom_id_keys` | list | `[]` | Item id keys besides Nexo, Oraxen and ItemsAdder, e.g. `myplugin:item_id`. |
| `median.lore_ignore` | list of regex | `[]` | Lore lines that differ per copy (durability, owner). Run `/ah admin reindex` after a change. |
| `median.custom_include_enchants` | boolean | `false` | Enchantments change a custom item's identity. |
| `median.attributes` | `ignore` / `keys` / `values` | `ignore` | How item attributes count; `ignore` when they are written in the lore. |
| `median.custom_window`, `median.custom_min_sales` | | `90` days / `50` sales, `3` | The same, for custom items. |
| `median.include` | list | `[sale, auction]` | Listing kinds counted. |
| `median.per_seller_cap` | integer | `3` | Sales counted per seller, so one seller cannot move the median. |
| `median.suggest_on_sell` | boolean | `true` | Suggest the median as the price when selling. |
| `median.warn_below`, `median.warn_above` | number | `0.5`, `3.0` | A price under or over this multiple of the median asks the seller to confirm. |

## Import

| Key | Type | Default | Effect |
|---|---|---|---|
| `import.currency_map` | map | `{}` | Source currency name to currency id here, e.g. `VAULT: vault`. Same names map themselves. |

## Texts

The texts are `plugins/Artisan/modules/auctions/lang/en.yaml` and `fr.yaml`, edited in the editor's
**Translations** like the menus and dialogs.
