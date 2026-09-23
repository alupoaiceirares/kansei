import { useCallback, useEffect, useState, type CSSProperties } from 'react';
import { COLORS, FONT_STACK } from '../design/tokens';
import { Banner } from '../components/Banner';
import { ReferencePicker } from '../components/ReferencePicker';
import {
  fetchCountries,
  saveAircraftType,
  saveAirline,
  saveAirport,
  saveCountry,
  searchAircraftTypes,
  searchAirlines,
  searchAirports,
} from '../api/tailwind';
import type { AircraftTypeRecord, AirlineRecord, AirportRecord, BodyType, CountryRecord, EngineType } from '../api/types';
import { CARD, CARD_HEAD, INPUT, PrimaryButton, errorText } from './adminShared';

type Kind = 'Airports' | 'Airlines' | 'Aircraft types' | 'Countries';

// One editable field. Every value lives as text in the draft and is converted when saved
type Field = {
  key: string;
  label: string;
  placeholder?: string;
  options?: string[];
  checkbox?: boolean;
  required?: boolean;
  upper?: boolean;
};

const FIELDS: Record<Kind, Field[]> = {
  Airports: [
    { key: 'iata', label: 'IATA', placeholder: 'OTP', upper: true },
    { key: 'icao', label: 'ICAO', placeholder: 'LROP', upper: true },
    { key: 'name', label: 'Name', placeholder: 'Henri Coanda International', required: true },
    { key: 'city', label: 'City', placeholder: 'Bucharest' },
    { key: 'countryCode', label: 'Country code', placeholder: 'RO', required: true, upper: true },
    { key: 'latitude', label: 'Latitude', placeholder: '44.5711', required: true },
    { key: 'longitude', label: 'Longitude', placeholder: '26.085', required: true },
    { key: 'timeZone', label: 'Time zone', placeholder: 'Europe/Bucharest' },
    { key: 'airportType', label: 'Type', placeholder: 'large_airport' },
  ],
  Airlines: [
    { key: 'icao', label: 'ICAO', placeholder: 'ROT', required: true, upper: true },
    { key: 'iata', label: 'IATA', placeholder: 'RO', upper: true },
    { key: 'name', label: 'Name', placeholder: 'Tarom', required: true },
    { key: 'countryCode', label: 'Country code', placeholder: 'RO', upper: true },
    { key: 'active', label: 'Still flying', checkbox: true },
  ],
  'Aircraft types': [
    { key: 'icaoCode', label: 'ICAO designator', placeholder: 'A20N', required: true, upper: true },
    { key: 'manufacturer', label: 'Manufacturer', placeholder: 'Airbus', required: true },
    { key: 'model', label: 'Model', placeholder: 'A320neo', required: true },
    { key: 'name', label: 'Display name', placeholder: 'Airbus A320neo' },
    { key: 'family', label: 'Family', placeholder: 'A320 family' },
    { key: 'bodyType', label: 'Body', options: ['NARROW', 'WIDE', 'REGIONAL', 'TURBOPROP', 'BUSINESS', 'OTHER'], required: true },
    { key: 'engineType', label: 'Engines', options: ['JET', 'TURBOPROP', 'PISTON', 'ELECTRIC'], required: true },
    { key: 'engineCount', label: 'Engine count', placeholder: '2' },
    { key: 'wakeCategory', label: 'Wake category', options: ['', 'L', 'M', 'H', 'J'] },
  ],
  Countries: [
    { key: 'code', label: 'Code', placeholder: 'RO', required: true, upper: true },
    { key: 'name', label: 'Name', placeholder: 'Romania', required: true },
    { key: 'continent', label: 'Continent', options: ['AF', 'AN', 'AS', 'EU', 'NA', 'OC', 'SA'], required: true },
  ],
};

type Draft = Record<string, string | boolean>;

const CHIP: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  height: 32,
  padding: '0 13px',
  borderRadius: 999,
  fontFamily: FONT_STACK,
  fontSize: 12.5,
  fontWeight: 500,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
  border: '1px solid ' + COLORS.line,
  background: 'transparent',
  color: COLORS.textMuted,
};

const CHIP_ON: CSSProperties = { ...CHIP, borderColor: COLORS.cyan, background: COLORS.raised, color: COLORS.text };

function emptyDraft(kind: Kind): Draft {
  const draft: Draft = {};
  for (const field of FIELDS[kind]) draft[field.key] = field.checkbox ? true : field.options ? field.options[0] : '';
  return draft;
}

function toDraft(kind: Kind, row: object): Draft {
  const draft = emptyDraft(kind);
  const values = row as Record<string, unknown>;
  for (const field of FIELDS[kind]) {
    const value = values[field.key];
    if (field.checkbox) draft[field.key] = value !== false;
    else draft[field.key] = value === null || value === undefined ? '' : String(value);
  }
  return draft;
}

const text = (draft: Draft, key: string) => String(draft[key] ?? '').trim();
const optional = (draft: Draft, key: string) => text(draft, key) || null;
const number = (draft: Draft, key: string) => (text(draft, key) === '' ? null : Number(text(draft, key)));

/**
 * Add and edit the shared reference data. Every save writes to what every log is built from and goes to the
 * audit trail, so the form shows exactly the row that will be written.
 */
export function ReferenceSection() {
  const [kind, setKind] = useState<Kind>('Airports');
  const [countries, setCountries] = useState<CountryRecord[]>([]);
  // id (or country code) of the row being edited, null while creating a new one
  const [editing, setEditing] = useState<{ key: number | string | null; draft: Draft } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const airportSearch = useCallback((value: string) => searchAirports(value, 25) as Promise<AirportRecord[]>, []);
  const airlineSearch = useCallback((value: string) => searchAirlines(value, 25), []);
  const typeSearch = useCallback((value: string) => searchAircraftTypes(value, 25) as Promise<AircraftTypeRecord[]>, []);
  const countrySearch = useCallback(
    (value: string) => {
      const q = value.toLowerCase();
      return Promise.resolve(countries.filter((c) => c.code.toLowerCase() === q || c.name.toLowerCase().includes(q)).slice(0, 25));
    },
    [countries],
  );

  useEffect(() => {
    fetchCountries()
      .then(setCountries)
      .catch(() => setCountries([]));
  }, []);

  const switchKind = (next: Kind) => {
    setKind(next);
    setEditing(null);
    setError(null);
    setSaved(null);
  };

  const open = (key: number | string | null, draft: Draft) => {
    setEditing({ key, draft });
    setError(null);
    setSaved(null);
  };

  const set = (key: string, value: string | boolean) =>
    setEditing((current) => (current ? { ...current, draft: { ...current.draft, [key]: value } } : current));

  const save = async () => {
    if (!editing) return;
    const { key, draft } = editing;
    const missing = FIELDS[kind].filter((field) => field.required && !text(draft, field.key)).map((field) => field.label);
    if (missing.length) {
      setError('Fill in ' + missing.join(', '));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      let label: string;
      if (kind === 'Airports') {
        const row = await saveAirport(key as number | null, {
          iata: optional(draft, 'iata'),
          icao: optional(draft, 'icao'),
          name: text(draft, 'name'),
          city: optional(draft, 'city'),
          countryCode: text(draft, 'countryCode'),
          latitude: Number(text(draft, 'latitude')),
          longitude: Number(text(draft, 'longitude')),
          timeZone: optional(draft, 'timeZone'),
          airportType: optional(draft, 'airportType'),
        });
        setEditing({ key: row.id, draft: toDraft(kind, row) });
        label = row.name;
      } else if (kind === 'Airlines') {
        const row = await saveAirline(key as number | null, {
          icao: text(draft, 'icao'),
          iata: optional(draft, 'iata'),
          name: text(draft, 'name'),
          countryCode: optional(draft, 'countryCode'),
          active: draft.active === true,
        });
        setEditing({ key: row.id, draft: toDraft(kind, row) });
        label = row.name;
      } else if (kind === 'Aircraft types') {
        const row = await saveAircraftType(key as number | null, {
          icaoCode: text(draft, 'icaoCode'),
          manufacturer: text(draft, 'manufacturer'),
          model: text(draft, 'model'),
          name: optional(draft, 'name') ?? '',
          family: optional(draft, 'family'),
          bodyType: text(draft, 'bodyType') as BodyType,
          engineType: text(draft, 'engineType') as EngineType,
          engineCount: number(draft, 'engineCount'),
          wakeCategory: optional(draft, 'wakeCategory'),
        });
        setEditing({ key: row.id, draft: toDraft(kind, row) });
        label = row.name;
      } else {
        const row = await saveCountry(key as string | null, {
          code: text(draft, 'code'),
          name: text(draft, 'name'),
          continent: text(draft, 'continent'),
        });
        setCountries((current) => [...current.filter((c) => c.code !== row.code), row].sort((a, b) => a.name.localeCompare(b.name)));
        setEditing({ key: row.code, draft: toDraft(kind, row) });
        label = row.name;
      }
      setSaved((key === null ? 'Added ' : 'Saved ') + label);
    } catch (cause) {
      setError(errorText(cause, 'The row could not be saved.'));
    } finally {
      setSaving(false);
    }
  };

  const creating = editing !== null && editing.key === null;
  const singular = kind === 'Countries' ? 'country' : kind === 'Aircraft types' ? 'aircraft type' : kind.slice(0, -1).toLowerCase();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 10,
          alignItems: 'center',
          padding: '14px 16px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 12,
        }}
      >
        <div style={{ flex: '1 1 260px', minWidth: 0 }}>
          {kind === 'Airports' && (
            <ReferencePicker<AirportRecord>
              value={null}
              onChange={(row) => row && open(row.id, toDraft(kind, row))}
              search={airportSearch}
              labelOf={(item) => (item.iata ?? item.icao ?? '') + ', ' + item.name}
              metaOf={(item) => [item.city, item.countryCode].filter(Boolean).join(', ')}
              placeholder="Search airports to edit"
            />
          )}
          {kind === 'Airlines' && (
            <ReferencePicker<AirlineRecord>
              value={null}
              onChange={(row) => row && open(row.id, toDraft(kind, row))}
              search={airlineSearch}
              labelOf={(item) => item.name}
              metaOf={(item) => [item.iata, item.icao, item.active ? 'active' : 'ceased'].filter(Boolean).join(' · ')}
              placeholder="Search airlines to edit"
            />
          )}
          {kind === 'Aircraft types' && (
            <ReferencePicker<AircraftTypeRecord>
              value={null}
              onChange={(row) => row && open(row.id, toDraft(kind, row))}
              search={typeSearch}
              labelOf={(item) => item.name}
              metaOf={(item) => [item.manufacturer, item.icaoCode, item.family].filter(Boolean).join(' · ')}
              placeholder="Search aircraft types to edit"
            />
          )}
          {kind === 'Countries' && (
            <ReferencePicker<CountryRecord>
              value={null}
              onChange={(row) => row && open(row.code, toDraft(kind, row))}
              search={countrySearch}
              labelOf={(item) => item.name}
              metaOf={(item) => item.code + ' · ' + item.continent}
              placeholder="Search countries to edit"
            />
          )}
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {(['Airports', 'Airlines', 'Aircraft types', 'Countries'] as Kind[]).map((item) => (
            <button key={item} type="button" onClick={() => switchKind(item)} style={kind === item ? CHIP_ON : CHIP}>
              {item}
            </button>
          ))}
        </div>
        <PrimaryButton label={'New ' + singular} onClick={() => open(null, emptyDraft(kind))} />
      </div>

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}
      {saved && <Banner tone="success" title={saved} />}

      <div style={CARD}>
        <div style={CARD_HEAD}>{editing ? (creating ? 'New ' + singular : 'Edit ' + singular) : kind}</div>
        {!editing && (
          <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted, lineHeight: 1.6 }}>
            Search above to open a row, or add a new one. Editing writes to the shared reference data, so every log sees it
            straight away, and each save goes to the audit trail.
          </div>
        )}
        {editing && (
          <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(210px, 1fr))', gap: 14 }}>
              {FIELDS[kind].map((field) => (
                <label key={field.key} style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>
                    {field.label}
                    {field.required && <span style={{ color: COLORS.orange }}> *</span>}
                  </span>
                  {field.checkbox ? (
                    <input
                      type="checkbox"
                      checked={editing.draft[field.key] === true}
                      onChange={(event) => set(field.key, event.target.checked)}
                      style={{ width: 18, height: 18, accentColor: COLORS.cyan }}
                    />
                  ) : field.options ? (
                    <select value={String(editing.draft[field.key])} onChange={(event) => set(field.key, event.target.value)} style={INPUT}>
                      {field.options.map((option) => (
                        <option key={option} value={option}>
                          {option || '—'}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      type="text"
                      value={String(editing.draft[field.key])}
                      placeholder={field.placeholder}
                      spellCheck={false}
                      // A country code is the key of its row, it cannot change once saved
                      disabled={kind === 'Countries' && field.key === 'code' && !creating}
                      onChange={(event) => set(field.key, field.upper ? event.target.value.toUpperCase() : event.target.value)}
                      style={INPUT}
                    />
                  )}
                </label>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
              <PrimaryButton label={creating ? 'Add ' + singular : 'Save changes'} onClick={save} busy={saving} />
              <button
                type="button"
                onClick={() => setEditing(null)}
                style={{ background: 'none', border: 'none', fontFamily: FONT_STACK, fontSize: 13, color: COLORS.textMuted, cursor: 'pointer' }}
              >
                Close
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
