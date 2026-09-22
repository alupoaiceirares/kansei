import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { COLORS, FONT_STACK, pill } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Spinner } from '../components/Spinner';
import { RouteMap } from '../map/RouteMap';
import { createJourney, fetchJourneys } from '../api/tailwind';
import type { Journey } from '../api/types';
import { hasUnflownGap, journeyDistance, journeyStops, journeyVisibility, legsOf, monthLabel, routeLine } from '../journeys';
import { formatKm } from '../format';

function visibilityPill(journey: Journey) {
  const visibility = journeyVisibility(journey);
  if (!visibility) return null;
  if (visibility === 'MIXED') {
    return (
      <span style={{ ...pill, background: 'rgba(14,54,68,0.9)', borderColor: COLORS.lineStrong, color: COLORS.textMuted }}>MIXED</span>
    );
  }
  const colours =
    visibility === 'PUBLIC'
      ? { background: 'rgba(18,63,44,0.9)', borderColor: '#2C6B4C', color: '#7FE0AE' }
      : visibility === 'PRIVATE'
        ? { background: 'rgba(34,26,46,0.9)', borderColor: COLORS.laterBorder, color: COLORS.laterText }
        : { background: 'rgba(14,54,68,0.9)', borderColor: COLORS.lineStrong, color: '#7FCFE8' };
  return <span style={{ ...pill, ...colours }}>{visibility}</span>;
}

function JourneyCard({ journey }: { journey: Journey }) {
  const legs = legsOf(journey);
  const upcoming = legs.some((leg) => leg.flight.upcoming);
  const gap = hasUnflownGap(journey);
  const hover = useHoverStyle(
    {
      display: 'flex',
      flexDirection: 'column' as const,
      background: COLORS.surfaceOverlay,
      border: '1px solid ' + (upcoming ? '#7A4A16' : COLORS.line),
      borderRadius: 14,
      overflow: 'hidden' as const,
      textDecoration: 'none',
      color: 'inherit',
    },
    { borderColor: upcoming ? '#FFB067' : COLORS.cyan },
  );

  return (
    <Link to={'/journeys/' + journey.id} {...hover}>
      <div
        style={{
          position: 'relative',
          height: 150,
          background: '#071D28',
          borderBottom: '1px solid ' + (upcoming ? '#7A4A16' : COLORS.line),
        }}
      >
        <RouteMap stops={journeyStops(journey)} dash={upcoming} accent={upcoming ? '#FFB067' : COLORS.cyan} compact />
        <span style={{ position: 'absolute', top: 12, right: 12, display: 'flex', gap: 6 }}>
          {upcoming && (
            <span style={{ ...pill, background: 'rgba(46,29,12,0.9)', borderColor: '#7A4A16', color: '#FFB067' }}>UPCOMING</span>
          )}
          {gap && (
            <span style={{ ...pill, background: 'rgba(14,54,68,0.9)', borderColor: COLORS.lineStrong, color: COLORS.textMuted }}>
              RETURN ELSEWHERE
            </span>
          )}
          {visibilityPill(journey)}
        </span>
      </div>
      <div style={{ padding: '17px 19px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
          <span style={{ fontSize: 17, fontWeight: 600, letterSpacing: '-0.01em' }}>{journey.title}</span>
          <span style={{ fontSize: 12, color: COLORS.textDim, whiteSpace: 'nowrap' }}>{monthLabel(journey)}</span>
        </div>
        <div style={{ fontSize: 13, color: COLORS.textMuted }}>{routeLine(journey)}</div>
        <div style={{ display: 'flex', gap: 16, paddingTop: 8, borderTop: '1px solid ' + COLORS.lineSoft, fontSize: 12.5 }}>
          <span style={{ color: COLORS.textDim }}>
            Distance <span style={{ color: COLORS.text, fontVariantNumeric: 'tabular-nums' }}>{formatKm(journeyDistance(journey))}</span>
          </span>
          <span style={{ color: COLORS.textDim }}>
            Flights <span style={{ color: COLORS.text, fontVariantNumeric: 'tabular-nums' }}>{legs.length}</span>
          </span>
        </div>
      </div>
    </Link>
  );
}

function NewJourneyButton({ onClick, busy }: { onClick: () => void; busy: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 9,
      height: 42,
      padding: '0 20px',
      borderRadius: 9,
      border: '1px solid ' + COLORS.cyan,
      background: 'transparent',
      color: COLORS.cyan,
      fontFamily: FONT_STACK,
      fontSize: 14,
      fontWeight: 600,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.raised },
  );
  return (
    <button type="button" onClick={onClick} disabled={busy} {...hover}>
      {busy && <Spinner size={13} />}
      New journey
    </button>
  );
}

export function JourneysPage() {
  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);

  const load = () =>
    fetchJourneys()
      .then(setJourneys)
      .catch(() => undefined)
      .finally(() => setLoading(false));

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const create = async () => {
    setCreating(true);
    try {
      await createJourney({});
      await load();
    } catch {
      // The list simply stays as it was.
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <PageShell maxWidth={1240}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading your journeys
        </div>
      </PageShell>
    );
  }

  const ordered = [...journeys].sort((a, b) => {
    const aDate = legsOf(a)[0]?.flight.flightDate ?? '';
    const bDate = legsOf(b)[0]?.flight.flightDate ?? '';
    return bDate.localeCompare(aDate);
  });

  return (
    <PageShell maxWidth={1240}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 620 }}>
          <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>Journeys</h1>
          <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: COLORS.textMuted, textWrap: 'pretty' }}>
            A journey groups flights that belong to one trip. It can be a there-and-back, a multi-stop, or a trip that flies home
            from a different airport. Grouping is entirely yours; nothing is inferred.
          </p>
        </div>
        <NewJourneyButton onClick={create} busy={creating} />
      </div>

      {ordered.length === 0 ? (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 16,
            padding: '72px 28px',
            background: 'rgba(10,44,57,0.7)',
            border: '1px dashed ' + COLORS.lineStrong,
            borderRadius: 14,
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 18, fontWeight: 600 }}>No journeys yet</div>
          <div style={{ fontSize: 13.5, color: COLORS.textMuted, maxWidth: 380, lineHeight: 1.6 }}>
            Every flight you add can go into a journey, either a new one or an existing one. Flights that are not grouped simply
            stay on their own in My flights.
          </div>
          <Link
            to="/add"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              height: 42,
              padding: '0 20px',
              borderRadius: 8,
              background: COLORS.orange,
              color: COLORS.textOnOrange,
              fontSize: 13.5,
              fontWeight: 700,
              textDecoration: 'none',
              whiteSpace: 'nowrap',
            }}
          >
            Add a flight
          </Link>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(330px, 1fr))', gap: 18 }}>
          {ordered.map((journey) => (
            <JourneyCard key={journey.id} journey={journey} />
          ))}
        </div>
      )}
    </PageShell>
  );
}
