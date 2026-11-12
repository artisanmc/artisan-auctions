---
title: Several servers, one database
description: Keep a separate auction house per server when servers share Artisan's database.
sidebar:
  order: 2
---

Artisan Auctions stores its data in Artisan's database (H2 by default, MySQL or MariaDB when set in
`plugins/Artisan/environment.yml`). Tables are named with Artisan's table prefix + `auctions_`.

When several servers share that database, each one keeps its own auction house, tagged by
`server-id` in `plugins/ArtisanAuctions/config.yml`:

```yaml
# Left empty, an id is generated at the first start.
server-id: "survival-1"
```

Two servers with different ids never see each other's listings. Give them the same id only if you
want them to share one house.
