import type { CSSProperties } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';

const SEG: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  height: 34,
  padding: '0 6px',
  borderRadius: 7,
  fontFamily: FONT_STACK,
  fontSize: 12,
  fontWeight: 600,
  letterSpacing: '0.03em',
  cursor: 'pointer',
  textAlign: 'center',
  whiteSpace: 'nowrap',
  transition: 'all 140ms ease',
  border: '1px solid ' + COLORS.line,
  background: 'transparent',
  color: COLORS.textMuted,
};

const ON: CSSProperties = { ...SEG, border: '1px solid ' + COLORS.cyanBright, background: COLORS.raised, color: COLORS.text };

// A selected visibility option is the one place that fills, so the choice reads at a glance.
const VIS_ON: CSSProperties = { ...SEG, border: '1px solid ' + COLORS.cyanBright, background: COLORS.cyanFill, color: '#FFFFFF' };

export type Option<T extends string> = { value: T; label: string };

type Props<T extends string> = {
  options: Option<T>[];
  value: T | null;
  onChange: (value: T | null) => void;
  columns?: number;
  /** Visibility uses the filled selected state instead of the raised one. */
  visibility?: boolean;
  /** Clicking the selected option clears it, for the optional fields. */
  clearable?: boolean;
};

export function Segmented<T extends string>({ options, value, onChange, columns, visibility = false, clearable = false }: Props<T>) {
  return (
    <div
      style={{
        display: columns ? 'grid' : 'flex',
        gridTemplateColumns: columns ? `repeat(${columns}, minmax(0,1fr))` : undefined,
        gap: columns ? 5 : 7,
        flexWrap: columns ? undefined : 'wrap',
      }}
    >
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            onClick={() => onChange(selected && clearable ? null : option.value)}
            style={selected ? (visibility ? VIS_ON : ON) : SEG}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
