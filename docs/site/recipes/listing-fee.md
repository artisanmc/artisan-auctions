---
title: A listing fee instead of a sale tax
description: Charge sellers when they list rather than when they sell, to discourage spam listings.
sidebar:
  order: 5
---

Sellers pay 2 % of the price plus 10 when they list, and keep the whole price when the item sells. The
fee is not refunded when the listing expires or is cancelled.

```yaml
# plugins/ArtisanAuctions/artisan_module/data/config.yaml (editor: Auctions settings)
limits:
  listing_fee: { percent: 2, flat: 10 }
  sale_tax: { percent: 0, flat: 0 }
```

Check it: note your balance, `/ah sell 1000`, and your balance drops by 30.
