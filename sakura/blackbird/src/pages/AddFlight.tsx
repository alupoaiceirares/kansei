import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { ApiError } from '../api/client';
import { lookupFlight } from '../api/tailwind';
import type { LookupEntry } from '../api/types';
import type { BannerTone } from '../design/tokens';

const NUMBER_PATTERN = /^[A-Za-z0-9]{2,3}[0-9]{1,4}[A-Za-z]?$/;

const INPUT = {
  height: 46,
  padding: '0 14px',
  borderRadius: 9,
  background: COLORS.ground,
  fontFamily: FONT_STACK,
  fontSize: 15,
  color: COLORS.text,
  letterSpacing: '0.03em',
  outline: 'none',
  colorScheme: 'dark' as const,
  boxSizing: 'border-box' as const,
  width: '100%',
};

type Outcome = {
  tone: BannerTone;
  title: string;
  body: string;
  /** null means retrying cannot help, so the retry button reads as dead. */
  retry: string | null;
};

/** Turns the service's own answer into the banner the design defines for that case. */
function outcomeOf(error: unknown): Outcome {
  if (!(error instanceof ApiError)) {
    return {
      tone: 'error',
      title: 'Something went wrong',
      body: 'The search could not be completed. Try again in a moment, or add the flight manually.',
      retry: 'Try again',
    };
  }
  const detail = error.detail;
  if (error.status === 404) {
    return {
      tone: 'error',
      title: 'No flight found',
      body:
        detail ??
        'The provider has no record of that flight on that date. Check the flight number and the date, which is the day of departure in local time at the origin.',
      retry: 'Search again',
    };
  }
  if (error.status === 400) {
    return {
      tone: 'warning',
      title: 'That date is out of range',
      body: detail ?? 'The provider only keeps the last 365 days, so there is nothing to retry for this date.',
      retry: null,
    };
  }
  if (error.status === 429) {
    return {
      tone: 'warning',
      title: 'Too many lookups',
      body: detail ?? 'You have used your flight lookups for now. Manual entry is not limited.',
      retry: null,
    };
  }
  if (error.status === 503) {
    return {
      tone: 'warning',
      title: 'Lookup budget used up',
      body: detail ?? 'The shared budget for provider calls is spent. Nothing else about the app is affected.',
      retry: null,
    };
  }
  return {
    tone: 'error',
    title: 'Provider unavailable',
    body: detail ?? 'We could not reach the flight data provider. This is usually brief.',
    retry: 'Try again',
  };
}

function SearchButton({ onClick }: { onClick: () => void }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 9,
      height: 46,
      padding: '0 26px',
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
    <button type="submit" onClick={onClick} {...hover}>
      <svg viewBox="0 0 16 16" width={15} height={15} fill="none" stroke={COLORS.textOnOrange} strokeWidth={1.9} aria-hidden="true">
        <circle cx={7} cy={7} r={4.6} />
        <path d="M10.6 10.6 L14 14" strokeLinecap="round" />
      </svg>
      Search for this flight
    </button>
  );
}

function ManualLink() {
  const hover = useHoverStyle(
    { fontSize: 14, color: COLORS.cyan, textDecoration: 'none', borderBottom: '1px solid ' + COLORS.lineStrong, paddingBottom: 2 },
    { color: COLORS.cyanBright, borderColor: COLORS.cyan },
  );
  return (
    <Link to="/add/manual" {...hover}>
      Can't find it, or older than a year? Add it manually
    </Link>
  );
}

function ResultRow({ entry, date }: { entry: LookupEntry; date: string }) {
  const hover = useHoverStyle(
    {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 14,
      padding: '14px 16px',
      borderRadius: 10,
      border: '1px solid ' + COLORS.line,
      background: COLORS.ground,
      textDecoration: 'none',
      color: 'inherit',
      flexWrap: 'wrap' as const,
    },
    { borderColor: COLORS.cyan, background: COLORS.raised },
  );
  const flight = entry.flight;
  return (
    <Link to={'/add/confirm?flightId=' + flight.id + '&flightNumber=' + flight.flightNumber + '&date=' + date} {...hover}>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <span style={{ fontSize: 15, fontWeight: 600 }}>
          {(flight.departureAirport.iata ?? flight.departureAirport.icao) + ' → ' + (flight.arrivalAirport.iata ?? flight.arrivalAirport.icao)}
        </span>
        <span style={{ fontSize: 12.5, color: COLORS.textMuted }}>
          {flight.airline.name}
          {flight.aircraft.typeName || flight.aircraft.family ? ' · ' + (flight.aircraft.typeName ?? flight.aircraft.family) : ''}
        </span>
      </span>
      <span style={{ fontSize: 13, color: entry.alreadyInLog ? COLORS.laterText : COLORS.cyan, fontWeight: 600 }}>
        {entry.alreadyInLog ? 'Already in your log' : 'Review and confirm →'}
      </span>
    </Link>
  );
}

export function AddFlightPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [flightNumber, setFlightNumber] = useState(params.get('flightNumber') ?? '');
  const [date, setDate] = useState(params.get('date') ?? '');
  const [busy, setBusy] = useState(false);
  const [entries, setEntries] = useState<LookupEntry[] | null>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  const valid = NUMBER_PATTERN.test(flightNumber.trim());

  const run = async (number: string, day: string) => {
    setBusy(true);
    setOutcome(null);
    setEntries(null);
    try {
      const response = await lookupFlight(number.trim().toUpperCase(), day);
      setEntries(response.flights);
      // One match goes straight to the confirm screen, several stay here to be picked.
      if (response.flights.length === 1) {
        const only = response.flights[0].flight;
        navigate('/add/confirm?flightId=' + only.id + '&flightNumber=' + only.flightNumber + '&date=' + day);
      }
    } catch (error) {
      setOutcome(outcomeOf(error));
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    // The dashboard search hands the number and date over, so run it once on arrival.
    const number = params.get('flightNumber');
    const day = params.get('date');
    if (number && day && NUMBER_PATTERN.test(number)) void run(number, day);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = (event: { preventDefault: () => void }) => {
    event.preventDefault();
    if (!valid || !date || busy) return;
    void run(flightNumber, date);
  };

  return (
    <PageShell maxWidth={760}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
        <div style={{ fontSize: 11, letterSpacing: '0.2em', textTransform: 'uppercase', color: COLORS.orange }}>Add a flight</div>
        <h1 style={{ margin: 0, fontSize: 34, fontWeight: 600, letterSpacing: '-0.02em', lineHeight: 1.08 }}>Which flight were you on?</h1>
        <p style={{ margin: 0, fontSize: 15, lineHeight: 1.65, color: COLORS.textMuted, maxWidth: 520, textWrap: 'pretty' }}>
          One flight is one takeoff and one landing. If you connected somewhere, add each leg separately and group them into a
          journey afterwards.
        </p>
      </div>

      <form
        onSubmit={submit}
        style={{
          padding: 28,
          background: COLORS.surface,
          border: '1px solid ' + COLORS.lineStrong,
          borderRadius: 14,
          display: 'flex',
          flexDirection: 'column',
          gap: 20,
        }}
      >
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.1fr) minmax(0,1fr)', gap: 16 }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Flight number</span>
            <input
              type="text"
              value={flightNumber}
              onChange={(event) => setFlightNumber(event.target.value)}
              placeholder="LH400"
              spellCheck={false}
              style={{ ...INPUT, border: '1px solid ' + (flightNumber && !valid ? '#C2454F' : COLORS.lineStrong) }}
            />
            <span style={{ fontSize: 11.5, color: flightNumber && !valid ? COLORS.dangerText : COLORS.textDim }}>
              {flightNumber && !valid
                ? 'Two or three letters or digits, then one to four digits.'
                : 'For example LH400, BA2490, W63021.'}
            </span>
          </label>

          <label style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>Date of departure</span>
            <input
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
              style={{ ...INPUT, border: '1px solid ' + COLORS.lineStrong }}
            />
            <span style={{ fontSize: 11.5, color: COLORS.textDim }}>The provider keeps the last 365 days.</span>
          </label>
        </div>

        <div style={{ height: 1, background: COLORS.line }} />

        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
          {busy ? (
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 10,
                height: 46,
                padding: '0 26px',
                borderRadius: 9,
                background: '#B85610',
                color: '#FFE2CB',
                fontSize: 15,
                fontWeight: 700,
                whiteSpace: 'nowrap',
              }}
            >
              <Spinner size={14} color="#FFE2CB" />
              Searching
            </span>
          ) : (
            <SearchButton onClick={() => undefined} />
          )}
          <ManualLink />
        </div>

        {busy && (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
              padding: 18,
              background: '#082834',
              border: '1px solid ' + COLORS.line,
              borderRadius: 11,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 9, fontSize: 13, color: COLORS.textMuted }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: COLORS.cyan, display: 'inline-block' }} />
              Asking the flight data provider. This can take a few seconds.
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
              {['38%', '74%', '56%'].map((width, index) => (
                <span
                  key={width}
                  style={{
                    height: 12,
                    width,
                    borderRadius: 4,
                    background: COLORS.skeleton,
                    animation: `bbSkeleton 1.5s ease-in-out ${index * 0.2}s infinite`,
                  }}
                />
              ))}
            </div>
          </div>
        )}
      </form>

      {outcome && (
        <Banner tone={outcome.tone} title={outcome.title}>
          {outcome.body}
          <div style={{ marginTop: 8, display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
            <Link to="/add/manual" style={{ fontSize: 13.5, fontWeight: 600 }}>
              Add this flight manually
            </Link>
            <span
              onClick={() => outcome.retry && run(flightNumber, date)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                height: 34,
                padding: '0 16px',
                borderRadius: 8,
                border: '1px solid ' + (outcome.retry ? COLORS.cyan : '#123240'),
                color: outcome.retry ? COLORS.cyan : COLORS.textFaint,
                fontSize: 13,
                fontWeight: 600,
                cursor: outcome.retry ? 'pointer' : 'not-allowed',
                whiteSpace: 'nowrap',
              }}
            >
              {outcome.retry ?? 'Search will not help here'}
            </span>
          </div>
        </Banner>
      )}

      {entries && entries.length > 1 && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            padding: 20,
            background: COLORS.surfaceOverlay,
            border: '1px solid ' + COLORS.line,
            borderRadius: 12,
          }}
        >
          <div style={{ fontSize: 11, letterSpacing: '0.16em', textTransform: 'uppercase', color: COLORS.textDim }}>
            {entries.length} legs flew under that number
          </div>
          {entries.map((entry) => (
            <ResultRow key={entry.flight.id} entry={entry} date={date} />
          ))}
        </div>
      )}

      {entries && entries.length === 0 && (
        <Banner tone="error" title="No flight found">
          The provider answered, but nothing matched that number on that date.
        </Banner>
      )}

      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          padding: '20px 22px',
          background: 'rgba(10,44,57,0.6)',
          border: '1px solid ' + COLORS.line,
          borderRadius: 12,
        }}
      >
        <div style={{ fontSize: 11, letterSpacing: '0.16em', textTransform: 'uppercase', color: COLORS.textDim }}>
          Why a search button, not live results
        </div>
        <div style={{ fontSize: 13.5, lineHeight: 1.65, color: COLORS.textMuted, textWrap: 'pretty' }}>
          Each lookup is a paid call to an external provider, so nothing is fetched while you type. Manual entry never calls the
          provider and always works, including for flights older than a year, charters and anything the provider has never heard
          of.
        </div>
      </div>
    </PageShell>
  );
}
