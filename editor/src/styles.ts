/**
 * The tab's own styles (`aa-` prefix): a bundle loaded at runtime cannot count on the
 * editor's Tailwind utilities, only on its design-system classes and tokens.
 */
export const AUCTIONS_CSS = `
.aa-stats { display: flex; flex-direction: column; gap: 16px; color: var(--text); }
.aa-stats-pick { display: flex; align-items: center; gap: 10px; }
.aa-stats-pick .select { height: 32px; padding: 5px 10px; font-size: 13px; min-width: 160px; }
.aa-h { font-size: 11px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; color: var(--text-mut); }
.aa-stats-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
.aa-stats-empty { padding: 24px 0; text-align: center; color: var(--text-mut); font-size: 13px; }
.aa-hint { color: var(--text-faint); font-size: 11.5px; }
`;
