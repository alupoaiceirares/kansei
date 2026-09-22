import type { ReactNode } from 'react';
import { BANNER_TONE, COLORS, type BannerTone } from '../design/tokens';

type Props = {
  tone: BannerTone;
  title: string;
  children?: ReactNode;
  /** Colour overrides for the purple "already in your log" treatment. */
  bg?: string;
  border?: string;
  dot?: string;
};

/** The dotted notice block used above forms and detail screens. */
export function Banner({ tone, title, children, bg, border, dot }: Props) {
  const palette = BANNER_TONE[tone];
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 13,
        padding: '17px 19px',
        borderRadius: 12,
        background: bg ?? palette.bg,
        border: '1px solid ' + (border ?? palette.border),
      }}
    >
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: dot ?? palette.dot, marginTop: 7, flexShrink: 0 }} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
        <div style={{ fontSize: 14.5, fontWeight: 600, color: dot ?? palette.dot }}>{title}</div>
        {children && <div style={{ fontSize: 13.5, lineHeight: 1.6, color: COLORS.bodyOnCard }}>{children}</div>}
      </div>
    </div>
  );
}
