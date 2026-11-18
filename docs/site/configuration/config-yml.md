---
title: config.yml
description: The one setting that belongs to this server only, its id in a shared database.
sidebar:
  order: 2
---

`plugins/ArtisanAuctions/config.yml` holds only what belongs to this server. Everything players see is in
the [Settings](../settings/).

```yaml
# Tags this server's listings in the suite's shared database. Two servers that
# share the database keep separate auction houses as long as their ids differ.
# Left empty, an id is generated at the first start.
server-id: ""
```

| Key | Type | Default | Effect |
|---|---|---|---|
| `server-id` | string | generated | Tags this server's listings. Servers on one database with different ids keep separate auction houses. Do not change it once players have listings: they stay under the old id. |
