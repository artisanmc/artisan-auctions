import { type FrontendModule, SDK_VERSION } from "@artisan/editor-sdk";
import { AuctionsStatsTab } from "./AuctionsStatsTab";
import { TAB_LABELS } from "./i18n";

/**
 * The editor view of ArtisanAuctions (module id `auctions`): the Auctions tab of the server's
 * Statistics space. The menus, dialogs, texts and settings are edited by the editor's own
 * views, from what the plugin declares.
 */
const auctionsModule: FrontendModule = {
  id: "auctions",
  displayName: "Auctions",
  schemaVersion: "auctions/v1",
  sdk: SDK_VERSION,
  sidebar: { icon: () => null, label: "Auctions", to: "/m/auctions" },
  routes: [],
  stats: { label: TAB_LABELS, component: AuctionsStatsTab },
};

export default auctionsModule;
