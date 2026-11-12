---
title: Median price
description: How the median price groups items, and how to set it up for custom items.
sidebar:
  order: 2
---

Each listing and each sell dialog shows the median price of recent sales of the same item, per
currency. A price far from it asks the seller to confirm (`median.warn_below`, `median.warn_above`).

## Which items count as "the same"

With `median.key: auto`, an item is identified by, in order:

1. its custom item id: Nexo, Oraxen, ItemsAdder, or a key listed in `median.custom_id_keys`;
2. else its `item_name`, or its name + lore;
3. else its material.

`median.key: vanilla` uses only the material and enchantments.

## Lore that differs between copies

Durability, owner or kill counters in the lore make two copies look different. List those lines as
regexes, then rebuild the keys:

```yaml
median:
  lore_ignore:
    - "^Durability: \\d+/\\d+$"
    - "^Owner: .*"
```

```
/ah admin reindex
```

## Custom items sell less often

They get their own window and threshold, so a median shows up with fewer sales:

```yaml
median:
  custom_window: { days: 90, max_sales: 50 }
  custom_min_sales: 3
```

`median.per_seller_cap` (default `3`) limits how many sales of one seller count, so a single seller
cannot set the price.
