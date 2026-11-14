---
title: Troubleshooting
description: What a console message or a refused sale means, and how to fix it.
sidebar:
  order: 5
---

| Symptom | Cause | Fix |
|---|---|---|
| `ArtisanAuctions needs Artisan with the API 1.0.0 or newer (found: …)` and the plugin disables itself | Artisan is missing or too old. | Install or update Artisan to 1.0.0 or newer. |
| `/ah` says the currency is unknown, or nothing can be sold | `vault` is allowed but Vault has no economy plugin. | Install an economy plugin, or allow another currency in `currencies.allowed`. |
| An item cannot be sold | Its material is in `items.blacklist`, or its lore matches `items.blacklist_lore_contains`. | Change the lists in the Settings. |
| The median is missing on a listing | Fewer sales than `median.min_sales` (or `custom_min_sales`) in the window. | Wait for more sales, or lower the minimum. |
| Copies of one custom item get different medians | A lore line differs per copy (durability, owner). | Add a regex for it to `median.lore_ignore`, then `/ah admin reindex`. |
| A grid menu does not show a new listing at once | `ah_main` and `ah_mine` show changes on their next render, so that viewers are not sent back to page 1. | Turn the page or reopen the menu. The lot menu and the claims box repaint at once. |
| Two servers on one database show each other's listings | They have the same `server-id`. | Give each server its own id in `plugins/ArtisanAuctions/config.yml`. |
| An edited menu no longer follows the module's new version | You edited it: it stays yours. | **Reset** it in the editor to take the module's version. |
