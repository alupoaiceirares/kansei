// Every colour, type step and control style the app is built from, taken from the design foundations.
// Screens compose these into inline style objects, only keyframes and pseudo-state rules live in index.css.
import type { CSSProperties } from 'react';

export const COLORS = {
  ground: '#06212B',
  surface: '#0A2C39',
  surfaceOverlay: 'rgba(10,44,57,0.86)',
  surfaceOverlayStrong: 'rgba(10,44,57,0.9)',
  raised: '#0E3644',
  line: '#114453',
  lineSoft: '#0F3B49',
  lineStrong: '#17596E',
  cyan: '#2BB3D9',
  cyanBright: '#7FE3FF',
  cyanFill: '#1B6D84',
  orange: '#FF7A18',
  orangeHover: '#FF9445',
  text: '#E4F4FA',
  textMuted: '#8FB8C6',
  textDim: '#6FA6BA',
  textFaint: '#4E7688',
  textOnOrange: '#141A22',
  success: '#3DD68C',
  warning: '#FFC94D',
  danger: '#FF6B6B',
  dangerText: '#FF9A9A',
  dangerBg: '#2A1418',
  dangerBorder: '#7A3038',
  texture: '#0C3441',
  plane: '#12475A',
  laterBg: '#221A2E',
  laterBorder: '#4A3A5E',
  laterText: '#B9A7D0',
  bodyOnCard: '#B7D4DE',
  skeleton: '#14414F',
} as const;

export const FONT_STACK = "'Space Grotesk', ui-sans-serif, system-ui, sans-serif";

export const PAGE_MAX_WIDTH = 1280;
export const CONTENT_MAX_WIDTH = 1180;
export const PAGE_PADDING_X = 32;

export const pageTitle: CSSProperties = {
  margin: 0,
  fontSize: 33,
  fontWeight: 600,
  letterSpacing: '-0.02em',
  lineHeight: 1.05,
};

export const sectionHeading: CSSProperties = { margin: 0, fontSize: 26, fontWeight: 600, letterSpacing: '-0.01em' };

export const cardTitle: CSSProperties = { fontSize: 16, fontWeight: 600 };

export const overline: CSSProperties = {
  fontSize: 11,
  fontWeight: 500,
  letterSpacing: '0.14em',
  textTransform: 'uppercase',
  color: COLORS.textDim,
};

export const bodyText: CSSProperties = { fontSize: 14, lineHeight: 1.6, color: COLORS.text };

export const mutedText: CSSProperties = { fontSize: 13, color: COLORS.textMuted };

export const statNumber: CSSProperties = {
  fontSize: 26,
  fontWeight: 600,
  fontVariantNumeric: 'tabular-nums',
  lineHeight: 1.05,
};

export const card: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: `1px solid ${COLORS.line}`,
  borderRadius: 14,
};

const buttonBase: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 8,
  height: 40,
  padding: '0 20px',
  borderRadius: 8,
  fontFamily: FONT_STACK,
  fontSize: 14,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
  textDecoration: 'none',
  border: '1px solid transparent',
  transition: 'background-color 140ms ease, color 140ms ease, border-color 140ms ease',
};

// One orange action per screen, everything secondary is a cyan ghost button.
export const primaryButton: CSSProperties = {
  ...buttonBase,
  background: COLORS.orange,
  color: COLORS.textOnOrange,
  fontWeight: 700,
};

export const primaryButtonHover: CSSProperties = { background: COLORS.orangeHover };

export const secondaryButton: CSSProperties = {
  ...buttonBase,
  background: 'transparent',
  border: `1px solid ${COLORS.cyan}`,
  color: COLORS.cyan,
  fontWeight: 600,
};

export const secondaryButtonHover: CSSProperties = { background: COLORS.raised };

export const tertiaryButton: CSSProperties = {
  ...buttonBase,
  background: 'transparent',
  border: `1px solid ${COLORS.lineStrong}`,
  color: COLORS.textMuted,
  fontWeight: 500,
};

export const tertiaryButtonHover: CSSProperties = { color: COLORS.text, borderColor: COLORS.cyan };

export const ghostButton: CSSProperties = {
  ...buttonBase,
  background: 'transparent',
  padding: '0 14px',
  color: COLORS.textMuted,
  fontWeight: 500,
};

export const ghostButtonHover: CSSProperties = { color: COLORS.text, background: COLORS.raised };

export const dangerButton: CSSProperties = {
  ...buttonBase,
  background: 'transparent',
  border: `1px solid ${COLORS.dangerBorder}`,
  color: COLORS.danger,
  fontWeight: 600,
};

export const dangerButtonHover: CSSProperties = { background: '#2E1418' };

export const disabledButton: CSSProperties = {
  ...buttonBase,
  background: '#123240',
  color: COLORS.textFaint,
  fontWeight: 700,
  cursor: 'default',
};

export const workingButton: CSSProperties = {
  ...buttonBase,
  background: '#B85610',
  color: '#FFE2CB',
  fontWeight: 700,
  gap: 9,
  cursor: 'default',
};

export const input: CSSProperties = {
  height: 42,
  padding: '0 13px',
  border: `1px solid ${COLORS.lineStrong}`,
  borderRadius: 8,
  background: COLORS.ground,
  fontFamily: FONT_STACK,
  fontSize: 14,
  color: COLORS.text,
  outline: 'none',
};

export const inputLabel: CSSProperties = { fontSize: 12, fontWeight: 500, color: COLORS.textMuted };

export const pill: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '3px 9px',
  borderRadius: 999,
  fontSize: 10.5,
  fontWeight: 600,
  letterSpacing: '0.06em',
  whiteSpace: 'nowrap',
  border: '1px solid transparent',
};

export const VISIBILITY_PILL = {
  PUBLIC: { ...pill, background: '#123F2C', borderColor: '#2C6B4C', color: '#7FE0AE' },
  FRIENDS: { ...pill, background: COLORS.raised, borderColor: COLORS.lineStrong, color: '#7FCFE8' },
  PRIVATE: { ...pill, background: COLORS.laterBg, borderColor: COLORS.laterBorder, color: COLORS.laterText },
} as const satisfies Record<string, CSSProperties>;

export const upcomingPill: CSSProperties = { ...pill, background: '#2E1D0C', borderColor: '#7A4A16', color: '#FFB067' };
export const canceledPill: CSSProperties = { ...pill, background: COLORS.dangerBg, borderColor: COLORS.dangerBorder, color: COLORS.dangerText };

export const laterPill: CSSProperties = {
  ...pill,
  background: 'transparent',
  borderColor: COLORS.laterBorder,
  color: COLORS.laterText,
  fontSize: 9.5,
  letterSpacing: '0.08em',
};

export const cargoBox: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 22,
  height: 20,
  borderRadius: 5,
  background: '#3A2A12',
  border: '1px solid #6B5220',
  color: COLORS.warning,
  fontSize: 10.5,
  fontWeight: 700,
};

export const BANNER_TONE = {
  error: { bg: COLORS.dangerBg, border: '#6B2830', dot: COLORS.danger },
  warning: { bg: '#2E250C', border: '#6B5A20', dot: COLORS.warning },
  success: { bg: '#0F2E20', border: '#2C6B4C', dot: COLORS.success },
  info: { bg: '#0C3241', border: COLORS.lineStrong, dot: '#7FCFE8' },
} as const;

export type BannerTone = keyof typeof BANNER_TONE;
