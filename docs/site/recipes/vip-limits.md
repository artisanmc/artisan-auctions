---
title: More listings for VIP players
description: Keep the default listing limit low and raise it for ranks with a permission.
sidebar:
  order: 2
---

Players keep 5 active listings, VIPs 15, staff no limit.

```yaml
# plugins/Artisan/modules/auctions/data/config.yaml
limits:
  max_active_listings: 5
```

```text
/lp group vip permission set auctions.limit.15
/lp group staff permission set auctions.limit.unlimited
```

A player who reaches the limit is told so when selling; their listings already up stay.
