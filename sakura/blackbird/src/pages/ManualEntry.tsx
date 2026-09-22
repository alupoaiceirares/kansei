import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { Segmented } from '../components/Segmented';
import { ReferencePicker } from '../components/ReferencePicker';
import { ApiError } from '../api/client';
import { addManualFlight, fetchAirlinesForFlightNumber, searchAircraftTypes, searchAirlines, searchAirports } from '../api/tailwind';
import type {
  AircraftTypeOption,
  AirlineOption,
  AirportOption,
  CabinClass,
  SeatPosition,
  TripReason,
  Visibility,
} from '../api/types';
import { useSession } from '../session';
import { formatDate, formatKm } from '../format';

const VIS_HINT: Record<Visibility, string> = {
  PUBLIC: 'Anyone can see it.',
  FRIENDS: 'Your friends on WTW only.',
  PRIVATE: 'Only you — still counts in your own numbers.',
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

const NUMBER_PATTERN = /^[A-Z0-9]{2,3}\d{1,4}[A-Z]?$/;

const LABEL = { fontSize: 12, fontWeight: 500, color: COLORS.textMuted };
const OPTIONAL = { color: COLORS.textDim, fontWeight: 400 };

function inputStyle(invalid: boolean) {
  return {
    height: 42,
    padding: '0 13px',
    borderRadius: 8,
    background: COLORS.ground,
    border: '1px solid ' + (invalid ? '#C2454F' : COLORS.lineStrong),
    fontFamily: FONT_STACK,
    fontSize: 14,
    color: COLORS.text,
    outline: 'none',
    colorScheme: 'dark' as const,
    boxSizing: 'border-box' as const,
    width: '100%',
  };
}

function SaveButton({ saving, onClick }: { saving: boolean; onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 9,
      height: 46,
      padding: '0 24px',
      borderRadius: 9,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 15,
      fontWeight: 700,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="submit" onClick={onClick} disabled={saving} {...hover}>
      {saving && <Spinner size={14} color={COLORS.textOnOrange} />}
      Add this flight
    </button>
  );
}

type Saved = { id: number; route: string; date: string; distanceKm: number };

/** Never calls the flight data provider, so it always works: old flights, charters, anything. */
export function ManualEntryPage() {
  const navigate = useNavigate();
  const { me } = useSession();

  const [date, setDate] = useState('');
  const [from, setFrom] = useState<AirportOption | null>(null);
  const [to, setTo] = useState<AirportOption | null>(null);
  const [airline, setAirline] = useState<AirlineOption | null>(null);
  const [aircraftType, setAircraftType] = useState<AircraftTypeOption | null>(null);
  const [flightNumber, setFlightNumber] = useState('');
  const [cargo, setCargo] = useState(false);
  const [visibility, setVisibility] = useState<Visibility>(me?.defaultVisibility ?? 'FRIENDS');
  const [seat, setSeat] = useState('');
  const [seatPosition, setSeatPosition] = useState<SeatPosition | null>(null);
  const [cabinClass, setCabinClass] = useState<CabinClass | null>(null);
  const [reason, setReason] = useState<TripReason | null>(null);
  const [notes, setNotes] = useState('');

  const [departureTime, setDepartureTime] = useState('');
  const [arrivalTime, setArrivalTime] = useState('');

  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<Saved | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [suggestedAirline, setSuggestedAirline] = useState<AirlineOption | null>(null);

  const airportSearch = useCallback((query: string) => searchAirports(query), []);
  const airlineSearch = useCallback((query: string) => searchAirlines(query), []);
  const aircraftSearch = useCallback((query: string) => searchAircraftTypes(query), []);

  const sameAirport = !!from && !!to && from.id === to.id;
  const numberInvalid = flightNumber.trim().length > 0 && !NUMBER_PATTERN.test(flightNumber.trim().toUpperCase());

  /**
   * A flight number names its airline, so the number fills the airline in when it is still blank. The user
   * can still pick a different one, and their own choice is never overwritten.
   */
  useEffect(() => {
    const number = flightNumber.trim().toUpperCase();
    if (!NUMBER_PATTERN.test(number)) {
      setSuggestedAirline(null);
      return;
    }
    let live = true;
    const timer = window.setTimeout(() => {
      fetchAirlinesForFlightNumber(number)
        .then((matches) => {
          if (!live) return;
          const match = matches[0] ?? null;
          setSuggestedAirline(match);
          setAirline((current) => current ?? match);
        })
        .catch(() => undefined);
    }, 250);
    return () => {
      live = false;
      window.clearTimeout(timer);
    };
  }, [flightNumber]);

  const submit = async (event: { preventDefault: () => void }) => {
    event.preventDefault();
    if (saving) return;

    const found: string[] = [];
    if (!date) found.push('a date of departure');
    if (!from) found.push('an origin airport');
    if (!to) found.push('a destination airport');
    if (!airline) found.push('an airline');
    if (sameAirport) found.push('two different airports');
    if (numberInvalid) found.push('a flight number like LH400, or none at all');
    if (found.length > 0) {
      setErrors(found);
      return;
    }

    setErrors([]);
    setSaving(true);
    try {
      const result = await addManualFlight({
        flightNumber: flightNumber.trim() ? flightNumber.trim().toUpperCase() : null,
        date,
        airlineId: airline!.id,
        departureAirportId: from!.id,
        arrivalAirportId: to!.id,
        aircraftTypeId: aircraftType?.id ?? null,
        departureTime: departureTime || null,
        arrivalTime: arrivalTime || null,
        cargo,
        visibility,
        seat: seat.trim() || null,
        seatPosition,
        cabinClass,
        reason,
        notes: notes.trim() || null,
      });
      setSaved({
        id: result.id,
        route: (from!.iata ?? from!.icao) + ' → ' + (to!.iata ?? to!.icao),
        date,
        distanceKm: result.flight.distanceKm,
      });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (cause) {
      setErrors([cause instanceof ApiError ? (cause.detail ?? 'The flight could not be saved.') : 'The flight could not be saved.']);
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageShell maxWidth={940}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
        <Link to="/add" style={{ fontSize: 13, color: COLORS.textMuted }}>
          ← Back to search
        </Link>
        <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>Add a flight by hand</h1>
        <p style={{ margin: 0, fontSize: 15, lineHeight: 1.65, color: COLORS.textMuted, maxWidth: 580, textWrap: 'pretty' }}>
          Nothing here calls the flight data provider, so this always works — for old flights, charters, and anything search has
          never heard of. Only the fields marked required are needed.
        </p>
      </div>

      {saved && (
        <Banner tone="success" title="Flight added">
          {saved.route} on {formatDate(saved.date)} is in your log. Distance was calculated from the two airports:{' '}
          {formatKm(saved.distanceKm)}.
          <div style={{ marginTop: 6 }}>
            <Link to={'/flights/' + saved.id} style={{ fontSize: 13.5, fontWeight: 600 }}>
              Open the flight
            </Link>
          </div>
        </Banner>
      )}

      {errors.length > 0 && (
        <Banner tone="error" title={errors.length === 1 ? 'One thing needs fixing' : errors.length + ' things need fixing'}>
          This flight needs {errors.join(', ')}.
        </Banner>
      )}

      <form
        onSubmit={submit}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 18,
          padding: 28,
          background: COLORS.surface,
          border: '1px solid ' + COLORS.lineStrong,
          borderRadius: 14,
        }}
      >
        <div style={{ fontSize: 11, letterSpacing: '0.16em', textTransform: 'uppercase', color: COLORS.textDim }}>Required</div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 16 }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>Date of departure</span>
            <input type="date" value={date} onChange={(event) => setDate(event.target.value)} style={inputStyle(false)} />
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>Any date, including decades ago.</span>
          </label>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>From</span>
            <ReferencePicker
              value={from}
              onChange={setFrom}
              search={airportSearch}
              labelOf={(airport) => (airport.iata ?? airport.icao ?? '') + ' — ' + airport.name}
              metaOf={(airport) => [airport.city, airport.countryCode].filter(Boolean).join(', ')}
              placeholder="OTP — Bucharest Otopeni"
              invalid={sameAirport}
            />
            <span style={{ fontSize: 11.5, color: sameAirport ? COLORS.dangerText : COLORS.textDim }}>
              {sameAirport ? 'Origin and destination cannot be the same airport.' : 'IATA code or city name.'}
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>To</span>
            <ReferencePicker
              value={to}
              onChange={setTo}
              search={airportSearch}
              labelOf={(airport) => (airport.iata ?? airport.icao ?? '') + ' — ' + airport.name}
              metaOf={(airport) => [airport.city, airport.countryCode].filter(Boolean).join(', ')}
              placeholder="IST — Istanbul"
              invalid={sameAirport}
            />
            <span style={{ fontSize: 11.5, color: sameAirport ? COLORS.dangerText : COLORS.textDim }}>
              {sameAirport ? 'Pick a different destination.' : 'IATA code or city name.'}
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>Airline</span>
            <ReferencePicker
              value={airline}
              onChange={setAirline}
              search={airlineSearch}
              labelOf={(item) => item.name}
              metaOf={(item) => [item.iata, item.icao].filter(Boolean).join(' · ')}
              placeholder="Turkish Airlines"
            />
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>
              {suggestedAirline
                ? 'Filled in from the flight number. Change it if it was a different carrier.'
                : 'Picked from the airline list, or filled in once you type a flight number.'}
            </span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>Who can see it</span>
            <Segmented options={VISIBILITIES} value={visibility} onChange={(value) => value && setVisibility(value)} columns={3} visibility />
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{VIS_HINT[visibility]}</span>
          </div>
        </div>

        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 11,
            padding: '13px 15px',
            borderRadius: 10,
            background: '#0C3241',
            border: '1px solid ' + COLORS.lineStrong,
          }}
        >
          <svg viewBox="0 0 16 16" width={15} height={15} style={{ marginTop: 2, flexShrink: 0 }} fill="none" stroke="#7FCFE8" strokeWidth={1.7} aria-hidden="true">
            <circle cx={8} cy={8} r={6.4} />
            <path d="M8 7.2 V11.4" strokeLinecap="round" />
            <circle cx={8} cy={4.9} r={0.9} fill="#7FCFE8" stroke="none" />
          </svg>
          <span style={{ fontSize: 13, lineHeight: 1.6, color: COLORS.bodyOnCard }}>
            Distance is calculated from the two airports, so it is always available even when everything else is blank. Time in
            air needs recorded times, and flights without them are simply left out of that one statistic.
          </span>
        </div>

        <div style={{ height: 1, background: COLORS.line }} />
        <div style={{ fontSize: 11, letterSpacing: '0.16em', textTransform: 'uppercase', color: COLORS.textDim }}>Optional</div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 16 }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Flight number <span style={OPTIONAL}>— optional</span>
            </span>
            <input
              type="text"
              value={flightNumber}
              onChange={(event) => setFlightNumber(event.target.value)}
              placeholder="TK1044"
              spellCheck={false}
              style={inputStyle(numberInvalid)}
            />
            {numberInvalid && <span style={{ fontSize: 11.5, color: COLORS.dangerText }}>Two or three letters or digits, then one to four digits.</span>}
          </label>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Aircraft type <span style={OPTIONAL}>— optional</span>
            </span>
            <ReferencePicker
              value={aircraftType}
              onChange={setAircraftType}
              search={aircraftSearch}
              labelOf={(item) => item.name}
              metaOf={(item) => [item.manufacturer, item.icaoCode].filter(Boolean).join(' · ')}
              placeholder="A321neo"
            />
          </div>

          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Departure time <span style={OPTIONAL}>— optional</span>
            </span>
            <input type="time" value={departureTime} onChange={(event) => setDepartureTime(event.target.value)} style={inputStyle(false)} />
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>Local time at the airport.</span>
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Arrival time <span style={OPTIONAL}>— optional</span>
            </span>
            <input type="time" value={arrivalTime} onChange={(event) => setArrivalTime(event.target.value)} style={inputStyle(false)} />
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>
              Local time at the airport. A flight that lands after midnight is counted into the next day on its own.
            </span>
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Seat <span style={OPTIONAL}>— optional</span>
            </span>
            <input type="text" value={seat} onChange={(event) => setSeat(event.target.value)} placeholder="e.g. 12F" style={inputStyle(false)} />
          </label>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Position <span style={OPTIONAL}>— optional</span>
            </span>
            <Segmented options={POSITIONS} value={seatPosition} onChange={setSeatPosition} columns={3} clearable />
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Cabin <span style={OPTIONAL}>— optional</span>
            </span>
            <Segmented options={CABINS} value={cabinClass} onChange={setCabinClass} columns={4} clearable />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={LABEL}>
              Reason <span style={OPTIONAL}>— optional</span>
            </span>
            <Segmented options={REASONS} value={reason} onChange={setReason} columns={2} clearable />
          </div>
        </div>

        <label style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13, color: COLORS.textMuted, cursor: 'pointer' }}>
          <input type="checkbox" checked={cargo} onChange={(event) => setCargo(event.target.checked)} style={{ accentColor: COLORS.orange }} />
          This was a cargo flight
        </label>

        <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          <span style={LABEL}>
            Notes <span style={OPTIONAL}>— optional</span>
          </span>
          <textarea
            rows={3}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="Anything worth remembering about this flight"
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

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <SaveButton saving={saving} onClick={() => undefined} />
          <button
            type="button"
            onClick={() => navigate('/add')}
            style={{ background: 'none', border: 'none', fontFamily: FONT_STACK, fontSize: 13.5, color: COLORS.textMuted, cursor: 'pointer' }}
          >
            Cancel
          </button>
          <span style={{ marginLeft: 'auto', fontSize: 12.5, color: COLORS.textDim }}>Nothing here is sent to a provider.</span>
        </div>
      </form>
    </PageShell>
  );
}
