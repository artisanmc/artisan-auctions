---
title: A median price for custom items
description: Make copies of one custom item share a median, even when their lore differs.
sidebar:
  order: 4
---

Your Nexo, Oraxen or ItemsAdder swords get one median price, even though each copy shows its own
durability and owner in the lore.

With `median.key: auto`, an item is identified by, in order:

1. its custom item id: Nexo, Oraxen, ItemsAdder, or a key listed in `median.custom_id_keys`;
2. else its `item_name`, or its name + lore;
3. else its material.

Lines that differ per copy go in `median.lore_ignore` as regexes, and custom items, which sell less
often, get their own window and threshold:

```yaml
# plugins/ArtisanAuctions/artisan_module/data/config.yaml (editor: Auctions settings)
median:
  key: auto
  custom_id_keys: ["myplugin:item_id"]
  lore_ignore:
    - "^Durability: \\d+/\\d+$"
    - "^Owner: .*"
  custom_window: { days: 90, max_sales: 50 }
  custom_min_sales: 3
```

Then rebuild the keys of past sales:

```text
/ah admin reindex
```

Check it: sell two copies with different durability, then open the sell dialog with a third one. It
shows a single median once `custom_min_sales` sales are recorded.
