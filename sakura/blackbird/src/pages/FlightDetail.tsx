import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { COLORS, FONT_STACK, pill, type BannerTone } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { Segmented } from '../components/Segmented';
import { AircraftPhoto } from '../components/AircraftPhoto';
import { RouteMap, type RouteStop } from '../map/RouteMap';
import { ApiError } from '../api/client';
import { deleteUserFlight, fetchJourneys, refreshUserFlight, updateUserFlight } from '../api/tailwind';
import { fetchDashboardProfile, type DashboardProfile } from '../api/profile';
import type { CabinClass, Journey, SeatPosition, TripReason, UserFlight, Visibility } from '../api/types';
import { formatDate, formatDuration, formatKm, formatTime } from '../format';

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

const CABIN_LABEL: Record<CabinClass, string> = {
  ECONOMY: 'Economy',
  PREMIUM_ECONOMY: 'Premium economy',
  BUSINESS: 'Business',
  FIRST: 'First',
};

const POSITION_LABEL: Record<SeatPosition, string> = { WINDOW: 'Window', MIDDLE: 'Middle', AISLE: 'Aisle' };
const REASON_LABEL: Record<TripReason, string> = { LEISURE: 'Leisure', BUSINESS: 'Business' };

const CARD = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden' as const,
};

const CARD_TITLE = { padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 };

function flagPill(label: string, background: string, border: string, color: string) {
  return (
    <span key={label} style={{ ...pill, padding: '4px 10px', fontSize: 11, background, borderColor: border, color }}>
      {label}
    </span>
  );
}

function durationOf(userFlight: UserFlight): string | null {
  const flight = userFlight.flight;
  const start = flight.departureActualUtc ?? flight.departureRevisedUtc ?? flight.departureScheduledUtc;
  const end = flight.arrivalActualUtc ?? flight.arrivalRevisedUtc ?? flight.arrivalScheduledUtc;
  if (!start || !end) return null;
  const minutes = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000);
  return minutes > 0 ? formatDuration(minutes) : null;
}

export function FlightDetailPage() {
  const { userFlightId } = useParams();
  const navigate = useNavigate();
  const id = Number(userFlightId);

  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [profile, setProfile] = useState<DashboardProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [askDelete, setAskDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshNote, setRefreshNote] = useState<{ tone: BannerTone; title: string; text: string } | null>(null);

  const [visibility, setVisibility] = useState<Visibility>('FRIENDS');
  const [seat, setSeat] = useState('');
  const [seatPosition, setSeatPosition] = useState<SeatPosition | null>(null);
  const [cabinClass, setCabinClass] = useState<CabinClass | null>(null);
  const [reason, setReason] = useState<TripReason | null>(null);
  const [notes, setNotes] = useState('');

  const load = () => {
    setLoading(true);
    return Promise.all([fetchJourneys(), fetchDashboardProfile().catch(() => null)])
      .then(([loadedJourneys, loadedProfile]) => {
        setJourneys(loadedJourneys);
        setProfile(loadedProfile);
      })
      .catch(() => setError('This flight could not be loaded.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const journey = useMemo(() => journeys.find((item) => item.flights.some((leg) => leg.id === id)), [journeys, id]);
  const userFlight = useMemo(() => journey?.flights.find((leg) => leg.id === id), [journey, id]);

  useEffect(() => {
    if (!userFlight) return;
    setVisibility(userFlight.visibility);
    setSeat(userFlight.seat ?? '');
    setSeatPosition(userFlight.seatPosition);
    setCabinClass(userFlight.cabinClass);
    setReason(userFlight.reason);
    setNotes(userFlight.notes ?? '');
  }, [userFlight]);

  const stops: RouteStop[] = useMemo(() => {
    if (!userFlight) return [];
    const flight = userFlight.flight;
    return [flight.departureAirport, flight.arrivalAirport].map((airport) => ({
      code: airport.iata ?? airport.icao ?? '',
      lon: airport.longitude,
      lat: airport.latitude,
      countryCode: airport.countryCode,
      kind: flight.upcoming ? ('soon' as const) : ('end' as const),
    }));
  }, [userFlight]);

  if (loading) {
    return (
      <PageShell maxWidth={1180}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading the flight
        </div>
      </PageShell>
    );
  }

  if (!userFlight) {
    return (
      <PageShell maxWidth={1180}>
        <Banner tone="error" title="Flight not found">
          {error ?? 'That flight is not in your log any more.'}
        </Banner>
        <Link to="/flights" style={{ fontSize: 13.5 }}>
          ← My flights
        </Link>
      </PageShell>
    );
  }

  const flight = userFlight.flight;
  const from = flight.departureAirport;
  const to = flight.arrivalAirport;
  const duration = durationOf(userFlight);
  const manual = flight.source === 'MANUAL';
  const sparse = manual && !flight.departureScheduledUtc && !flight.arrivalScheduledUtc;
  const familyCount = profile?.aircraft.find((item) => item.family === flight.aircraft.family)?.flightCount ?? null;

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      await updateUserFlight(userFlight.id, {
        visibility,
        seat: seat.trim() || null,
        seatPosition,
        cabinClass,
        reason,
        notes: notes.trim() || null,
      });
      setEditing(false);
      await load();
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'The change could not be saved.') : 'The change could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  // Swaps the one leg in place, so the page keeps showing while the provider answers
  const refresh = async () => {
    setRefreshing(true);
    setRefreshNote(null);
    try {
      const updated = await refreshUserFlight(userFlight.id);
      setJourneys((current) =>
        current.map((item) => ({ ...item, flights: item.flights.map((leg) => (leg.id === updated.id ? updated : leg)) })),
      );
      setRefreshNote(
        updated.flight.awaitingRefresh
          ? { tone: 'info', title: 'Schedule updated', text: 'The real times and aircraft follow once the flight has landed.' }
          : { tone: 'success', title: 'Flight data updated', text: 'Real times and aircraft are in.' },
      );
    } catch (cause) {
      const detail = cause instanceof ApiError ? cause.detail : null;
      if (cause instanceof ApiError && cause.status === 429) {
        setRefreshNote({ tone: 'warning', title: 'Too soon, try later', text: detail ?? 'This flight was refreshed recently.' });
      } else {
        setRefreshNote({ tone: 'error', title: 'Refresh did not work', text: detail ?? 'The flight data could not be refreshed.' });
      }
    } finally {
      setRefreshing(false);
    }
  };

  const remove = async () => {
    setDeleting(true);
    try {
      await deleteUserFlight(userFlight.id);
      navigate('/flights');
    } catch (cause) {
      setAskDelete(false);
      setDeleting(false);
      setError(cause instanceof ApiError ? (cause.detail ?? 'The flight could not be deleted.') : 'The flight could not be deleted.');
    }
  };

  const details: { label: string; value: string; color?: string }[] = [
    { label: 'Visibility', value: userFlight.visibility },
    { label: 'Seat', value: userFlight.seat ?? 'Not recorded', color: userFlight.seat ? COLORS.text : COLORS.textFaint },
    {
      label: 'Position',
      value: userFlight.seatPosition ? POSITION_LABEL[userFlight.seatPosition] : 'Not recorded',
      color: userFlight.seatPosition ? COLORS.text : COLORS.textFaint,
    },
    {
      label: 'Cabin',
      value: userFlight.cabinClass ? CABIN_LABEL[userFlight.cabinClass] : 'Not recorded',
      color: userFlight.cabinClass ? COLORS.text : COLORS.textFaint,
    },
    {
      label: 'Reason',
      value: userFlight.reason ? REASON_LABEL[userFlight.reason] : 'Not recorded',
      color: userFlight.reason ? COLORS.text : COLORS.textFaint,
    },
    { label: 'Distance', value: formatKm(flight.distanceKm) },
  ];

  return (
    <PageShell maxWidth={1180}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
          <Link to="/flights" style={{ fontSize: 13, color: COLORS.textMuted }}>
            ← My flights
          </Link>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, flexWrap: 'wrap' }}>
            <h1 style={{ margin: 0, fontSize: 34, fontWeight: 600, letterSpacing: '-0.02em' }}>
              {flight.flightNumber ?? 'No flight number'}
            </h1>
            <span style={{ fontSize: 16, color: COLORS.textMuted }}>{flight.airline.name}</span>
            <span style={{ fontSize: 14, color: COLORS.textDim }}>{formatDate(flight.flightDate)}</span>
          </div>
          <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
            {flight.upcoming
              ? flagPill('UPCOMING', '#2E1D0C', '#7A4A16', '#FFB067')
              : flagPill('LANDED', '#123F2C', '#2C6B4C', '#7FE0AE')}
            {flight.cargo && flagPill('CARGO', '#3A2A12', '#6B5220', COLORS.warning)}
            {manual && flagPill('ADDED BY HAND', COLORS.raised, COLORS.lineStrong, COLORS.textMuted)}
            {flagPill(userFlight.visibility, ...visibilityColors(userFlight.visibility))}
          </div>
        </div>

        <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
          <EditButton editing={editing} onClick={() => setEditing((open) => !open)} />
          <DeleteButton onClick={() => setAskDelete(true)} />
        </div>
      </div>

      {flight.upcoming && (
        <Banner tone="warning" title={'Not flown yet — ' + formatDate(flight.flightDate)} bg="#2E1D0C" border="#7A4A16" dot="#FFB067">
          Excluded from stats, records, collections and the map until the departure date passes. The aircraft shown is the
          scheduled one.
        </Banner>
      )}

      {sparse && (
        <Banner tone="warning" title="Added by hand, times unknown">
          Distance is still counted because it comes from the two airports. This flight is left out of time-in-air only.
        </Banner>
      )}

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.5fr) minmax(0,1fr)', gap: 22, alignItems: 'start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={CARD}>
            <div style={{ position: 'relative', height: 300, background: '#071D28', borderBottom: '1px solid ' + COLORS.line }}>
              <RouteMap
                stops={stops}
                dash={flight.upcoming || sparse}
                accent={flight.upcoming ? '#FFB067' : sparse ? COLORS.textMuted : COLORS.cyan}
              />
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0,1fr) auto minmax(0,1fr)',
                alignItems: 'center',
                gap: 16,
                padding: '18px 26px',
                borderBottom: '1px solid ' + COLORS.line,
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
                <span style={{ fontSize: 32, fontWeight: 700, letterSpacing: '0.02em', lineHeight: 1 }}>{from.iata ?? from.icao}</span>
                <span style={{ fontSize: 13.5, color: COLORS.bodyOnCard }}>{from.city ?? from.name}</span>
                <span style={{ fontSize: 12, color: COLORS.textDim }}>{from.countryCode}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}>
                <span style={{ fontSize: 12.5, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>{formatKm(flight.distanceKm)}</span>
                <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{duration ?? '—'}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, textAlign: 'right', minWidth: 0 }}>
                <span style={{ fontSize: 32, fontWeight: 700, letterSpacing: '0.02em', lineHeight: 1 }}>{to.iata ?? to.icao}</span>
                <span style={{ fontSize: 13.5, color: COLORS.bodyOnCard }}>{to.city ?? to.name}</span>
                <span style={{ fontSize: 12, color: COLORS.textDim }}>{to.countryCode}</span>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))' }}>
              <div style={{ padding: '18px 22px', borderRight: '1px solid ' + COLORS.line, display: 'flex', flexDirection: 'column', gap: 11 }}>
                <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>Departure</span>
                <TimeRow label="Scheduled" value={flight.departureScheduledUtc ? formatTime(flight.departureScheduledUtc, from.timeZone) : '—'} />
                <TimeRow
                  label="Actual"
                  value={flight.departureActualUtc ? formatTime(flight.departureActualUtc, from.timeZone) : '—'}
                  dim={!flight.departureActualUtc}
                />
              </div>
              <div style={{ padding: '18px 22px', display: 'flex', flexDirection: 'column', gap: 11 }}>
                <span style={{ fontSize: 11, letterSpacing: '0.14em', textTransform: 'uppercase', color: COLORS.textDim }}>Arrival</span>
                <TimeRow label="Scheduled" value={flight.arrivalScheduledUtc ? formatTime(flight.arrivalScheduledUtc, to.timeZone) : '—'} />
                <TimeRow
                  label="Actual"
                  value={flight.arrivalActualUtc ? formatTime(flight.arrivalActualUtc, to.timeZone) : '—'}
                  dim={!flight.arrivalActualUtc}
                />
              </div>
            </div>
          </div>

          <div style={CARD}>
            <div style={CARD_TITLE}>Aircraft</div>
            <div style={{ display: 'flex', gap: 20, padding: 20, flexWrap: 'wrap' }}>
              <AircraftPhoto aircraftTypeId={null} family={flight.aircraft.family} width={248} height={152} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 11, minWidth: 0, flex: '1 1 220px' }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
                  <Link to="/stats" style={{ fontSize: 23, fontWeight: 600, letterSpacing: '-0.01em' }}>
                    {flight.aircraft.family ?? 'Unknown'}
                  </Link>
                  <span style={{ fontSize: 14.5, color: COLORS.textMuted }}>{flight.aircraft.typeName ?? 'Variant unknown'}</span>
                  {flight.awaitingRefresh && flagPill('SCHEDULED, MAY CHANGE', '#2E1D0C', '#7A4A16', '#FFB067')}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 7, fontSize: 13.5 }}>
                  <DetailRow label="Registration" value={flight.aircraft.registration ?? 'Not recorded'} />
                  <DetailRow label="Operator" value={flight.airline.name} />
                  <DetailRow
                    label="You have flown"
                    value={familyCount ? familyCount + (familyCount === 1 ? ' time' : ' times') : '—'}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={CARD}>
            <div style={CARD_TITLE}>Your details</div>
            {editing ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 20 }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Who can see this flight</span>
                  <Segmented options={VISIBILITIES} value={visibility} onChange={(value) => value && setVisibility(value)} columns={3} visibility />
                </div>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Seat</span>
                  <input
                    type="text"
                    value={seat}
                    onChange={(event) => setSeat(event.target.value)}
                    placeholder="e.g. 24A"
                    style={{
                      height: 40,
                      padding: '0 12px',
                      borderRadius: 8,
                      background: COLORS.ground,
                      border: '1px solid ' + COLORS.lineStrong,
                      fontFamily: FONT_STACK,
                      fontSize: 14,
                      color: COLORS.text,
                      outline: 'none',
                    }}
                  />
                </label>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Position</span>
                  <Segmented options={POSITIONS} value={seatPosition} onChange={setSeatPosition} columns={3} clearable />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Cabin</span>
                  <Segmented options={CABINS} value={cabinClass} onChange={setCabinClass} columns={4} clearable />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Reason</span>
                  <Segmented options={REASONS} value={reason} onChange={setReason} columns={2} clearable />
                </div>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Notes</span>
                  <textarea
                    rows={3}
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
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
                    }}
                  />
                </label>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  <SaveEditButton saving={saving} onClick={save} />
                  <button
                    type="button"
                    onClick={() => setEditing(false)}
                    style={{
                      background: 'none',
                      border: 'none',
                      fontFamily: FONT_STACK,
                      fontSize: 13,
                      color: COLORS.textMuted,
                      cursor: 'pointer',
                    }}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {details.map((detail) => (
                  <div
                    key={detail.label}
                    style={{
                      display: 'flex',
                      alignItems: 'baseline',
                      justifyContent: 'space-between',
                      gap: 14,
                      padding: '12px 20px',
                      borderBottom: '1px solid ' + COLORS.lineSoft,
                    }}
                  >
                    <span style={{ fontSize: 12.5, color: COLORS.textMuted, flexShrink: 0 }}>{detail.label}</span>
                    <span style={{ fontSize: 13.5, fontWeight: 500, textAlign: 'right', color: detail.color ?? COLORS.text }}>
                      {detail.value}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div style={CARD}>
            <div style={CARD_TITLE}>Journey</div>
            <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {journey ? (
                <>
                  <Link to={'/journeys/' + journey.id} style={{ fontSize: 15, fontWeight: 600 }}>
                    {journey.title}
                  </Link>
                  <div style={{ fontSize: 13, color: COLORS.textMuted }}>
                    {journey.flights.length} {journey.flights.length === 1 ? 'flight' : 'flights'} ·{' '}
                    {formatKm(journey.flights.reduce((sum, leg) => sum + leg.flight.distanceKm, 0))}
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 7, paddingTop: 12, borderTop: '1px solid ' + COLORS.lineSoft }}>
                    {journey.flights.map((leg) => {
                      const current = leg.id === userFlight.id;
                      return (
                        <div key={leg.id} style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13 }}>
                          <span
                            style={{
                              width: 7,
                              height: 7,
                              borderRadius: '50%',
                              background: current ? COLORS.orange : COLORS.lineStrong,
                              flexShrink: 0,
                            }}
                          />
                          <span style={{ color: current ? COLORS.text : COLORS.textMuted, fontWeight: 500 }}>
                            {(leg.flight.departureAirport.iata ?? leg.flight.departureAirport.icao) +
                              ' → ' +
                              (leg.flight.arrivalAirport.iata ?? leg.flight.arrivalAirport.icao)}
                          </span>
                          <span style={{ color: COLORS.textDim, marginLeft: 'auto', fontVariantNumeric: 'tabular-nums' }}>
                            {formatKm(leg.flight.distanceKm)}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </>
              ) : (
                <div style={{ fontSize: 13, color: COLORS.textMuted }}>This flight stands on its own.</div>
              )}
            </div>
          </div>

          <div style={CARD}>
            <div style={CARD_TITLE}>Notes</div>
            <div style={{ padding: '17px 20px', fontSize: 13.5, lineHeight: 1.65, color: userFlight.notes ? COLORS.bodyOnCard : COLORS.textFaint }}>
              {userFlight.notes ?? 'Nothing written down for this one.'}
            </div>
          </div>

          <div style={CARD}>
            <div style={CARD_TITLE}>Data source</div>
            <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 9, fontSize: 13 }}>
              <DetailRow label="Added" value={manual ? 'Manual entry' : 'Flight search'} width={90} />
              <DetailRow label="Status" value={flight.status ?? (flight.upcoming ? 'Scheduled' : 'Unknown')} width={90} />
              <div style={{ fontSize: 12.5, lineHeight: 1.6, color: COLORS.textMuted, paddingTop: 4 }}>
                {manual
                  ? 'Nothing was fetched from a provider. Distance is calculated from the two airports, so it still counts.'
                  : flight.awaitingRefresh && flight.upcoming
                    ? 'Scheduled data only. It is refreshed automatically the day after the flight, or pull the latest schedule now.'
                    : flight.awaitingRefresh
                      ? 'Still on schedule data. The daily refresh fills in the real times and aircraft, or pull them now.'
                      : 'Times and aircraft came from the flight data provider and are kept as they were on the day.'}
              </div>
              {!manual && flight.awaitingRefresh && <RefreshButton refreshing={refreshing} onClick={refresh} />}
              {refreshNote && (
                <Banner tone={refreshNote.tone} title={refreshNote.title}>
                  {refreshNote.text}
                </Banner>
              )}
            </div>
          </div>
        </div>
      </div>

      {askDelete && (
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
              border: '1px solid ' + COLORS.dangerBorder,
              borderRadius: 14,
              boxShadow: '0 30px 70px rgba(0,0,0,0.6)',
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
            }}
          >
            <div style={{ fontSize: 18, fontWeight: 600 }}>Delete this flight?</div>
            <p style={{ margin: 0, fontSize: 14, lineHeight: 1.65, color: COLORS.bodyOnCard }}>
              {flight.flightNumber ?? 'This flight'} will be removed from your log. Your distance, countries, aircraft and records
              will all be recalculated without it. This cannot be undone.
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 4, flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={() => setAskDelete(false)}
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
                }}
              >
                Keep it
              </button>
              <button
                type="button"
                onClick={remove}
                disabled={deleting}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 9,
                  height: 40,
                  padding: '0 18px',
                  borderRadius: 8,
                  background: '#C2454F',
                  border: 'none',
                  color: '#FFE8EA',
                  fontFamily: FONT_STACK,
                  fontSize: 13.5,
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                {deleting && <Spinner size={13} color="#FFE8EA" />}
                Delete flight
              </button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}

function visibilityColors(visibility: Visibility): [string, string, string] {
  if (visibility === 'PUBLIC') return ['#123F2C', '#2C6B4C', '#7FE0AE'];
  if (visibility === 'PRIVATE') return [COLORS.laterBg, COLORS.laterBorder, COLORS.laterText];
  return [COLORS.raised, COLORS.lineStrong, '#7FCFE8'];
}

function TimeRow({ label, value, dim = false }: { label: string; value: string; dim?: boolean }) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
      <span style={{ fontSize: 12, color: COLORS.textDim, width: 74 }}>{label}</span>
      <span style={{ fontSize: 16, fontVariantNumeric: 'tabular-nums', color: dim ? COLORS.textFaint : COLORS.text }}>{value}</span>
    </div>
  );
}

function DetailRow({ label, value, width = 104 }: { label: string; value: string; width?: number }) {
  return (
    <div style={{ display: 'flex', gap: 12 }}>
      <span style={{ color: COLORS.textDim, width, flexShrink: 0 }}>{label}</span>
      <span style={{ color: COLORS.text }}>{value}</span>
    </div>
  );
}

function EditButton({ editing, onClick }: { editing: boolean; onClick: () => void }) {
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
      {editing ? 'Close editor' : 'Edit'}
    </button>
  );
}

function DeleteButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 40,
      padding: '0 18px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.dangerBorder,
      background: 'transparent',
      color: COLORS.dangerText,
      fontFamily: FONT_STACK,
      fontSize: 13.5,
      fontWeight: 600,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.dangerBg },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      Delete
    </button>
  );
}

function RefreshButton({ refreshing, onClick }: { refreshing: boolean; onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      alignSelf: 'flex-start',
      gap: 9,
      height: 36,
      padding: '0 16px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.cyan,
      background: 'transparent',
      color: COLORS.cyan,
      fontFamily: FONT_STACK,
      fontSize: 13,
      fontWeight: 600,
      cursor: refreshing ? 'default' : 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.raised },
  );
  return (
    <button type="button" onClick={onClick} disabled={refreshing} {...hover}>
      {refreshing && <Spinner size={13} color={COLORS.cyan} />}
      {refreshing ? 'Refreshing' : 'Refresh flight data'}
    </button>
  );
}

function SaveEditButton({ saving, onClick }: { saving: boolean; onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 9,
      height: 40,
      padding: '0 20px',
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
      Save changes
    </button>
  );
}
