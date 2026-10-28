---
title: Recipes
description: Ready-made settings for common auction houses.
sidebar:
  order: 3
---

Each recipe shows only the keys that change from the [default settings](../../configuration/settings/).

## Fixed-price market only

```yaml
sales:
  enabled: true
auctions:
  enabled: false
```

The menus show no type filter.

## Auctions with a buy-now price and a 1-day maximum

```yaml
auctions:
  enabled: true
  durations: [1h, 6h, 12h, 24h]
  default_duration: 12h
  buyout: true
```

## A listing fee instead of a sale tax

The fee is paid when listing and kept, which discourages spam listings:

```yaml
limits:
  listing_fee: { percent: 2, flat: 10 }
  sale_tax: { percent: 0, flat: 0 }
```

## VIPs list more items

Keep `limits.max_active_listings: 10` for everyone, and give the VIP group the permission
`auctions.limit.25` (with LuckPerms: `lp group vip permission set auctions.limit.25`).
