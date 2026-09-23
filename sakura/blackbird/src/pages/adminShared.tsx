import type { CSSProperties } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { Spinner } from '../components/Spinner';

// Building blocks every admin section shares, kept out of the page so the sections can live in their own files.

export const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

export const CARD_HEAD: CSSProperties = { padding: '16px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 };

export const INPUT: CSSProperties = {
  height: 40,
  padding: '0 13px',
  borderRadius: 8,
  background: COLORS.ground,
  border: '1px solid ' + COLORS.lineStrong,
  fontFamily: FONT_STACK,
  fontSize: 13.5,
  color: COLORS.text,
  outline: 'none',
};

export function PrimaryButton({
  label,
  onClick,
  busy = false,
  disabled = false,
}: {
  label: string;
  onClick: () => void;
  busy?: boolean;
  disabled?: boolean;
}) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 34,
      padding: '0 15px',
      borderRadius: 8,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 12.5,
      fontWeight: 700,
      cursor: disabled ? 'default' : 'pointer',
      opacity: disabled ? 0.5 : 1,
      whiteSpace: 'nowrap',
    },
    { background: disabled ? COLORS.orange : COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={onClick} disabled={busy || disabled} {...hover}>
      {busy && <Spinner size={12} color={COLORS.textOnOrange} />}
      {label}
    </button>
  );
}

export function DangerButton({ label, onClick, busy = false }: { label: string; onClick: () => void; busy?: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 34,
      padding: '0 15px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.dangerBorder,
      background: 'transparent',
      color: COLORS.dangerText,
      fontFamily: FONT_STACK,
      fontSize: 12.5,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.dangerBg },
  );
  return (
    <button type="button" onClick={onClick} disabled={busy} {...hover}>
      {busy && <Spinner size={12} color={COLORS.dangerText} />}
      {label}
    </button>
  );
}

export function Row({
  label,
  value,
  tone = 'normal',
  width = 76,
  small = false,
}: {
  label: string;
  value: string;
  tone?: 'normal' | 'bad';
  width?: number;
  small?: boolean;
}) {
  return (
    <div style={{ display: 'flex', gap: 10, fontSize: small ? 11.5 : 12.5 }}>
      <span style={{ color: COLORS.textDim, width, flexShrink: 0 }}>{label}</span>
      <span style={{ color: tone === 'bad' ? COLORS.dangerText : COLORS.bodyOnCard }}>{value}</span>
    </div>
  );
}

export function errorText(cause: unknown, fallback: string): string {
  if (cause && typeof cause === 'object' && 'detail' in cause) {
    const detail = (cause as { detail: string | null }).detail;
    if (detail) return detail;
  }
  return fallback;
}
