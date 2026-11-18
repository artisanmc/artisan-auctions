import { describe, expect, it } from "vitest";
import { changeLabel, currencies, itemLabel, marketRows, saleLines, sum, topItems } from "./stats";

const row = (value: string, count: number, s: number) => ({ value, count, sum: s, min: 0, max: 0 });

describe("auction statistics", () => {
  it("reads the market figure", () => {
    const [r] = marketRows([{ currency: "vault", sales: 3, auctions: 1, bids: 1, sale_value: 120.5, bid_value: 40, money_waiting: 9, items_waiting: 2 }]);
    expect(r).toEqual({ currency: "vault", sales: 3, auctions: 1, bids: 1, saleValue: 120.5, bidValue: 40, moneyWaiting: 9, itemsWaiting: 2 });
    expect(marketRows([{ currency: "x", sales: "3" }])[0]?.sales).toBe(0);
  });

  it("offers the busiest currency first, then the ones only held now", () => {
    const market = marketRows([{ currency: "gems" }, { currency: "vault" }]);
    expect(currencies([row("vault", 2, 10), row("tokens", 9, 500)], market)).toEqual(["tokens", "vault", "gems"]);
    expect(currencies(undefined, undefined)).toEqual([]);
  });

  it("compares with the previous period", () => {
    expect(changeLabel(150, 100, "en")).toBe("▲ 50%");
    expect(changeLabel(50, 100, "en")).toBe("▼ 50%");
    expect(changeLabel(5, 0, "en")).toBeNull();
    expect(changeLabel(5, undefined, "en")).toBeNull();
  });

  it("names items and ranks them by money", () => {
    expect(itemLabel("diamond_sword")).toBe("Diamond sword");
    expect(topItems([row("dirt", 50, 5), row("elytra", 1, 900), row("stone", 3, 5)], 2).map((r) => r.value)).toEqual(["elytra", "dirt"]);
    expect(sum([row("a", 1, 2), row("b", 1, 3)])).toBe(5);
  });

  it("turns raw sale rows into lines", () => {
    const [line] = saleLines([
      {
        id: 7,
        player: "1234567890ab",
        event: "auctions:sold",
        at: 1000,
        dimensions: { item: "elytra", currency: "vault", type: "sale" },
        value: 900,
        details: { seller_name: "Alice", buyer_name: "", quantity: "1" },
      },
    ]);
    expect(line).toEqual({ id: 7, at: 1000, item: "elytra", quantity: 1, price: 900, seller: "Alice", buyer: "12345678" });
  });
});
