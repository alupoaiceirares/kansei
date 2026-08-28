// Canonical order: header gradient, hexagon lattice, flower petals and timeline markers all cycle through this same sequence
export const PALETTE = [
  "oklch(62% 0.13 200)", // blue
  "oklch(48% 0.18 25)", // dark red
  "oklch(65% 0.20 340)", // pink
  "oklch(70% 0.15 80)", // dark yellow
  "oklch(42% 0.12 150)", // dark green
] as const;

export const PAGE_BG = "oklch(96.5% 0.009 70)";
export const PAGE_BG_FAINT = "oklch(96.5% 0.009 70 / 0.72)";
export const FOOTER_BG = "oklch(93% 0.012 70)";
export const TEXT_BODY = "oklch(22% 0.01 60)";
export const TEXT_SECONDARY = "oklch(50% 0.01 60)";
export const TEXT_MUTED = "oklch(58% 0.01 60)";
export const HAIRLINE = "rgba(0,0,0,0.08)";
export const INPUT_BORDER = "rgba(0,0,0,0.13)";
export const CARD_BORDER = "rgba(0,0,0,0.07)";
export const CARD_SHADOW = "0 18px 48px rgba(0,0,0,0.07)";
export const MODAL_SHADOW = "0 24px 60px rgba(0,0,0,0.18)";
export const FOCUS_COLOR = "oklch(65% 0.20 340)"; // pink

export const HEADER_GRADIENT = `linear-gradient(90deg,
  ${PALETTE[0]} 0%, ${PALETTE[0]} 24%,
  ${PALETTE[1]} 38%, ${PALETTE[2]} 52%,
  ${PALETTE[3]} 64%, ${PALETTE[4]} 76%,
  ${PALETTE[4]} 100%)`;

export const PRIMARY_BUTTON_GRADIENT = `linear-gradient(90deg, ${PALETTE[0]}, ${PALETTE[2]} 55%, ${PALETTE[1]})`;

export const FONT_DISPLAY = "'Shippori Mincho', serif";
export const FONT_UI = "'Inter', system-ui, sans-serif";
