---
title: Auctions only, with a buy-now price
description: Turn off fixed-price sales and let sellers add a buy-now price to their auctions.
sidebar:
  order: 1
---

Your house runs auctions only, of 12 or 24 hours, and a seller may add a buy-now price.

```yaml
# plugins/Artisan/modules/auctions/data/config.yaml (editor: auctions folder, Settings)
sales:
  enabled: false
auctions:
  enabled: true
  durations: [12h, 24h]
  default_duration: 24h
  anti_snipe: { window: 2m, extend: 2m }
  buyout: true
```

With one kind of listing, the menus show no type tabs. Check it: hold an item, `/ah auction 100 12h 400`,
then open `/ah` from another account and buy it now.
