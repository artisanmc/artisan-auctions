import type { StatsBreakdownRow, StatsEventRow, StatsPoint } from "@artisan/editor-sdk";

/** The Stat events ArtisanAuctions records (`MarketStats` on the server). Public: never renamed. */
export const EVENTS = {
  listed: "auctions:listed",
  sold: "auctions:sold",
  bid: "auctions:bid",
  expired: "auctions:expired",
  cancelled: "auctions:cancelled",
  revenue: "auctions:revenue",
} as const;

/** What the house holds now, computed by the server (`MarketFigures`). */
export const MARKET_QUERY = "auctions:market";

/** A row of `auctions:market`: one currency. */
export interface MarketRow {
  currency: string;
  sales: number;
  auctions: number;
  bids: number;
  saleValue: number;
  bidValue: number;
  moneyWaiting: number;
  itemsWaiting: number;
}

const num = (value: unknown): number => (typeof value === "number" && Number.isFinite(value) ? value : 0);

export function marketRows(rows: readonly Record<string, unknown>[]): MarketRow[] {
  return rows.map((r) => ({
    currency: String(r.currency ?? ""),
    sales: num(r.sales),
    auctions: num(r.auctions),
    bids: num(r.bids),
    saleValue: num(r.sale_value),
    bidValue: num(r.bid_value),
    moneyWaiting: num(r.money_waiting),
    itemsWaiting: num(r.items_waiting),
  }));
}

/**
 * The currencies to offer, the busiest first: those traded over the period (by volume), then
 * those the house holds listings or money in now.
 */
export function currencies(traded: readonly StatsBreakdownRow[] | undefined, market: readonly MarketRow[] | undefined): string[] {
  const out = [...(traded ?? [])].sort((a, b) => b.sum - a.sum).map((r) => r.value);
  for (const row of market ?? []) if (!out.includes(row.currency)) out.push(row.currency);
  return out.filter((c) => c !== "");
}

export const count = (rows: readonly StatsBreakdownRow[] | undefined): number =>
  (rows ?? []).reduce((n, r) => n + r.count, 0);

export const sum = (rows: readonly StatsBreakdownRow[] | undefined): number =>
  (rows ?? []).reduce((n, r) => n + r.sum, 0);

/** `▲ 12 %` against the previous period; null when there is nothing to compare with. */
export function changeLabel(current: number, previous: number | undefined, lang: string): string | null {
  if (previous === undefined || previous === 0) return null;
  const ratio = (current - previous) / previous;
  const percent = new Intl.NumberFormat(lang, { style: "percent", maximumFractionDigits: 0 }).format(Math.abs(ratio));
  return `${ratio >= 0 ? "▲" : "▼"} ${percent}`;
}

/** `diamond_sword` → `Diamond sword`; `lot` and `other` are translated by the caller. */
export function itemLabel(item: string): string {
  const words = item.replace(/^minecraft:/, "").split("_").filter(Boolean).join(" ");
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : item;
}

/** The items that moved the most money, the largest first, at most [limit]. */
export function topItems(rows: readonly StatsBreakdownRow[] | undefined, limit = 10): StatsBreakdownRow[] {
  return [...(rows ?? [])].sort((a, b) => b.sum - a.sum || b.count - a.count).slice(0, limit);
}

/** A sale as the recent sales table shows it. */
export interface SaleLine {
  id: number;
  at: number;
  item: string;
  quantity: number;
  price: number;
  seller: string;
  buyer: string;
}

export function saleLines(rows: readonly StatsEventRow[]): SaleLine[] {
  return rows.map((r) => {
    const details = (r.details ?? {}) as Record<string, unknown>;
    return {
      id: r.id,
      at: r.at,
      item: r.dimensions.item ?? "",
      quantity: Number(details.quantity ?? 1) || 1,
      price: r.value,
      seller: String(details.seller_name ?? ""),
      buyer: String(details.buyer_name ?? "") || (r.player ?? "").slice(0, 8),
    };
  });
}

/** The sums of a series, bucket by bucket. */
export const volumes = (points: readonly StatsPoint[] | undefined): number[] => (points ?? []).map((p) => p.sum);
