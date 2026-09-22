import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import { COLORS, FONT_STACK, pill } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { Segmented } from '../components/Segmented';
import { FlightFlags, aircraftLabel, routeOf } from '../components/FlightFlags';
import { RouteMap } from '../map/RouteMap';
import { ApiError } from '../api/client';
import { fetchJourney, setJourneyVisibility, updateJourney } from '../api/tailwind';
import type { Journey, Visibility } from '../api/types';
import { hasUnflownGap, journeyCountries, journeyDistance, journeyStops, journeyVisibility, legsOf, monthLabel, routeLine } from '../journeys';
import { formatDate, formatKm, formatMinutes } from '../format';

const VISIBILITIES = [
  { value: 'PUBLIC' as const, label: 'PUBLIC' },
  { value: 'FRIENDS' as const, label: 'FRIENDS' },
  { value: 'PRIVATE' as const, label: 'PRIVATE' },
];

const VIS_HINT: Record<Visibility, string> = {
  PUBLIC: 'Every flight in this journey becomes visible to anyone who opens your profile.',
  FRIENDS: 'Every flight in this journey becomes visible to your friends only.',
  PRIVATE: 'Every flight in this journey becomes yours alone. They still count in your own numbers.',
};

const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

const LEG_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '26px 92px 72px minmax(0,1fr) minmax(0,0.9fr) 84px auto',
  gap: 14,
  alignItems: 'center',
  padding: '13px 20px',
  borderBottom: '1px solid ' + COLORS.lineSoft,
  textDecoration: 'none',
  color: 'inherit',
};

function durationMinutes(startIso: string | null, endIso: string | null): number | null {
  if (!startIso || !endIso) return null;
  const minutes = Math.round((new Date(endIso).getTime() - new Date(startIso).getTime()) / 60000);
  return minutes > 0 ? minutes : null;
}

function LegRow({ index, journeyId, legId, children }: { index: number; journeyId: number; legId: number; children: React.ReactNode }) {
  const hover = useHoverStyle(LEG_GRID, { background: COLORS.raised });
  return (
    <Link to={'/flights/' + legId} state={{ journeyId }} {...hover}>
      <span
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 26,
          height: 26,
          borderRadius: '50%',
          background: COLORS.raised,
          border: '1px solid ' + COLORS.lineStrong,
          fontSize: 12,
          fontWeight: 600,
          color: COLORS.textMuted,
          flexShrink: 0,
        }}
      >
        {index}
      </span>
      {children}
    </Link>
  );
}

export function JourneyDetailPage() {
  const { journeyId } = useParams();
  const id = Number(journeyId);

  const [journey, setJourney] = useState<Journey | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState(false);
  const [title, setTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [applying, setApplying] = useState(false);

  const load = () =>
    fetchJourney(id)
      .then((loaded) => {
        setJourney(loaded);
        setTitle(loaded.titleIsCustom ? loaded.title : '');
      })
      .catch(() => setError('This journey could not be loaded.'))
      .finally(() => setLoading(false));

  useEffect(() => {
    setLoading(true);
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const legs = useMemo(() => (journey ? legsOf(journey) : []), [journey]);

  if (loading) {
    return (
      <PageShell maxWidth={1180}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading the journey
        </div>
      </PageShell>
    );
  }

  if (!journey) {
    return (
      <PageShell maxWidth={1180}>
        <Banner tone="error" title="Journey not found">
          {error ?? 'That journey is not in your log any more.'}
        </Banner>
        <Link to="/journeys" style={{ fontSize: 13.5 }}>
          ← Journeys
        </Link>
      </PageShell>
    );
  }

  const upcoming = legs.some((leg) => leg.flight.upcoming);
  const gap = hasUnflownGap(journey);
  const distance = journeyDistance(journey);
  const countries = journeyCountries(journey);
  const current = journeyVisibility(journey);

  const totalMinutes = legs.reduce((sum, leg) => {
    const minutes = durationMinutes(
      leg.flight.departureActualUtc ?? leg.flight.departureScheduledUtc,
      leg.flight.arrivalActualUtc ?? leg.flight.arrivalScheduledUtc,
    );
    return sum + (minutes ?? 0);
  }, 0);

  const rename = async () => {
    setSaving(true);
    setError(null);
    try {
      await updateJourney(journey.id, { title: title.trim() || null });
      setRenaming(false);
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'The journey could not be renamed.') : 'The journey could not be renamed.');
    } finally {
      setSaving(false);
    }
  };

  const applyVisibility = async (visibility: Visibility) => {
    setApplying(true);
    setError(null);
    try {
      await setJourneyVisibility(journey.id, visibility);
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'The visibility could not be changed.') : 'The visibility could not be changed.');
    } finally {
      setApplying(false);
    }
  };

  const stats = [
    { label: 'Distance', value: formatKm(distance), note: upcoming ? 'upcoming legs not counted' : 'across the whole trip', color: COLORS.text },
    { label: 'Flights', value: String(legs.length), note: monthLabel(journey), color: COLORS.text },
    {
      label: 'Time in air',
      value: totalMinutes > 0 ? formatMinutes(totalMinutes) : '—',
      note: totalMinutes > 0 ? 'from the recorded times' : 'no times recorded',
      color: COLORS.cyan,
    },
    {
      label: 'Countries',
      value: String(countries.filter((country) => country.kind === 'visited').length),
      note: countries.filter((country) => country.kind === 'transit').length + ' in transit only',
      color: COLORS.cyan,
    },
  ];

  return (
    <PageShell maxWidth={1180}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
          <Link to="/journeys" style={{ fontSize: 13, color: COLORS.textMuted }}>
            ← Journeys
          </Link>
          {renaming ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <input
                type="text"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder={journey.title}
                style={{
                  height: 44,
                  padding: '0 13px',
                  borderRadius: 8,
                  background: COLORS.ground,
                  border: '1px solid ' + COLORS.lineStrong,
                  fontFamily: FONT_STACK,
                  fontSize: 18,
                  fontWeight: 600,
                  color: COLORS.text,
                  outline: 'none',
                  minWidth: 260,
                }}
              />
              <SaveTitleButton saving={saving} onClick={rename} />
              <button
                type="button"
                onClick={() => setRenaming(false)}
                style={{ background: 'none', border: 'none', fontFamily: FONT_STACK, fontSize: 13, color: COLORS.textMuted, cursor: 'pointer' }}
              >
                Cancel
              </button>
              <span style={{ fontSize: 12, color: COLORS.textDim, flexBasis: '100%' }}>
                Leave it blank and the journey goes back to being named after its route.
              </span>
            </div>
          ) : (
            <h1 style={{ margin: 0, fontSize: 34, fontWeight: 600, letterSpacing: '-0.02em' }}>{journey.title}</h1>
          )}
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 14, color: COLORS.textMuted }}>{routeLine(journey)}</span>
            <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
              {upcoming && (
                <span style={{ ...pill, padding: '4px 10px', fontSize: 11, background: '#2E1D0C', borderColor: '#7A4A16', color: '#FFB067' }}>
                  UPCOMING
                </span>
              )}
              {gap && (
                <span
                  style={{ ...pill, padding: '4px 10px', fontSize: 11, background: COLORS.raised, borderColor: COLORS.lineStrong, color: COLORS.textMuted }}
                >
                  RETURN ELSEWHERE
                </span>
              )}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
          <RenameButton onClick={() => setRenaming((open) => !open)} />
          <AddLegButton />
        </div>
      </div>

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
        {stats.map((stat) => (
          <div
            key={stat.label}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              padding: '18px 20px',
              background: COLORS.surfaceOverlay,
              border: '1px solid ' + COLORS.line,
              borderRadius: 13,
            }}
          >
            <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>{stat.label}</span>
            <span style={{ fontSize: 25, fontWeight: 600, fontVariantNumeric: 'tabular-nums', lineHeight: 1.05, color: stat.color }}>
              {stat.value}
            </span>
            <span style={{ fontSize: 12, color: COLORS.textMuted }}>{stat.note}</span>
          </div>
        ))}
      </div>

      <div style={CARD}>
        <div style={{ position: 'relative', height: 340, background: '#071D28' }}>
          <RouteMap stops={journeyStops(journey)} dash={upcoming} accent={upcoming ? '#FFB067' : COLORS.cyan} />
        </div>
        <div style={{ padding: '13px 20px', borderTop: '1px solid ' + COLORS.line, fontSize: 12.5, color: COLORS.textMuted }}>
          {gap
            ? 'The dashed leg was never flown: you came home from a different airport than the one you arrived at.'
            : 'Arcs follow the great circle between each pair of airports, the way the flight actually ran.'}
        </div>
      </div>

      <div style={CARD}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 14,
            padding: '16px 20px',
            borderBottom: '1px solid ' + COLORS.line,
          }}
        >
          <span style={{ fontSize: 16, fontWeight: 600 }}>Legs in order</span>
          <span style={{ fontSize: 12.5, color: COLORS.textDim }}>
            {legs.length} {legs.length === 1 ? 'flight' : 'flights'} · {formatKm(distance)}
          </span>
        </div>

        {legs.map((leg, index) => (
          <LegRow key={leg.id} index={index + 1} journeyId={journey.id} legId={leg.id}>
            <span style={{ color: COLORS.textMuted, fontSize: 13, fontVariantNumeric: 'tabular-nums' }}>
              {formatDate(leg.flight.flightDate)}
            </span>
            <span style={{ color: COLORS.text, fontSize: 13.5, fontWeight: 600, letterSpacing: '0.03em' }}>
              {leg.flight.flightNumber ?? '—'}
            </span>
            <span style={{ color: COLORS.text, fontSize: 13.5, fontWeight: 500 }}>{routeOf(leg)}</span>
            <span style={{ color: COLORS.textMuted, fontSize: 13 }}>{aircraftLabel(leg)}</span>
            <span style={{ color: COLORS.text, fontSize: 13.5, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
              {formatKm(leg.flight.distanceKm)}
            </span>
            <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
              <FlightFlags userFlight={leg} />
            </span>
          </LegRow>
        ))}

        {legs.length === 0 && (
          <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>
            No flights in this journey yet. Add one and pick this journey on the confirm screen.
          </div>
        )}

        {gap && (
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 12,
              padding: '15px 20px',
              borderTop: '1px solid ' + COLORS.lineSoft,
              background: 'rgba(14,54,68,0.4)',
            }}
          >
            <svg viewBox="0 0 16 16" width={15} height={15} style={{ marginTop: 2, flexShrink: 0 }} fill="none" stroke="#7FCFE8" strokeWidth={1.7} aria-hidden="true">
              <circle cx={8} cy={8} r={6.4} />
              <path d="M8 7.2 V11.4" strokeLinecap="round" />
              <circle cx={8} cy={4.9} r={0.9} fill="#7FCFE8" stroke="none" />
            </svg>
            <span style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.bodyOnCard }}>
              You flew home from a different airport than the one you arrived at. The gap between them was not flown, so it adds
              nothing to the distance.
            </span>
          </div>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 18 }}>
        <div style={CARD}>
          <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>
            Countries on this journey
          </div>
          <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 11 }}>
            {countries.map((country) => (
              <div key={country.code} style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: 2,
                    background: country.kind === 'transit' ? COLORS.warning : COLORS.cyan,
                    flexShrink: 0,
                  }}
                />
                <span style={{ fontSize: 13.5, color: COLORS.text }}>{country.code}</span>
                <span style={{ fontSize: 12, color: COLORS.textDim, marginLeft: 'auto' }}>
                  {country.kind === 'transit' ? 'transit' : 'visited'}
                </span>
              </div>
            ))}
            {countries.length === 0 && <span style={{ fontSize: 13, color: COLORS.textMuted }}>No countries yet.</span>}
            <div style={{ fontSize: 12, lineHeight: 1.55, color: COLORS.textMuted, paddingTop: 5, borderTop: '1px solid ' + COLORS.lineSoft }}>
              A country you only changed planes in is marked as transit and counted separately from one you actually visited.
            </div>
          </div>
        </div>

        <div style={CARD}>
          <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>Visibility</div>
          <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Segmented
              options={VISIBILITIES}
              value={current === 'MIXED' ? null : current}
              onChange={(value) => value && applyVisibility(value)}
              columns={3}
              visibility
            />
            <div style={{ fontSize: 12.5, lineHeight: 1.6, color: COLORS.textMuted }}>
              {applying
                ? 'Applying to every flight…'
                : current === 'MIXED'
                  ? 'The flights in this journey have different settings. Picking one here writes it onto all of them.'
                  : current
                    ? VIS_HINT[current]
                    : 'Add a flight to this journey first.'}
            </div>
          </div>
        </div>
      </div>
    </PageShell>
  );
}

function RenameButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 40,
      padding: '0 18px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.cyan,
      background: 'transparent',
      color: COLORS.cyan,
      fontFamily: FONT_STACK,
      fontSize: 13.5,
      fontWeight: 600,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.raised },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      Rename
    </button>
  );
}

function AddLegButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 7,
      height: 40,
      padding: '0 18px',
      borderRadius: 8,
      background: COLORS.orange,
      color: COLORS.textOnOrange,
      fontSize: 13.5,
      fontWeight: 700,
      textDecoration: 'none',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <Link to="/add" {...hover}>
      <span style={{ fontSize: 16, lineHeight: 1, marginTop: -2 }}>+</span>Add a leg
    </Link>
  );
}

function SaveTitleButton({ saving, onClick }: { saving: boolean; onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 9,
      height: 40,
      padding: '0 18px',
      borderRadius: 8,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 13.5,
      fontWeight: 700,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={onClick} disabled={saving} {...hover}>
      {saving && <Spinner size={13} color={COLORS.textOnOrange} />}
      Save name
    </button>
  );
}
