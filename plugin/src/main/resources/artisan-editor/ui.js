import { createModuleI18n as Y, injectModuleStyles as J, useStatsRead as A, statsBreakdown as $, statsQuery as X, statsSeries as z, statsEvents as ee, StatsPanel as I, StatCard as N, TimeChart as te, BarList as ne, SortableTable as se, SDK_VERSION as ae } from "@artisan/editor-sdk";
import { jsx as r, jsxs as k, Fragment as oe } from "react/jsx-runtime";
const M = {
  tab: "Auctions",
  currency: "Currency",
  noData: "Nothing sold or listed yet. The figures fill in as players use /ah.",
  unavailable: "The statistics cannot be read right now.",
  offline: "The server is off: what the house holds now shows when it is back.",
  notComputed: "This server runs an ArtisanAuctions without these figures.",
  overPeriod: "over the period",
  volume: "Volume traded",
  sales: "Sales",
  average: "Average sale",
  listed: "New listings",
  expired: "Unsold",
  revenue: "House revenue",
  revenueSub: "listing fees and sale tax",
  now: "Now",
  nowHint: "What the house holds at this moment",
  activeSales: "Fixed-price listings",
  activeAuctions: "Auctions running",
  withBids: "{{count}} with a bid",
  onSale: "Value on sale",
  onSaleSub: "fixed prices plus best bids",
  waiting: "In claims boxes",
  waitingSub: "{{items}} items not collected",
  volumeChart: "Volume traded",
  previous: "previous period",
  topItems: "Items that move the most money",
  topItemsHint: "By volume over the period",
  salesCount: "{{count}} sales",
  salesCountOne: "1 sale",
  lot: "Mixed lot",
  other: "Other items",
  recent: "Latest sales",
  recentHint: "Up to 50, newest first",
  when: "When",
  item: "Item",
  quantity: "Qty",
  price: "Price",
  seller: "Seller",
  buyer: "Buyer"
}, R = {
  tab: "Hôtel des ventes",
  currency: "Monnaie",
  noData: "Rien de vendu ni d'annoncé pour l'instant. Les chiffres se remplissent quand les joueurs utilisent /ah.",
  unavailable: "Les statistiques ne peuvent pas être lues pour l'instant.",
  offline: "Le serveur est éteint : ce que l'hôtel détient s'affichera à son retour.",
  notComputed: "Ce serveur fait tourner un ArtisanAuctions sans ces chiffres.",
  overPeriod: "sur la période",
  volume: "Volume échangé",
  sales: "Ventes",
  average: "Vente moyenne",
  listed: "Nouvelles annonces",
  expired: "Invendus",
  revenue: "Recettes de l'hôtel",
  revenueSub: "frais de mise en vente et taxe",
  now: "En ce moment",
  nowHint: "Ce que l'hôtel détient à cet instant",
  activeSales: "Annonces à prix fixe",
  activeAuctions: "Enchères en cours",
  withBids: "{{count}} avec une offre",
  onSale: "Valeur en vente",
  onSaleSub: "prix fixes et meilleures offres",
  waiting: "Dans les récupérations",
  waitingSub: "{{items}} objets non récupérés",
  volumeChart: "Volume échangé",
  previous: "période précédente",
  topItems: "Objets qui font circuler le plus d'argent",
  topItemsHint: "Par volume sur la période",
  salesCount: "{{count}} ventes",
  salesCountOne: "1 vente",
  lot: "Lot mélangé",
  other: "Autres objets",
  recent: "Dernières ventes",
  recentHint: "Jusqu'à 50, les plus récentes d'abord",
  when: "Quand",
  item: "Objet",
  quantity: "Qté",
  price: "Prix",
  seller: "Vendeur",
  buyer: "Acheteur"
}, U = {
  tab: "Auktionshaus",
  currency: "Währung",
  noData: "Noch nichts verkauft oder eingestellt. Die Zahlen füllen sich, sobald Spieler /ah nutzen.",
  unavailable: "Die Statistiken können gerade nicht gelesen werden.",
  offline: "Der Server ist aus: Der aktuelle Bestand erscheint, sobald er wieder läuft.",
  notComputed: "Auf diesem Server läuft ein ArtisanAuctions ohne diese Zahlen.",
  overPeriod: "im Zeitraum",
  volume: "Handelsvolumen",
  sales: "Verkäufe",
  average: "Durchschnittlicher Verkauf",
  listed: "Neue Angebote",
  expired: "Unverkauft",
  revenue: "Einnahmen des Hauses",
  revenueSub: "Einstellgebühren und Verkaufssteuer",
  now: "Aktuell",
  nowHint: "Was das Haus gerade hält",
  activeSales: "Festpreisangebote",
  activeAuctions: "Laufende Auktionen",
  withBids: "{{count}} mit Gebot",
  onSale: "Wert im Verkauf",
  onSaleSub: "Festpreise plus Höchstgebote",
  waiting: "In Abholfächern",
  waitingSub: "{{items}} Gegenstände nicht abgeholt",
  volumeChart: "Handelsvolumen",
  previous: "vorheriger Zeitraum",
  topItems: "Gegenstände mit dem meisten Umsatz",
  topItemsHint: "Nach Volumen im Zeitraum",
  salesCount: "{{count}} Verkäufe",
  salesCountOne: "1 Verkauf",
  lot: "Gemischtes Los",
  other: "Andere Gegenstände",
  recent: "Letzte Verkäufe",
  recentHint: "Bis zu 50, die neuesten zuerst",
  when: "Wann",
  item: "Gegenstand",
  quantity: "Anz.",
  price: "Preis",
  seller: "Verkäufer",
  buyer: "Käufer"
}, G = {
  tab: "Casa de subastas",
  currency: "Moneda",
  noData: "Aún no se ha vendido ni publicado nada. Las cifras se llenan cuando los jugadores usan /ah.",
  unavailable: "Las estadísticas no se pueden leer ahora mismo.",
  offline: "El servidor está apagado: lo que guarda la casa se mostrará cuando vuelva.",
  notComputed: "Este servidor ejecuta un ArtisanAuctions sin estas cifras.",
  overPeriod: "en el periodo",
  volume: "Volumen negociado",
  sales: "Ventas",
  average: "Venta media",
  listed: "Anuncios nuevos",
  expired: "Sin vender",
  revenue: "Ingresos de la casa",
  revenueSub: "comisiones de publicación e impuesto de venta",
  now: "Ahora",
  nowHint: "Lo que la casa guarda en este momento",
  activeSales: "Anuncios a precio fijo",
  activeAuctions: "Subastas en curso",
  withBids: "{{count}} con puja",
  onSale: "Valor en venta",
  onSaleSub: "precios fijos más mejores pujas",
  waiting: "En los buzones de recogida",
  waitingSub: "{{items}} objetos sin recoger",
  volumeChart: "Volumen negociado",
  previous: "periodo anterior",
  topItems: "Objetos que mueven más dinero",
  topItemsHint: "Por volumen en el periodo",
  salesCount: "{{count}} ventas",
  salesCountOne: "1 venta",
  lot: "Lote mixto",
  other: "Otros objetos",
  recent: "Últimas ventas",
  recentHint: "Hasta 50, las más recientes primero",
  when: "Cuándo",
  item: "Objeto",
  quantity: "Cant.",
  price: "Precio",
  seller: "Vendedor",
  buyer: "Comprador"
}, ie = { en: M.tab, fr: R.tab, de: U.tab, es: G.tab }, re = { en: M, fr: R, de: U, es: G }, Q = Y(re), w = {
  listed: "auctions:listed",
  sold: "auctions:sold",
  expired: "auctions:expired",
  revenue: "auctions:revenue"
}, le = "auctions:market", S = (t) => typeof t == "number" && Number.isFinite(t) ? t : 0;
function ue(t) {
  return t.map((e) => ({
    currency: String(e.currency ?? ""),
    sales: S(e.sales),
    auctions: S(e.auctions),
    bids: S(e.bids),
    saleValue: S(e.sale_value),
    bidValue: S(e.bid_value),
    moneyWaiting: S(e.money_waiting),
    itemsWaiting: S(e.items_waiting)
  }));
}
function ce(t, e) {
  const a = [...t ?? []].sort((o, u) => u.sum - o.sum).map((o) => o.value);
  for (const o of e ?? []) a.includes(o.currency) || a.push(o.currency);
  return a.filter((o) => o !== "");
}
const g = (t) => (t ?? []).reduce((e, a) => e + a.count, 0), V = (t) => (t ?? []).reduce((e, a) => e + a.sum, 0);
function de(t, e, a) {
  if (e === void 0 || e === 0) return null;
  const o = (t - e) / e, u = new Intl.NumberFormat(a, { style: "percent", maximumFractionDigits: 0 }).format(Math.abs(o));
  return `${o >= 0 ? "▲" : "▼"} ${u}`;
}
function me(t) {
  const e = t.replace(/^minecraft:/, "").split("_").filter(Boolean).join(" ");
  return e ? e.charAt(0).toUpperCase() + e.slice(1) : t;
}
function ve(t, e = 10) {
  return [...t ?? []].sort((a, o) => o.sum - a.sum || o.count - a.count).slice(0, e);
}
function he(t) {
  return t.map((e) => {
    const a = e.details ?? {};
    return {
      id: e.id,
      at: e.at,
      item: e.dimensions.item ?? "",
      quantity: Number(a.quantity ?? 1) || 1,
      price: e.value,
      seller: String(a.seller_name ?? ""),
      buyer: String(a.buyer_name ?? "") || (e.player ?? "").slice(0, 8)
    };
  });
}
const W = (t) => (t ?? []).map((e) => e.sum), pe = `
.aa-stats { display: flex; flex-direction: column; gap: 16px; color: var(--text); }
.aa-stats-pick { display: flex; align-items: center; gap: 10px; }
.aa-stats-pick .select { height: 32px; padding: 5px 10px; font-size: 13px; min-width: 160px; }
.aa-h { font-size: 11px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; color: var(--text-mut); }
.aa-stats-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
.aa-stats-empty { padding: 24px 0; text-align: center; color: var(--text-mut); font-size: 13px; }
.aa-hint { color: var(--text-faint); font-size: 11.5px; }
`;
function fe(t) {
  J("auctions", pe);
  const e = Q(), { serverId: a, period: o, player: u } = t, p = A(
    `${a}|currencies|${o.from}|${o.to}|${u ?? ""}`,
    () => $(a, { event: w.sold, from: o.from, to: o.to, player: u }, "currency")
  ), i = A(
    `${a}|market`,
    () => X(a, le).then((v) => ue(v.rows))
  );
  if (p.error) return /* @__PURE__ */ r("p", { className: "aa-stats-empty", children: e("unavailable") });
  const n = ce(p.data, i.data), m = n.includes(t.focus ?? "") ? t.focus : n[0];
  return m ? /* @__PURE__ */ k("div", { className: "aa-stats", children: [
    n.length > 1 && /* @__PURE__ */ k("label", { className: "aa-stats-pick", children: [
      /* @__PURE__ */ r("span", { className: "aa-h", children: e("currency") }),
      /* @__PURE__ */ r("select", { className: "select", value: m, onChange: (v) => t.onFocus(v.target.value), children: n.map((v) => /* @__PURE__ */ r("option", { value: v, children: v }, v)) })
    ] }),
    /* @__PURE__ */ r(be, { ...t, currency: m, market: i }, m)
  ] }) : p.loading ? /* @__PURE__ */ r("p", { className: "aa-stats-empty", children: "…" }) : /* @__PURE__ */ r("p", { className: "aa-stats-empty", children: e("noData") });
}
function be({
  serverId: t,
  period: e,
  player: a,
  compare: o,
  lang: u,
  currency: p,
  market: i
}) {
  const n = Q(), m = { currency: p }, v = `${t}|${p}|${a ?? ""}`, P = (s, l) => Promise.all([
    $(t, { event: w.sold, from: s, to: l, player: a, dims: m }, "item"),
    $(t, { event: w.listed, from: s, to: l, player: a, dims: m }, "type"),
    $(t, { event: w.expired, from: s, to: l, player: a, dims: m }, "type"),
    $(t, { event: w.revenue, from: s, to: l, player: a, dims: m }, "kind")
  ]).then(([C, y, q, L]) => ({ sold: C, listed: y, expired: q, revenue: L })), E = A(`${v}|${e.from}|${e.to}`, () => P(e.from, e.to)), Z = A(
    `${v}|${e.previous.from}|${e.previous.to}|${o}`,
    () => o ? P(e.previous.from, e.previous.to) : Promise.resolve(void 0)
  ), D = A(
    `${v}|series|${e.from}|${e.to}|${e.by}|${o}`,
    () => Promise.all([
      z(t, { event: w.sold, from: e.from, to: e.to, player: a, dims: m }, e.by),
      o ? z(
        t,
        { event: w.sold, from: e.previous.from, to: e.previous.to, player: a, dims: m },
        e.by
      ) : Promise.resolve(void 0)
    ])
  ), j = A(
    `${v}|recent|${e.from}|${e.to}`,
    () => ee(t, { event: w.sold, from: e.from, to: e.to, player: a, dims: m, limit: 50 }).then(
      (s) => he(s.events)
    )
  ), f = (s) => new Intl.NumberFormat(u).format(s), b = (s) => `${new Intl.NumberFormat(u, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(s)} ${p}`, T = (s) => s === "lot" ? n("lot") : s === "other" ? n("other") : me(s), K = (s, l, C) => {
    const y = de(s, l, u);
    if (y !== null)
      return { label: y, tone: y.startsWith("▲") === C ? "positive" : "negative" };
  };
  if (E.error) return /* @__PURE__ */ r("p", { className: "aa-stats-empty", children: n("unavailable") });
  const h = E.data, c = Z.data, _ = V(h?.sold), H = g(h?.sold), x = (s, l, C, y, q = !0, L) => /* @__PURE__ */ r(
    N,
    {
      label: s,
      value: l === void 0 ? "…" : C(l),
      sub: L ?? n("overPeriod"),
      trend: l === void 0 ? void 0 : K(l, y, q)
    }
  ), d = i.data?.find((s) => s.currency === p), B = i.error === void 0 ? null : n(i.error === "server_offline" ? "offline" : i.error === "unknown_query" ? "notComputed" : "unavailable"), F = (D.data?.[0] ?? []).map(
    (s) => new Intl.DateTimeFormat(u, e.by === "hour" ? { hour: "2-digit", day: "numeric" } : { day: "numeric", month: "short" }).format(
      new Date(s.start * 1e3)
    )
  ), O = [
    {
      id: "now",
      label: n("volumeChart"),
      color: "var(--chart-1)",
      points: W(D.data?.[0]).map((s, l) => ({ label: F[l] ?? "", y: s }))
    }
  ];
  return D.data?.[1] && O.push({
    id: "previous",
    label: n("previous"),
    color: "var(--chart-2)",
    dashed: !0,
    points: W(D.data[1]).map((s, l) => ({ label: F[l] ?? "", y: s }))
  }), /* @__PURE__ */ k(oe, { children: [
    /* @__PURE__ */ k("div", { className: "aa-stats-cards", children: [
      x(n("volume"), h && _, b, c && V(c.sold)),
      x(n("sales"), h && H, f, c && g(c.sold)),
      x(n("average"), h && (H ? _ / H : 0), b, c && (g(c.sold) ? V(c.sold) / g(c.sold) : void 0)),
      x(n("listed"), h && g(h.listed), f, c && g(c.listed)),
      x(n("expired"), h && g(h.expired), f, c && g(c.expired), !1),
      x(n("revenue"), h && V(h.revenue), b, c && V(c.revenue), !0, n("revenueSub"))
    ] }),
    /* @__PURE__ */ r(I, { title: n("now"), hint: n("nowHint"), children: B ? /* @__PURE__ */ r("p", { className: "aa-hint", children: B }) : /* @__PURE__ */ k("div", { className: "aa-stats-cards", children: [
      /* @__PURE__ */ r(N, { label: n("activeSales"), value: d ? f(d.sales) : "…" }),
      /* @__PURE__ */ r(
        N,
        {
          label: n("activeAuctions"),
          value: d ? f(d.auctions) : "…",
          sub: d ? n("withBids", { count: f(d.bids) }) : void 0
        }
      ),
      /* @__PURE__ */ r(N, { label: n("onSale"), value: d ? b(d.saleValue + d.bidValue) : "…", sub: n("onSaleSub") }),
      /* @__PURE__ */ r(
        N,
        {
          label: n("waiting"),
          value: d ? b(d.moneyWaiting) : "…",
          sub: d ? n("waitingSub", { items: f(d.itemsWaiting) }) : void 0
        }
      )
    ] }) }),
    /* @__PURE__ */ r(I, { title: n("volumeChart"), hint: n("overPeriod"), children: /* @__PURE__ */ r(te, { series: O, label: n("volumeChart"), formatY: b }) }),
    /* @__PURE__ */ r(I, { title: n("topItems"), hint: n("topItemsHint"), children: H === 0 ? /* @__PURE__ */ r("p", { className: "aa-stats-empty", children: n("noData") }) : /* @__PURE__ */ r(
      ne,
      {
        rows: ve(h?.sold).map((s) => ({
          id: s.value,
          label: T(s.value),
          value: s.sum,
          caption: `${b(s.sum)} · ${s.count === 1 ? n("salesCountOne") : n("salesCount", { count: f(s.count) })}`
        }))
      }
    ) }),
    /* @__PURE__ */ r(I, { title: n("recent"), hint: n("recentHint"), children: (j.data ?? []).length === 0 ? /* @__PURE__ */ r("p", { className: "aa-stats-empty", children: j.loading ? "…" : n("noData") }) : /* @__PURE__ */ r(
      se,
      {
        rows: j.data ?? [],
        rowId: (s) => String(s.id),
        initialSort: "at",
        columns: ge(n, u, T, f, b)
      }
    ) })
  ] });
}
function ge(t, e, a, o, u) {
  const p = new Intl.DateTimeFormat(e, { dateStyle: "short", timeStyle: "short" });
  return [
    { id: "at", label: t("when"), value: (i) => i.at, render: (i) => p.format(new Date(i.at)) },
    { id: "item", label: t("item"), value: (i) => a(i.item) },
    { id: "quantity", label: t("quantity"), align: "right", value: (i) => i.quantity, render: (i) => o(i.quantity) },
    { id: "price", label: t("price"), align: "right", value: (i) => i.price, render: (i) => u(i.price) },
    { id: "seller", label: t("seller"), value: (i) => i.seller },
    { id: "buyer", label: t("buyer"), value: (i) => i.buyer }
  ];
}
const we = {
  id: "auctions",
  displayName: "Auctions",
  schemaVersion: "auctions/v1",
  sdk: ae,
  sidebar: { icon: () => null, label: "Auctions", to: "/m/auctions" },
  routes: [],
  stats: { label: ie, component: fe }
}, xe = { ...we, sdk: "1.23.0" };
export {
  xe as default
};
