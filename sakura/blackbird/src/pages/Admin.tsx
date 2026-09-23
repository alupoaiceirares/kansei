import { useCallback, useEffect, useState, type CSSProperties } from 'react';
import { Navigate } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { AircraftPhoto } from '../components/AircraftPhoto';
import { ReferencePicker } from '../components/ReferencePicker';
import { ApiError } from '../api/client';
import {
  deleteAircraftPhoto,
  fetchPhotoCandidates,
  fetchPhotoInfoForType,
  searchAircraftTypes,
  searchAirlines,
  searchAirports,
  selectCommonsPhoto,
} from '../api/tailwind';
import type { AircraftTypeOption, AirlineOption, AirportOption, CommonsCandidate, PhotoInfo } from '../api/types';
import { useSession } from '../session';
import { CARD, CARD_HEAD, DangerButton, PrimaryButton, Row } from './adminShared';
import { AircraftStringsSection, ImportSection, UsersSection } from './AdminTools';

type Section = 'Photos' | 'Reference data' | 'Aircraft strings' | 'Import flights' | 'Users';
type RefKind = 'Airports' | 'Airlines' | 'Aircraft types';

const SECTION: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  flex: '1 1 140px',
  height: 40,
  padding: '0 18px',
  borderRadius: 8,
  fontFamily: FONT_STACK,
  fontSize: 14,
  fontWeight: 600,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
  transition: 'all 140ms ease',
  border: 'none',
  background: 'transparent',
  color: COLORS.textMuted,
};

const SECTION_ON: CSSProperties = { ...SECTION, background: COLORS.raised, color: COLORS.text, boxShadow: 'inset 0 -2px 0 ' + COLORS.cyan };

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

const REF_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '92px minmax(0,1.4fr) minmax(0,1fr) auto',
  gap: 14,
  alignItems: 'center',
  padding: '13px 20px',
  borderBottom: '1px solid ' + COLORS.lineSoft,
  fontSize: 13.5,
};

/** Staff only. The role comes from the JWT, so a promotion needs a fresh login to take effect. */
export function AdminPage() {
  const { isAdmin } = useSession();
  const [section, setSection] = useState<Section>('Photos');

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <PageShell maxWidth={1180}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 640 }}>
          <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>Admin</h1>
          <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: COLORS.textMuted, textWrap: 'pretty' }}>
            Aircraft photos, reference data, aircraft strings, imports and users. Changes here affect everybody, and each one is
            published to the audit trail.
          </p>
        </div>
      </div>

      <div
        style={{
          display: 'flex',
          gap: 4,
          flexWrap: 'wrap',
          padding: 5,
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 11,
        }}
      >
        {(['Photos', 'Reference data', 'Aircraft strings', 'Import flights', 'Users'] as Section[]).map((item) => (
          <button key={item} type="button" onClick={() => setSection(item)} style={section === item ? SECTION_ON : SECTION}>
            {item}
          </button>
        ))}
      </div>

      {section === 'Photos' && <PhotosSection />}
      {section === 'Reference data' && <ReferenceSection />}
      {section === 'Aircraft strings' && <AircraftStringsSection />}
      {section === 'Import flights' && <ImportSection />}
      {section === 'Users' && <UsersSection />}
    </PageShell>
  );
}

function PhotosSection() {
  const [type, setType] = useState<AircraftTypeOption | null>(null);
  const [info, setInfo] = useState<PhotoInfo | null>(null);
  const [candidates, setCandidates] = useState<CommonsCandidate[]>([]);
  const [query, setQuery] = useState('');
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [busyTitle, setBusyTitle] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const typeSearch = useCallback((value: string) => searchAircraftTypes(value), []);

  useEffect(() => {
    setCandidates([]);
    setInfo(null);
    setSaved(null);
    if (!type) return;
    fetchPhotoInfoForType(type.id)
      .then(setInfo)
      .catch(() => undefined);
  }, [type]);

  const loadCandidates = async () => {
    if (!type) return;
    setLoadingCandidates(true);
    setError(null);
    try {
      setCandidates(await fetchPhotoCandidates(type.id, query.trim() || undefined));
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'Commons could not be reached.') : 'Commons could not be reached.');
    } finally {
      setLoadingCandidates(false);
    }
  };

  const choose = async (candidate: CommonsCandidate) => {
    if (!type) return;
    setBusyTitle(candidate.title);
    setError(null);
    try {
      setInfo(await selectCommonsPhoto(type.id, candidate.title));
      setSaved('Photo set for ' + type.name);
      setCandidates([]);
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'The photo could not be set.') : 'The photo could not be set.');
    } finally {
      setBusyTitle(null);
    }
  };

  const remove = async () => {
    if (!type) return;
    setDeleting(true);
    setError(null);
    try {
      await deleteAircraftPhoto(type.id);
      setInfo(await fetchPhotoInfoForType(type.id));
      setSaved('Photo removed, the silhouette is back');
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'The photo could not be removed.') : 'The photo could not be removed.');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: 13,
          padding: '16px 18px',
          borderRadius: 12,
          background: 'rgba(12,50,65,0.7)',
          border: '1px solid ' + COLORS.lineStrong,
        }}
      >
        <svg viewBox="0 0 16 16" width={15} height={15} style={{ marginTop: 2, flexShrink: 0 }} fill="none" stroke="#7FCFE8" strokeWidth={1.7} aria-hidden="true">
          <circle cx={8} cy={8} r={6.4} />
          <path d="M8 7.2 V11.4" strokeLinecap="round" />
          <circle cx={8} cy={4.9} r={0.9} fill="#7FCFE8" stroke="none" />
        </svg>
        <span style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.bodyOnCard }}>
          Every aircraft photo is sourced from Wikimedia Commons and must keep its author and licence. A type with no approved
          photo falls back to the silhouette, which is fine — a wrong or unlicensed photo is not.
        </span>
      </div>

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}
      {saved && <Banner tone="success" title={saved} />}

      <div style={CARD}>
        <div style={CARD_HEAD}>Pick a type</div>
        <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <ReferencePicker
            value={type}
            onChange={setType}
            search={typeSearch}
            labelOf={(item) => item.name}
            metaOf={(item) => [item.manufacturer, item.icaoCode, item.family].filter(Boolean).join(' · ')}
            placeholder="A321neo, 777-300ER, ATR 72-600"
          />

          {type && (
            <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
              <AircraftPhoto aircraftTypeId={type.id} width={248} height={152} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 11, minWidth: 0, flex: '1 1 240px' }}>
                <div style={{ fontSize: 17, fontWeight: 600 }}>{type.name}</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 5, fontSize: 12.5 }}>
                  <Row label="Photo" value={info?.hasPhoto ? 'Set' : 'None, showing the silhouette'} />
                  <Row label="Author" value={info?.author ?? '—'} />
                  <Row label="Licence" value={info?.license ?? '—'} tone={info?.hasPhoto && !info.license ? 'bad' : 'normal'} />
                  <Row label="Source" value={info?.origin ?? '—'} />
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 2 }}>
                  <PrimaryButton label="Find on Commons" onClick={loadCandidates} busy={loadingCandidates} />
                  {info?.hasPhoto && <DangerButton label="Remove photo" onClick={remove} busy={deleting} />}
                </div>
              </div>
            </div>
          )}

          {type && (
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
              <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Narrow the Commons search, e.g. Lufthansa A321neo"
                spellCheck={false}
                style={{
                  flex: '1 1 260px',
                  minWidth: 0,
                  height: 40,
                  padding: '0 13px',
                  borderRadius: 8,
                  background: COLORS.ground,
                  border: '1px solid ' + COLORS.lineStrong,
                  fontFamily: FONT_STACK,
                  fontSize: 13.5,
                  color: COLORS.text,
                  outline: 'none',
                }}
              />
            </div>
          )}
        </div>
      </div>

      {candidates.length > 0 && type && (
        <div style={CARD}>
          <div style={CARD_HEAD}>
            {candidates.length} candidates for {type.name}
          </div>
          <div style={{ padding: 20, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16 }}>
            {candidates.map((candidate) => (
              <div
                key={candidate.title}
                style={{ display: 'flex', flexDirection: 'column', background: '#082834', border: '1px solid ' + COLORS.line, borderRadius: 12, overflow: 'hidden' }}
              >
                <img
                  src={candidate.thumbUrl}
                  alt={candidate.title}
                  style={{ width: '100%', height: 150, objectFit: 'cover', borderBottom: '1px solid ' + COLORS.line }}
                />
                <div style={{ padding: '13px 15px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 12.5, color: COLORS.bodyOnCard, lineHeight: 1.5 }}>{candidate.title.replace('File:', '')}</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 11.5 }}>
                    <Row label="Author" value={candidate.author ?? 'Unknown'} small />
                    <Row
                      label="Licence"
                      value={candidate.license ?? 'No licence stated'}
                      tone={candidate.license ? 'normal' : 'bad'}
                      small
                    />
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 2 }}>
                    <PrimaryButton label="Use this one" onClick={() => choose(candidate)} busy={busyTitle === candidate.title} />
                    <a
                      href={candidate.pageUrl}
                      target="_blank"
                      rel="noreferrer noopener"
                      style={{ fontSize: 12.5, color: COLORS.cyan, alignSelf: 'center' }}
                    >
                      Open on Commons
                    </a>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function ReferenceSection() {
  const [kind, setKind] = useState<RefKind>('Airports');
  const [airport, setAirport] = useState<AirportOption | null>(null);
  const [airline, setAirline] = useState<AirlineOption | null>(null);
  const [type, setType] = useState<AircraftTypeOption | null>(null);

  const airportSearch = useCallback((value: string) => searchAirports(value, 25), []);
  const airlineSearch = useCallback((value: string) => searchAirlines(value, 25), []);
  const typeSearch = useCallback((value: string) => searchAircraftTypes(value, 25), []);

  const rows =
    kind === 'Airports'
      ? airport
        ? [{ code: airport.iata ?? airport.icao ?? '—', name: airport.name, extra: [airport.city, airport.countryCode].filter(Boolean).join(', ') }]
        : []
      : kind === 'Airlines'
        ? airline
          ? [{ code: airline.iata ?? airline.icao ?? '—', name: airline.name, extra: airline.active ? airline.countryCode ?? '' : (airline.countryCode ?? '') + ' — ceased' }]
          : []
        : type
          ? [{ code: type.icaoCode, name: type.name, extra: type.family ?? '' }]
          : [];

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
            <ReferencePicker
              value={airport}
              onChange={setAirport}
              search={airportSearch}
              labelOf={(item) => (item.iata ?? item.icao ?? '') + ' — ' + item.name}
              metaOf={(item) => [item.city, item.countryCode].filter(Boolean).join(', ')}
              placeholder="Search airports"
            />
          )}
          {kind === 'Airlines' && (
            <ReferencePicker
              value={airline}
              onChange={setAirline}
              search={airlineSearch}
              labelOf={(item) => item.name}
              metaOf={(item) => [item.iata, item.icao, item.active ? 'active' : 'ceased'].filter(Boolean).join(' · ')}
              placeholder="Search airlines"
            />
          )}
          {kind === 'Aircraft types' && (
            <ReferencePicker
              value={type}
              onChange={setType}
              search={typeSearch}
              labelOf={(item) => item.name}
              metaOf={(item) => [item.manufacturer, item.icaoCode, item.family].filter(Boolean).join(' · ')}
              placeholder="Search aircraft types"
            />
          )}
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {(['Airports', 'Airlines', 'Aircraft types'] as RefKind[]).map((item) => (
            <button key={item} type="button" onClick={() => setKind(item)} style={kind === item ? CHIP_ON : CHIP}>
              {item}
            </button>
          ))}
        </div>
      </div>

      <div style={CARD}>
        <div style={CARD_HEAD}>{kind}</div>
        <div
          style={{
            ...REF_GRID,
            background: '#082834',
            borderBottom: '1px solid ' + COLORS.line,
            fontSize: 11,
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            color: COLORS.textDim,
          }}
        >
          <span>Code</span>
          <span>Name</span>
          <span>{kind === 'Aircraft types' ? 'Family' : 'Country'}</span>
          <span />
        </div>
        {rows.length === 0 && (
          <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>
            Search above to open a row. Editing writes to the shared reference data, so every log sees it.
          </div>
        )}
        {rows.map((row) => (
          <div key={row.code} style={REF_GRID}>
            <span style={{ color: COLORS.text, fontWeight: 600, letterSpacing: '0.04em' }}>{row.code}</span>
            <span style={{ color: COLORS.text }}>{row.name}</span>
            <span style={{ color: COLORS.textMuted }}>{row.extra}</span>
            <span style={{ display: 'flex', gap: 7, justifyContent: 'flex-end', fontSize: 12.5, color: COLORS.textDim }}>
              Editing lands in a later pass
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

