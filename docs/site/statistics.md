---
title: Statistics
description: What the editor's Auctions tab shows, where the figures come from, and the events another tool can read.
sidebar:
  order: 4
---

The editor's **Statistics** space has an **Auctions** tab: how much money the house moved over the
period, what sold, and what the house holds right now. It needs Artisan with the API 1.5.0 or newer
and statistics turned on in Artisan. On an older Artisan the house works the same, without the tab.

## The tab

Amounts in two currencies never add up, so the tab shows one currency at a time; a selector appears
when the house has seen more than one. The period, the player filter and the comparison with the
previous period come from the Statistics space.

| Block | What it shows |
|---|---|
| Key figures | Volume traded, number of sales, average sale, new listings, unsold listings, and the house's revenue (listing fees plus sale tax), each against the previous period. |
| Now | Fixed-price listings and auctions running, the value on sale (fixed prices plus best bids), and what waits in claims boxes. Read from the server when the tab opens, kept 30 seconds; shown only while the server is on. |
| Volume traded | Money spent on the house, per hour or per day. |
| Items that move the most money | The ten items with the largest volume, with their number of sales. A lot of several kinds counts as *Mixed lot*. |
| Latest sales | Up to 50 sales of the period: when, item, quantity, price, seller and buyer. |

Figures start on the day the plugin runs with statistics: nothing is rebuilt from earlier sales.
Artisan keeps the per-hour figures, and the rows of the latest sales for as long as its statistics
retention says.

## Events

The plugin records these Artisan Stat events. Their names and dimensions are stable.

| Event | Player | Dimensions | Value |
|---|---|---|---|
| `auctions:listed` | seller | `type` (`sale`, `auction`), `currency`, `item` | price, or opening bid |
| `auctions:sold` | buyer | `type`, `currency`, `item` | price paid; each sale is also kept as a row with `seller`, `seller_name`, `buyer_name`, `quantity`, `listing` |
| `auctions:bid` | bidder | `currency`, `item` | amount bid |
| `auctions:expired` | seller | `type`, `currency`, `item` | |
| `auctions:cancelled` | seller | `type`, `currency`, `item`, `by` (`seller`, `admin`) | |
| `auctions:revenue` | payer | `currency`, `kind` (`fee`, `tax`) | amount the house kept |

`item` is the material in lower case (`diamond_sword`), or `lot` for a lot of several kinds. Past 200
distinct items, Artisan groups the rest under `other`.

The figure `auctions:market` (one row per currency: `sales`, `auctions`, `bids`, `sale_value`,
`bid_value`, `money_waiting`, `items_waiting`) is computed by the server on demand.
