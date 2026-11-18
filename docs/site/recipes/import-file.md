---
title: Import from any plugin
description: The import file Artisan Auctions reads, for a plugin it has no converter for.
sidebar:
  order: 5
---

For an auction plugin with no built-in import, write its data into one JSON file, put it in
`plugins/Artisan/imports/`, then:

```text
artisan import auctions:json <file>            # dry run
artisan import auctions:json <file> confirm    # import
```

The dry run lists every wrong value with its place in the file (`listings[3].seller: not a UUID`);
that record is left aside, the rest imports. Running the same file again skips what was imported.

```json
{
  "format": "auctions/1",
  "source": "myplugin",
  "listings": [
    {"id": "1", "seller": "<uuid>", "seller_name": "Steve", "currency": "vault", "price": "12.50",
     "created_at": 1700000000000, "ends_at": 1700600000000,
     "items": [{"encoding": "bukkit_yaml", "data": "i:\n  ==: org.bukkit.inventory.ItemStack\n  ..."}]}
  ],
  "claims": [
    {"id": "2", "owner": "<uuid>", "reason": "expired", "created_at": 1700000000000, "items": [...]},
    {"id": "3", "owner": "<uuid>", "reason": "sold", "created_at": 1700000000000, "currency": "vault", "amount": "40"}
  ],
  "sales": [
    {"id": "4", "seller": "<uuid>", "buyer": "<uuid>", "currency": "vault", "total": "30",
     "sold_at": 1700000000000, "items": [...]}
  ]
}
```

| Field | Rule |
|---|---|
| `format` | `auctions/1`. A later format gets a new number; this one never changes. |
| `source` | A short id (`[a-z0-9_-]`, 40 at most). Ids are unique per source: two files of the same source share them. |
| `id` | Unique within its list and source. |
| `seller`, `owner`, `buyer` | Player UUIDs. `buyer` is optional. |
| `price`, `amount`, `total` | At most two decimals, not negative. |
| `created_at`, `ends_at`, `sold_at` | Epoch milliseconds. |
| `currency` | A currency id of the server, or a name mapped in `import.currency_map`. |
| `reason` | `bought`, `sold`, `expired`, `cancelled`, `won`, `refund`, `outbid` or `imported`. |
| `items` | One or more items, each `{"encoding", "data"}`. |

Item encodings: `bukkit_yaml` (Bukkit's YAML of an `ItemStack`, under any key), `snbt` (the item's NBT
as text, `{id:"minecraft:stone",count:3,components:{...}}`; Paper only), `paper` (base64 of Paper's
`ItemStack.serializeAsBytes`), `zah` (zAuctionHouse's). An item the server cannot read leaves its
record in the file and is listed.
