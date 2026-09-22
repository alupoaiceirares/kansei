import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { COLORS, FONT_STACK, pill } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { Segmented } from '../components/Segmented';
import { AircraftPhoto } from '../components/AircraftPhoto';
import { RouteMap, type RouteStop } from '../map/RouteMap';
import { ApiError } from '../api/client';
import { addFlight, fetchJourneys, lookupFlight } from '../api/tailwind';
import type { CabinClass, Flight, Journey, LookupEntry, SeatPosition, TripReason, Visibility } from '../api/types';
import { useSession } from '../session';
import { formatDate, formatDuration, formatKm, formatTime } from '../format';

const VIS_HINT: Record<Visibility, string> = {
  PUBLIC: 'Anyone who opens your profile can see this flight.',
  FRIENDS: 'Only people you are friends with on WTW. This is the default so nothing becomes public by accident.',
  PRIVATE: 'Only you. It still counts in your own stats, map and records.',
};

const VISIBILITIES = [
  { value: 'PUBLIC' as const, label: 'PUBLIC' },
  { value: 'FRIENDS' as const, label: 'FRIENDS' },
  { value: 'PRIVATE' as const, label: 'PRIVATE' },
];

const POSITIONS = [
  { value: 'WINDOW' as const, label: 'Window' },
  { value: 'MIDDLE' as const, label: 'Middle' },
  { value: 'AISLE' as const, label: 'Aisle' },
];

const CABINS = [
  { value: 'ECONOMY' as const, label: 'Economy' },
  { value: 'PREMIUM_ECONOMY' as const, label: 'Premium' },
  { value: 'BUSINESS' as const, label: 'Business' },
  { value: 'FIRST' as const, label: 'First' },
];

const REASONS = [
  { value: 'LEISURE' as const, label: 'Leisure' },
  { value: 'BUSINESS' as const, label: 'Business' },
];

const FIELD_INPUT = {
  height: 40,
  padding: '0 12px',
  borderRadius: 8,
  background: COLORS.ground,
  border: '1px solid ' + COLORS.lineStrong,
  fontFamily: FONT_STACK,
  fontSize: 14,
  color: COLORS.text,
  outline: 'none',
  width: '100%',
  boxSizing: 'border-box' as const,
};

function flagPill(label: string, background: string, border: string, color: string) {
  return (
    <span key={label} style={{ ...pill, padding: '4px 10px', fontSize: 11, background, borderColor: border, color }}>
      {label}
    </span>
  );
}

function stopsOf(flight: Flight, upcoming: boolean): RouteStop[] {
  return [flight.departureAirport, flight.arrivalAirport].map((airport) => ({
    code: airport.iata ?? airport.icao ?? '',
    lon: airport.longitude,
    lat: airport.latitude,
    countryCode: airport.countryCode,
    kind: upcoming ? ('soon' as const) : ('end' as const),
  }));
}

function durationOf(flight: Flight): string | null {
  const start = flight.departureActualUtc ?? flight.departureRevisedUtc ?? flight.departureScheduledUtc;
  const end = flight.arrivalActualUtc ?? flight.arrivalRevisedUtc ?? flight.arrivalScheduledUtc;
  if (!start || !end) return null;
  const minutes = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000);
  return minutes > 0 ? formatDuration(minutes) : null;
}

export function AddFlightConfirmPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { me } = useSession();

  const flightId = Number(params.get('flightId'));
  const flightNumber = params.get('flightNumber') ?? '';
  const date = params.get('date') ?? '';

  const [entry, setEntry] = useState<LookupEntry | null>(null);
  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [visibility, setVisibility] = useState<Visibility>(me?.defaultVisibility ?? 'FRIENDS');
  const [journeyMode, setJourneyMode] = useState<'NEW' | 'EXISTING'>('NEW');
  const [journeyId, setJourneyId] = useState<number | null>(null);
  const [seat, setSeat] = useState('');
  const [seatPosition, setSeatPosition] = useState<SeatPosition | null>(null);
  const [cabinClass, setCabinClass] = useState<CabinClass | null>(null);
  const [reason, setReason] = useState<TripReason | null>(null);
  const [notes, setNotes] = useState('');
  const [cargoModal, setCargoModal] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let live = true;
    // The lookup is served from our own row by then, the provider is not called twice.
    Promise.all([lookupFlight(flightNumber, date), fetchJourneys().catch(() => [])])
      .then(([lookup, loadedJourneys]) => {
        if (!live) return;
        setEntry(lookup.flights.find((item) => item.flight.id === flightId) ?? lookup.flights[0] ?? null);
        setJourneys(loadedJourneys);
        setLoading(false);
      })
      .catch((cause) => {
        if (!live) return;
        setError(cause instanceof ApiError ? (cause.detail ?? 'That flight could not be loaded.') : 'That flight could not be loaded.');
        setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [flightId, flightNumber, date]);

  const flight = entry?.flight ?? null;
  const upcoming = !!flight?.upcoming;
  const already = !!entry?.alreadyInLog;
  const stops = useMemo(() => (flight ? stopsOf(flight, upcoming) : []), [flight, upcoming]);

  const save = async () => {
    if (!flight || already || saving) return;
    if (flight.cargo && !cargoModal) {
      setCargoModal(true);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const saved = await addFlight({
        flightId: flight.id,
        visibility,
        journeyId: journeyMode === 'EXISTING' ? journeyId : null,
        confirmCargo: flight.cargo ? true : undefined,
        seat: seat.trim() || null,
        seatPosition,
        cabinClass,
        reason,
        notes: notes.trim() || null,
      });
      navigate('/flights/' + saved.id);
    } catch (cause) {
      setCargoModal(false);
      setError(cause instanceof ApiError ? (cause.detail ?? 'The flight could not be added.') : 'The flight could not be added.');
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <PageShell maxWidth={1120}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading the flight
        </div>
      </PageShell>
    );
  }

  if (!flight) {
    return (
      <PageShell maxWidth={1120}>
        <Banner tone="error" title="Flight not available">
          {error ?? 'That flight is no longer available. Search for it again, or add it by hand.'}
        </Banner>
        <Link to="/add" style={{ fontSize: 13.5 }}>
          ← Back to search
        </Link>
      </PageShell>
    );
  }

  const duration = durationOf(flight);
  const from = flight.departureAirport;
  const to = flight.arrivalAirport;

  return (
    <PageShell maxWidth={1120}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <Link to="/add" style={{ fontSize: 13, color: COLORS.textMuted }}>
            ← Back to search
          </Link>
          <h1 style={{ margin: 0, fontSize: 30, fontWeight: 600, letterSpacing: '-0.02em' }}>
            {already ? 'You have already logged this one' : 'Confirm the flight'}
          </h1>
        </div>
        <div style={{ fontSize: 12.5, color: COLORS.textDim }}>One entry per leg.</div>
      </div>

      {already && (
        <Banner tone="info" title="Already in your log" bg={COLORS.laterBg} border={COLORS.laterBorder} dot={COLORS.laterText}>
          You added {flight.flightNumber} on {formatDate(flight.flightDate)} already. It will not be added twice.{' '}
          <Link to="/flights" style={{ fontWeight: 600 }}>
            Open your flights
          </Link>
        </Banner>
      )}

      {upcoming && (
        <Banner tone="warning" title="This flight has not happened yet" bg="#2E1D0C" border="#7A4A16" dot="#FFB067">
          It will be saved and shown as upcoming, but stays out of your stats, records, collections and map until the departure
          date has passed. The aircraft below is the scheduled one, and airlines change it often.
        </Banner>
      )}

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.45fr) minmax(0,1fr)', gap: 22, alignItems: 'start' }}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            background: COLORS.surface,
            border: '1px solid ' + (already ? COLORS.laterBorder : COLORS.lineStrong),
            borderRadius: 14,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 14,
              padding: '18px 22px',
              borderBottom: '1px solid ' + COLORS.line,
              flexWrap: 'wrap',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span style={{ fontSize: 20, fontWeight: 700, letterSpacing: '0.04em' }}>{flight.flightNumber}</span>
              <span style={{ fontSize: 14, color: COLORS.textMuted }}>{flight.airline.name}</span>
            </div>
            <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
              {upcoming && flagPill('UPCOMING', '#2E1D0C', '#7A4A16', '#FFB067')}
              {flight.cargo && flagPill('CARGO', '#3A2A12', '#6B5220', COLORS.warning)}
              {already && flagPill('IN YOUR LOG', COLORS.laterBg, COLORS.laterBorder, COLORS.laterText)}
              {!upcoming && !flight.cargo && !already && flagPill('LANDED', '#123F2C', '#2C6B4C', '#7FE0AE')}
            </div>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'minmax(0,1fr) 110px minmax(0,1fr)',
              gap: 10,
              alignItems: 'center',
              padding: '22px 22px 16px',
            }}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
              <span style={{ fontSize: 34, fontWeight: 700, letterSpacing: '0.02em', lineHeight: 1 }}>{from.iata ?? from.icao}</span>
              <span style={{ fontSize: 13, color: COLORS.textMuted }}>{from.city ?? from.name}</span>
              <span style={{ fontSize: 12, color: COLORS.textDim }}>{from.countryCode}</span>
            </div>
            <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <span style={{ fontSize: 12, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>{formatKm(flight.distanceKm)}</span>
              <span style={{ fontSize: 11, color: COLORS.textDim }}>{duration ? (upcoming ? 'scheduled ' + duration : duration) : '—'}</span>
            </span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5, textAlign: 'right' }}>
              <span style={{ fontSize: 34, fontWeight: 700, letterSpacing: '0.02em', lineHeight: 1 }}>{to.iata ?? to.icao}</span>
              <span style={{ fontSize: 13, color: COLORS.textMuted }}>{to.city ?? to.name}</span>
              <span style={{ fontSize: 12, color: COLORS.textDim }}>{to.countryCode}</span>
            </div>
          </div>

          <div style={{ position: 'relative', height: 250, background: '#071D28', borderTop: '1px solid ' + COLORS.line }}>
            <RouteMap stops={stops} dash={upcoming} accent={upcoming ? '#FFB067' : COLORS.cyan} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', borderTop: '1px solid ' + COLORS.line }}>
            <div style={{ padding: '16px 22px', borderRight: '1px solid ' + COLORS.line, display: 'flex', flexDirection: 'column', gap: 10 }}>
              <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>Departure</span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
                <span style={{ fontSize: 12, color: COLORS.textDim, width: 70 }}>Scheduled</span>
                <span style={{ fontSize: 15, fontVariantNumeric: 'tabular-nums' }}>{formatTime(flight.departureScheduledUtc, from.timeZone)}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
                <span style={{ fontSize: 12, color: COLORS.textDim, width: 70 }}>Actual</span>
                <span style={{ fontSize: 15, fontVariantNumeric: 'tabular-nums', color: flight.departureActualUtc ? COLORS.text : COLORS.textFaint }}>
                  {flight.departureActualUtc ? formatTime(flight.departureActualUtc, from.timeZone) : '—'}
                </span>
              </div>
            </div>
            <div style={{ padding: '16px 22px', display: 'flex', flexDirection: 'column', gap: 10 }}>
              <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>Arrival</span>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
                <span style={{ fontSize: 12, color: COLORS.textDim, width: 70 }}>Scheduled</span>
                <span style={{ fontSize: 15, fontVariantNumeric: 'tabular-nums' }}>{formatTime(flight.arrivalScheduledUtc, to.timeZone)}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
                <span style={{ fontSize: 12, color: COLORS.textDim, width: 70 }}>Actual</span>
                <span style={{ fontSize: 15, fontVariantNumeric: 'tabular-nums', color: flight.arrivalActualUtc ? COLORS.text : COLORS.textFaint }}>
                  {flight.arrivalActualUtc ? formatTime(flight.arrivalActualUtc, to.timeZone) : '—'}
                </span>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 18, padding: '20px 22px', borderTop: '1px solid ' + COLORS.line, flexWrap: 'wrap' }}>
            <AircraftPhoto aircraftTypeId={null} family={flight.aircraft.family} width={190} height={118} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0, flex: '1 1 200px' }}>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' }}>
                <span style={{ fontSize: 19, fontWeight: 600 }}>{flight.aircraft.family ?? 'Unknown'}</span>
                <span style={{ fontSize: 14, color: COLORS.textMuted }}>{flight.aircraft.typeName ?? 'Variant unknown'}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 5, fontSize: 13 }}>
                <div style={{ display: 'flex', gap: 10 }}>
                  <span style={{ color: COLORS.textDim, width: 96 }}>Registration</span>
                  <span style={{ color: COLORS.text }}>{flight.aircraft.registration ?? 'Not yet assigned'}</span>
                </div>
                <div style={{ display: 'flex', gap: 10 }}>
                  <span style={{ color: COLORS.textDim, width: 96 }}>Distance</span>
                  <span style={{ color: COLORS.text, fontVariantNumeric: 'tabular-nums' }}>{formatKm(flight.distanceKm)}</span>
                </div>
                <div style={{ display: 'flex', gap: 10 }}>
                  <span style={{ color: COLORS.textDim, width: 96 }}>Status</span>
                  <span style={{ color: COLORS.text }}>{flight.status ?? (upcoming ? 'Scheduled' : 'Unknown')}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 18,
            padding: 24,
            background: COLORS.surface,
            border: '1px solid ' + COLORS.lineStrong,
            borderRadius: 14,
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Who can see this flight</span>
            <Segmented options={VISIBILITIES} value={visibility} onChange={(value) => value && setVisibility(value)} columns={3} visibility />
            <span style={{ fontSize: 11.5, color: COLORS.textDim, lineHeight: 1.5 }}>{VIS_HINT[visibility]}</span>
          </div>

          <div style={{ height: 1, background: COLORS.line }} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Journey</span>
            <Segmented
              options={[
                { value: 'NEW', label: 'New journey' },
                { value: 'EXISTING', label: 'Existing journey' },
              ]}
              value={journeyMode}
              onChange={(value) => value && setJourneyMode(value)}
            />
            {journeyMode === 'EXISTING' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 5, maxHeight: 172, overflowY: 'auto', scrollbarWidth: 'thin' }}>
                {journeys.length === 0 && <span style={{ fontSize: 12.5, color: COLORS.textDim }}>No journeys yet.</span>}
                {journeys.map((journey) => (
                  <button
                    key={journey.id}
                    type="button"
                    onClick={() => setJourneyId(journey.id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '9px 11px',
                      borderRadius: 8,
                      cursor: 'pointer',
                      textAlign: 'left',
                      border: '1px solid ' + (journeyId === journey.id ? COLORS.cyan : COLORS.line),
                      background: journeyId === journey.id ? COLORS.raised : 'transparent',
                    }}
                  >
                    <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                      <span style={{ fontSize: 13.5, fontWeight: 500, color: COLORS.text }}>{journey.title}</span>
                      <span style={{ fontSize: 11.5, color: COLORS.textDim }}>
                        {journey.flights.length} flights ·{' '}
                        {formatKm(journey.flights.reduce((sum, item) => sum + item.flight.distanceKm, 0))}
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            )}
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>
              {journeyMode === 'NEW'
                ? 'A new journey is named after the route unless you rename it.'
                : 'A journey can fly home from a different airport than it arrived at.'}
            </span>
          </div>

          <div style={{ height: 1, background: COLORS.line }} />

          <div style={{ display: 'grid', gridTemplateColumns: '96px minmax(0,1fr)', gap: 12 }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>
                Seat <span style={{ color: COLORS.textDim, fontWeight: 400 }}>— optional</span>
              </span>
              <input type="text" value={seat} onChange={(event) => setSeat(event.target.value)} placeholder="e.g. 24A" style={FIELD_INPUT} />
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
              <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>
                Position <span style={{ color: COLORS.textDim, fontWeight: 400 }}>— optional</span>
              </span>
              <Segmented options={POSITIONS} value={seatPosition} onChange={setSeatPosition} columns={3} clearable />
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>
              Cabin <span style={{ color: COLORS.textDim, fontWeight: 400 }}>— optional</span>
            </span>
            <Segmented options={CABINS} value={cabinClass} onChange={setCabinClass} columns={4} clearable />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>
              Reason <span style={{ color: COLORS.textDim, fontWeight: 400 }}>— optional</span>
            </span>
            <Segmented options={REASONS} value={reason} onChange={setReason} columns={2} clearable />
          </div>

          <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>
              Notes <span style={{ color: COLORS.textDim, fontWeight: 400 }}>— optional</span>
            </span>
            <textarea
              rows={3}
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="Anything worth remembering"
              style={{
                padding: '10px 12px',
                borderRadius: 8,
                background: COLORS.ground,
                border: '1px solid ' + COLORS.lineStrong,
                fontFamily: FONT_STACK,
                fontSize: 13.5,
                color: COLORS.text,
                outline: 'none',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </label>

          <div style={{ height: 1, background: COLORS.line }} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <ConfirmButton disabled={already} saving={saving} upcoming={upcoming} onClick={save} />
            <Link to="/add" style={{ textAlign: 'center', fontSize: 13, color: COLORS.textMuted }}>
              Cancel
            </Link>
          </div>
        </div>
      </div>

      {cargoModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 60,
            background: 'rgba(3,14,19,0.72)',
            backdropFilter: 'blur(3px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 32,
          }}
        >
          <div
            style={{
              width: '100%',
              maxWidth: 440,
              padding: 26,
              background: COLORS.raised,
              border: '1px solid ' + COLORS.lineStrong,
              borderRadius: 14,
              boxShadow: '0 30px 70px rgba(0,0,0,0.6)',
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 26,
                  borderRadius: 6,
                  background: '#3A2A12',
                  border: '1px solid #6B5220',
                  color: COLORS.warning,
                  fontSize: 13,
                  fontWeight: 700,
                }}
              >
                C
              </span>
              <span style={{ fontSize: 18, fontWeight: 600 }}>This is a cargo flight</span>
            </div>
            <p style={{ margin: 0, fontSize: 14, lineHeight: 1.65, color: COLORS.bodyOnCard }}>
              {flight.flightNumber} is a freight service with no passenger cabin. People do fly on them, so this is not a block,
              just make sure it is the flight you mean.
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 4, flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={() => setCargoModal(false)}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  height: 40,
                  padding: '0 18px',
                  borderRadius: 8,
                  border: '1px solid ' + COLORS.lineStrong,
                  background: 'transparent',
                  color: COLORS.textMuted,
                  fontFamily: FONT_STACK,
                  fontSize: 13.5,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                No, go back
              </button>
              <button
                type="button"
                onClick={save}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
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
                }}
              >
                Yes, add it
              </button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}

function ConfirmButton({
  disabled,
  saving,
  upcoming,
  onClick,
}: {
  disabled: boolean;
  saving: boolean;
  upcoming: boolean;
  onClick: () => void;
}) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 9,
      height: 46,
      borderRadius: 9,
      background: disabled ? '#123240' : COLORS.orange,
      border: 'none',
      color: disabled ? COLORS.textFaint : COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 15,
      fontWeight: 700,
      cursor: disabled ? 'not-allowed' : 'pointer',
      whiteSpace: 'nowrap',
    },
    disabled ? {} : { background: COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={onClick} disabled={disabled || saving} {...hover}>
      {saving && <Spinner size={14} color={COLORS.textOnOrange} />}
      {disabled ? 'Already added' : upcoming ? 'Add as upcoming flight' : 'Add this flight'}
    </button>
  );
}
