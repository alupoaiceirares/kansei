import { useEffect, useRef, useState } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';
import { Spinner } from './Spinner';

type Props<T> = {
  value: T | null;
  onChange: (value: T | null) => void;
  search: (query: string) => Promise<T[]>;
  labelOf: (item: T) => string;
  metaOf?: (item: T) => string;
  placeholder: string;
  invalid?: boolean;
};

/**
 * Type-ahead over the reference tables. These are our own rows, not the flight data provider,
 * so searching while typing costs nothing.
 */
export function ReferencePicker<T>({ value, onChange, search, labelOf, metaOf, placeholder, invalid = false }: Props<T>) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<T[]>([]);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || query.trim().length < 2) {
      setResults([]);
      return;
    }
    let live = true;
    setBusy(true);
    const timer = window.setTimeout(() => {
      search(query.trim())
        .then((found) => live && setResults(found))
        .catch(() => live && setResults([]))
        .finally(() => live && setBusy(false));
    }, 220);
    return () => {
      live = false;
      window.clearTimeout(timer);
    };
  }, [query, open, search]);

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const inputStyle = {
    height: 42,
    padding: '0 13px',
    borderRadius: 8,
    background: COLORS.ground,
    border: '1px solid ' + (invalid ? '#C2454F' : COLORS.lineStrong),
    fontFamily: FONT_STACK,
    fontSize: 14,
    color: COLORS.text,
    outline: 'none',
    boxSizing: 'border-box' as const,
    width: '100%',
  };

  return (
    <div ref={boxRef} style={{ position: 'relative' }}>
      <input
        type="text"
        value={value ? labelOf(value) : query}
        placeholder={placeholder}
        spellCheck={false}
        onFocus={() => setOpen(true)}
        onChange={(event) => {
          if (value) onChange(null);
          setQuery(event.target.value);
          setOpen(true);
        }}
        style={inputStyle}
      />

      {open && !value && query.trim().length >= 2 && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: 0,
            right: 0,
            zIndex: 30,
            maxHeight: 232,
            overflowY: 'auto',
            scrollbarWidth: 'thin',
            padding: 6,
            background: COLORS.raised,
            border: '1px solid ' + COLORS.lineStrong,
            borderRadius: 10,
            boxShadow: '0 20px 44px rgba(0,0,0,0.5)',
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          {busy && results.length === 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '9px 10px', fontSize: 13, color: COLORS.textMuted }}>
              <Spinner size={13} />
              Searching
            </div>
          )}
          {!busy && results.length === 0 && (
            <div style={{ padding: '9px 10px', fontSize: 13, color: COLORS.textMuted }}>Nothing matches that.</div>
          )}
          {results.map((item, index) => (
            <button
              key={index}
              type="button"
              onClick={() => {
                onChange(item);
                setQuery('');
                setOpen(false);
              }}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-start',
                gap: 2,
                padding: '8px 10px',
                borderRadius: 7,
                border: 'none',
                background: 'transparent',
                color: COLORS.text,
                fontFamily: FONT_STACK,
                fontSize: 13.5,
                textAlign: 'left',
                cursor: 'pointer',
              }}
            >
              <span>{labelOf(item)}</span>
              {metaOf && <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{metaOf(item)}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
