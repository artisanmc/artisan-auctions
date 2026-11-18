import {
  BarList,
  type ChartSeries,
  type ModuleStatsProps,
  SortableTable,
  StatCard,
  StatsPanel,
  TimeChart,
  injectModuleStyles,
  statsBreakdown,
  statsEvents,
  statsQuery,
  statsSeries,
  useStatsRead,
} from "@artisan/editor-sdk";
import type { ReactElement } from "react";
import { type T, useT } from "./i18n";
import {
  EVENTS,
  MARKET_QUERY,
  type SaleLine,
  changeLabel,
  count,
  currencies,
  itemLabel,
  marketRows,
  saleLines,
  sum,
  topItems,
  volumes,
} from "./stats";
import { AUCTIONS_CSS } from "./styles";

/**
 * The Auctions tab of the Statistics space: one currency at a time (kept in the URL as the
 * tab's focus), since amounts of two currencies never add up. What happened over the period
 * comes from the house's events; what it holds now from a figure the server computes.
 */
export function AuctionsStatsTab(props: ModuleStatsProps): ReactElement {
  injectModuleStyles("auctions", AUCTIONS_CSS);
  const t = useT();
  const { serverId, period, player } = props;
  const traded = useStatsRead(`${serverId}|currencies|${period.from}|${period.to}|${player ?? ""}`, () =>
    statsBreakdown(serverId, { event: EVENTS.sold, from: period.from, to: period.to, player }, "currency"),
  );
  const market = useStatsRead(`${serverId}|market`, () =>
    statsQuery(serverId, MARKET_QUERY).then((r) => marketRows(r.rows)),
  );
  if (traded.error) return <p className="aa-stats-empty">{t("unavailable")}</p>;
  const offered = currencies(traded.data, market.data);
  const chosen = offered.includes(props.focus ?? "") ? (props.focus as string) : offered[0];
  if (!chosen) {
    return traded.loading ? <p className="aa-stats-empty">…</p> : <p className="aa-stats-empty">{t("noData")}</p>;
  }
  return (
    <div className="aa-stats">
      {offered.length > 1 && (
        <label className="aa-stats-pick">
          <span className="aa-h">{t("currency")}</span>
          <select className="select" value={chosen} onChange={(e) => props.onFocus(e.target.value)}>
            {offered.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
      )}
      <CurrencyStats key={chosen} {...props} currency={chosen} market={market} />
    </div>
  );
}

type Market = ReturnType<typeof useStatsRead<ReturnType<typeof marketRows>>>;

function CurrencyStats({
  serverId,
  period,
  player,
  compare,
  lang,
  currency,
  market,
}: ModuleStatsProps & { currency: string; market: Market }): ReactElement {
  const t = useT();
  const dims = { currency };
  const scope = `${serverId}|${currency}|${player ?? ""}`;
  const totals = (from: number, to: number) =>
    Promise.all([
      statsBreakdown(serverId, { event: EVENTS.sold, from, to, player, dims }, "item"),
      statsBreakdown(serverId, { event: EVENTS.listed, from, to, player, dims }, "type"),
      statsBreakdown(serverId, { event: EVENTS.expired, from, to, player, dims }, "type"),
      statsBreakdown(serverId, { event: EVENTS.revenue, from, to, player, dims }, "kind"),
    ]).then(([sold, listed, expired, revenue]) => ({ sold, listed, expired, revenue }));

  const now = useStatsRead(`${scope}|${period.from}|${period.to}`, () => totals(period.from, period.to));
  const before = useStatsRead(`${scope}|${period.previous.from}|${period.previous.to}|${compare}`, () =>
    compare ? totals(period.previous.from, period.previous.to) : Promise.resolve(undefined),
  );
  const series = useStatsRead(`${scope}|series|${period.from}|${period.to}|${period.by}|${compare}`, () =>
    Promise.all([
      statsSeries(serverId, { event: EVENTS.sold, from: period.from, to: period.to, player, dims }, period.by),
      compare
        ? statsSeries(
            serverId,
            { event: EVENTS.sold, from: period.previous.from, to: period.previous.to, player, dims },
            period.by,
          )
        : Promise.resolve(undefined),
    ]),
  );
  const recent = useStatsRead(`${scope}|recent|${period.from}|${period.to}`, () =>
    statsEvents(serverId, { event: EVENTS.sold, from: period.from, to: period.to, player, dims, limit: 50 }).then((r) =>
      saleLines(r.events),
    ),
  );

  const integer = (n: number) => new Intl.NumberFormat(lang).format(n);
  const money = (n: number) =>
    `${new Intl.NumberFormat(lang, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)} ${currency}`;
  const item = (key: string) => (key === "lot" ? t("lot") : key === "other" ? t("other") : itemLabel(key));
  const trend = (current: number, previous: number | undefined, goodWhenUp: boolean) => {
    const label = changeLabel(current, previous, lang);
    if (label === null) return undefined;
    return { label, tone: label.startsWith("▲") === goodWhenUp ? ("positive" as const) : ("negative" as const) };
  };

  if (now.error) return <p className="aa-stats-empty">{t("unavailable")}</p>;
  const d = now.data;
  const p = before.data;
  const volume = sum(d?.sold);
  const sales = count(d?.sold);
  const card = (label: string, value: number | undefined, format: (n: number) => string, prev?: number, good = true, sub?: string) => (
    <StatCard
      label={label}
      value={value === undefined ? "…" : format(value)}
      sub={sub ?? t("overPeriod")}
      trend={value === undefined ? undefined : trend(value, prev, good)}
    />
  );

  const row = market.data?.find((r) => r.currency === currency);
  const marketNotice =
    market.error === undefined
      ? null
      : t(market.error === "server_offline" ? "offline" : market.error === "unknown_query" ? "notComputed" : "unavailable");

  const labels = (series.data?.[0] ?? []).map((pt) =>
    new Intl.DateTimeFormat(lang, period.by === "hour" ? { hour: "2-digit", day: "numeric" } : { day: "numeric", month: "short" }).format(
      new Date(pt.start * 1000),
    ),
  );
  const chart: ChartSeries[] = [
    {
      id: "now",
      label: t("volumeChart"),
      color: "var(--chart-1)",
      points: volumes(series.data?.[0]).map((y, i) => ({ label: labels[i] ?? "", y })),
    },
  ];
  if (series.data?.[1]) {
    chart.push({
      id: "previous",
      label: t("previous"),
      color: "var(--chart-2)",
      dashed: true,
      points: volumes(series.data[1]).map((y, i) => ({ label: labels[i] ?? "", y })),
    });
  }

  return (
    <>
      <div className="aa-stats-cards">
        {card(t("volume"), d && volume, money, p && sum(p.sold))}
        {card(t("sales"), d && sales, integer, p && count(p.sold))}
        {card(t("average"), d && (sales ? volume / sales : 0), money, p && (count(p.sold) ? sum(p.sold) / count(p.sold) : undefined))}
        {card(t("listed"), d && count(d.listed), integer, p && count(p.listed))}
        {card(t("expired"), d && count(d.expired), integer, p && count(p.expired), false)}
        {card(t("revenue"), d && sum(d.revenue), money, p && sum(p.revenue), true, t("revenueSub"))}
      </div>

      <StatsPanel title={t("now")} hint={t("nowHint")}>
        {marketNotice ? (
          <p className="aa-hint">{marketNotice}</p>
        ) : (
          <div className="aa-stats-cards">
            <StatCard label={t("activeSales")} value={row ? integer(row.sales) : "…"} />
            <StatCard
              label={t("activeAuctions")}
              value={row ? integer(row.auctions) : "…"}
              sub={row ? t("withBids", { count: integer(row.bids) }) : undefined}
            />
            <StatCard label={t("onSale")} value={row ? money(row.saleValue + row.bidValue) : "…"} sub={t("onSaleSub")} />
            <StatCard
              label={t("waiting")}
              value={row ? money(row.moneyWaiting) : "…"}
              sub={row ? t("waitingSub", { items: integer(row.itemsWaiting) }) : undefined}
            />
          </div>
        )}
      </StatsPanel>

      <StatsPanel title={t("volumeChart")} hint={t("overPeriod")}>
        <TimeChart series={chart} label={t("volumeChart")} formatY={money} />
      </StatsPanel>

      <StatsPanel title={t("topItems")} hint={t("topItemsHint")}>
        {sales === 0 ? (
          <p className="aa-stats-empty">{t("noData")}</p>
        ) : (
          <BarList
            rows={topItems(d?.sold).map((r) => ({
              id: r.value,
              label: item(r.value),
              value: r.sum,
              caption: `${money(r.sum)} · ${r.count === 1 ? t("salesCountOne") : t("salesCount", { count: integer(r.count) })}`,
            }))}
          />
        )}
      </StatsPanel>

      <StatsPanel title={t("recent")} hint={t("recentHint")}>
        {(recent.data ?? []).length === 0 ? (
          <p className="aa-stats-empty">{recent.loading ? "…" : t("noData")}</p>
        ) : (
          <SortableTable<SaleLine>
            rows={recent.data ?? []}
            rowId={(r) => String(r.id)}
            initialSort="at"
            columns={saleColumns(t, lang, item, integer, money)}
          />
        )}
      </StatsPanel>
    </>
  );
}

function saleColumns(
  t: T,
  lang: string,
  item: (key: string) => string,
  integer: (n: number) => string,
  money: (n: number) => string,
) {
  const when = new Intl.DateTimeFormat(lang, { dateStyle: "short", timeStyle: "short" });
  return [
    { id: "at", label: t("when"), value: (r: SaleLine) => r.at, render: (r: SaleLine) => when.format(new Date(r.at)) },
    { id: "item", label: t("item"), value: (r: SaleLine) => item(r.item) },
    { id: "quantity", label: t("quantity"), align: "right" as const, value: (r: SaleLine) => r.quantity, render: (r: SaleLine) => integer(r.quantity) },
    { id: "price", label: t("price"), align: "right" as const, value: (r: SaleLine) => r.price, render: (r: SaleLine) => money(r.price) },
    { id: "seller", label: t("seller"), value: (r: SaleLine) => r.seller },
    { id: "buyer", label: t("buyer"), value: (r: SaleLine) => r.buyer },
  ];
}
