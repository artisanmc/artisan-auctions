# In-game test

`e2e/run.sh <paper|folia> <Artisan jar>` starts a local server with the plugin, a test harness and
three bots (Seller, Buyer, Bidder), and plays the house: a sale, two buyers racing for one listing,
an auction with an outbid and an admin cancel, the claims box, an offline seller, and `/ah`'s menu.
It fails on a failed check or on an exception in the server log. Run it on Paper and on Folia
before a release.

- `harness/`: the plugin `E2EHarness`. It registers a `coins` currency through the add-on's API
  (`AuctionCurrency`, no Vault needed) and the `/e2e` commands the bots use: `act <command>` runs a
  module command as a menu button would (`auctions:buy <id>`), `money`, `setmoney`, `listings`,
  `count`.
- `bot/run.js`: the scenario, with [mineflayer](https://github.com/PrismarineJS/mineflayer).
- Needs Java 21, Node 20+ and network access to PaperMC. The server stays in `e2e/work/` (ignored).
