import { useEffect, useState, type CSSProperties } from 'react';
import { Link, useParams } from 'react-router-dom';
import { COLORS, FONT_STACK, pill } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { ApiError } from '../api/client';
import {
  acceptFriendRequest,
  fetchFriendRequests,
  fetchFriends,
  fetchJoinRequests,
  fetchMyFlights,
  joinFlight,
  fetchUserFlights,
  removeFriend,
  sendFriendRequest,
} from '../api/tailwind';
import { fetchOtherProfile, fetchOwnComparableProfile, type OtherProfile } from '../api/profile';
import type { UserFlight } from '../api/types';
import { aircraftLabel, routeOf } from '../components/FlightFlags';
import { useSession } from '../session';
import { earthLaps, formatDate, formatKm, formatNumber, initialsOf } from '../format';

const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

const ROW: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '88px 64px minmax(0,1fr) 78px 14px',
  gap: 11,
  alignItems: 'center',
  padding: '12px 20px',
  borderBottom: '1px solid ' + COLORS.lineSoft,
  fontSize: 13,
  textDecoration: 'none',
  color: 'inherit',
};

type Relation = 'FRIENDS' | 'REQUEST_SENT' | 'REQUEST_RECEIVED' | 'NONE';

function PrimaryAction({ label, onClick, busy }: { label: string; onClick: () => void; busy: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 40,
      padding: '0 18px',
      borderRadius: 8,
      background: COLORS.orange,
      border: '1px solid transparent',
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
    <button type="button" onClick={onClick} disabled={busy} {...hover}>
      {busy && <Spinner size={13} color={COLORS.textOnOrange} />}
      {label}
    </button>
  );
}

function DangerAction({ label, onClick, busy }: { label: string; onClick: () => void; busy: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
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
    <button type="button" onClick={onClick} disabled={busy} {...hover}>
      {busy && <Spinner size={13} color={COLORS.dangerText} />}
      {label}
    </button>
  );
}

/** Where "I was on this too" stands for one of their flights, from the caller's side. */
type JoinState = { kind: 'none' } | { kind: 'mine'; userFlightId: number | null } | { kind: 'requested' } | { kind: 'busy' };

function FlightRow({
  userFlight,
  state,
  isFriend,
  onJoin,
}: {
  userFlight: UserFlight;
  state: JoinState;
  isFriend: boolean;
  onJoin: () => void;
}) {
  // Between friends a looked-up flight is added at once, anything else asks the owner first
  const direct = isFriend && userFlight.flight.source === 'API';
  return (
    <div style={{ ...ROW, gridTemplateColumns: '88px 64px minmax(0,1fr) 78px auto' }}>
      <span style={{ color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>{formatDate(userFlight.flight.flightDate)}</span>
      <span style={{ color: COLORS.bodyOnCard, fontWeight: 600, letterSpacing: '0.03em' }}>{userFlight.flight.flightNumber ?? '—'}</span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span style={{ color: COLORS.text, fontWeight: 500 }}>{routeOf(userFlight)}</span>
        <span style={{ fontSize: 11.5, color: COLORS.textDim }}>{aircraftLabel(userFlight)}</span>
      </span>
      <span style={{ color: COLORS.textMuted, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
        {formatNumber(Math.round(userFlight.flight.distanceKm))}
      </span>
      <span style={{ textAlign: 'right', fontSize: 12, whiteSpace: 'nowrap' }}>
        {state.kind === 'mine' &&
          (state.userFlightId ? (
            <Link to={'/flights/' + state.userFlightId} style={{ fontWeight: 600 }}>
              In your log
            </Link>
          ) : (
            <span style={{ color: COLORS.textMuted }}>In your log</span>
          ))}
        {state.kind === 'requested' && <span style={{ color: COLORS.textMuted }}>Asked</span>}
        {state.kind === 'busy' && <Spinner size={12} />}
        {state.kind === 'none' && (
          <button
            type="button"
            onClick={onJoin}
            title={direct ? 'Adds it to your log' : 'Asks them to confirm you were on it'}
            style={{ background: 'none', border: 'none', padding: 0, fontFamily: FONT_STACK, fontSize: 12, fontWeight: 600, color: COLORS.cyan, cursor: 'pointer' }}
          >
            I was on this too
          </button>
        )}
      </span>
    </div>
  );
}

function CompareBar({ label, mine, theirs, format }: { label: string; mine: number; theirs: number; format: (value: number) => string }) {
  const total = mine + theirs;
  const minePct = total > 0 ? Math.round((mine / total) * 100) : 50;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12 }}>
        <span style={{ fontSize: 12.5, color: COLORS.textMuted }}>{label}</span>
        <span style={{ fontSize: 12.5, color: COLORS.textDim }}>
          <span style={{ color: '#FFB067', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{format(mine)}</span>
          <span style={{ padding: '0 6px' }}>vs</span>
          <span style={{ color: '#7FCFE8', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{format(theirs)}</span>
        </span>
      </div>
      <div style={{ display: 'flex', gap: 4, height: 9 }}>
        <span style={{ width: minePct + '%', background: COLORS.orange, borderRadius: '4px 0 0 4px' }} />
        <span style={{ width: 100 - minePct + '%', background: '#1B6D84', borderRadius: '0 4px 4px 0' }} />
      </div>
    </div>
  );
}

export function ProfileOtherPage() {
  const { userId } = useParams();
  const { refreshRequests } = useSession();

  const [profile, setProfile] = useState<OtherProfile | null>(null);
  const [mine, setMine] = useState<OtherProfile | null>(null);
  const [flights, setFlights] = useState<UserFlight[]>([]);
  const [username, setUsername] = useState<string | null>(null);
  const [relation, setRelation] = useState<Relation>('NONE');
  const [friendsSince, setFriendsSince] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // My own entry per flight id, and their entries I already asked to join
  const [mineByFlight, setMineByFlight] = useState<Map<number, number>>(new Map());
  const [asked, setAsked] = useState<Set<number>>(new Set());
  const [joining, setJoining] = useState<number | null>(null);

  const load = async () => {
    if (!userId) return;
    const [theirProfile, ownProfile, theirFlights, friends, requests, myFlights, joinRequests] = await Promise.all([
      fetchOtherProfile(userId).catch(() => null),
      fetchOwnComparableProfile().catch(() => null),
      fetchUserFlights(userId).catch(() => []),
      fetchFriends().catch(() => []),
      fetchFriendRequests().catch(() => []),
      fetchMyFlights().catch(() => []),
      fetchJoinRequests().catch(() => []),
    ]);

    const friend = friends.find((item) => item.userId === userId);
    const request = requests.find((item) => item.userId === userId);
    setRelation(friend ? 'FRIENDS' : request ? (request.direction === 'INCOMING' ? 'REQUEST_RECEIVED' : 'REQUEST_SENT') : 'NONE');
    setFriendsSince(friend?.friendsSince ?? null);
    setUsername(friend?.username ?? request?.username ?? null);
    setProfile(theirProfile);
    setMine(ownProfile);
    setFlights(theirFlights);
    setMineByFlight(new Map(myFlights.map((entry) => [entry.flight.id, entry.id])));
    setAsked(new Set(joinRequests.filter((request) => request.direction === 'OUTGOING').map((request) => request.userFlightId)));
    setLoading(false);
  };

  useEffect(() => {
    setLoading(true);
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  const joinStateOf = (userFlight: UserFlight): JoinState => {
    if (joining === userFlight.id) return { kind: 'busy' };
    if (mineByFlight.has(userFlight.flight.id)) return { kind: 'mine', userFlightId: mineByFlight.get(userFlight.flight.id) ?? null };
    if (asked.has(userFlight.id)) return { kind: 'requested' };
    return { kind: 'none' };
  };

  const join = async (userFlight: UserFlight) => {
    setJoining(userFlight.id);
    setError(null);
    try {
      const result = await joinFlight(userFlight.id);
      if (result.outcome === 'ADDED' && result.userFlight) {
        const added = result.userFlight;
        setMineByFlight((current) => new Map(current).set(added.flight.id, added.id));
      } else {
        setAsked((current) => new Set(current).add(userFlight.id));
      }
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'That did not work.') : 'That did not work.');
    } finally {
      setJoining(null);
    }
  };

  const act = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      await load();
      void refreshRequests();
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'That did not work.') : 'That did not work.');
    } finally {
      setBusy(false);
    }
  };

  if (loading || !userId) {
    return (
      <PageShell maxWidth={1100}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading the profile
        </div>
      </PageShell>
    );
  }

  if (!profile) {
    return (
      <PageShell maxWidth={1100}>
        <Banner tone="error" title="Profile not available">
          {error ?? 'That user has not joined WTW, or their profile could not be loaded.'}
        </Banner>
        <Link to="/friends" style={{ fontSize: 13.5 }}>
          ← Friends
        </Link>
      </PageShell>
    );
  }

  const name = username ?? 'This user';
  const isFriend = relation === 'FRIENDS';
  const shared = profile.stats.flightCount;
  // A log with nothing shared is either private throughout, or friends-only to someone who is not a friend
  const nothingShared = shared === 0;

  const theirFamilies = new Set(profile.aircraft.map((family) => family.family));
  const myFamilies = new Set((mine?.aircraft ?? []).map((family) => family.family));
  const envy = [...theirFamilies].filter((family) => !myFamilies.has(family)).slice(0, 8);

  const stats = [
    {
      label: 'Distance',
      value: formatKm(profile.stats.distanceKm),
      note: earthLaps(profile.stats.distanceKm) + ' times around',
      color: COLORS.text,
    },
    {
      label: 'Flights',
      value: formatNumber(profile.stats.flightCount),
      note: profile.records.firstFlight ? 'since ' + profile.records.firstFlight.date.slice(0, 4) : 'shared with you',
      color: COLORS.text,
    },
    {
      label: 'Countries',
      value: formatNumber(profile.countries.visited.length),
      note: 'plus ' + profile.countries.passedThrough.length + ' in transit',
      color: COLORS.cyan,
    },
    {
      label: 'Aircraft',
      value: formatNumber(profile.stats.aircraftFamilyCount),
      note: 'families flown',
      color: COLORS.cyan,
    },
  ];

  const recent = [...flights].sort((a, b) => b.flight.flightDate.localeCompare(a.flight.flightDate)).slice(0, 5);

  return (
    <PageShell maxWidth={1100}>
      <Link to="/friends" style={{ fontSize: 13, color: COLORS.textMuted }}>
        ← Friends
      </Link>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 20,
          flexWrap: 'wrap',
          padding: '24px 26px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.line,
          borderRadius: 14,
        }}
      >
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 72,
            height: 72,
            borderRadius: '50%',
            background: COLORS.raised,
            border: '1px solid ' + COLORS.lineStrong,
            fontSize: 22,
            fontWeight: 600,
            color: COLORS.textMuted,
            flexShrink: 0,
          }}
        >
          {initialsOf(username)}
        </span>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 5, minWidth: 0, flex: '1 1 220px' }}>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em' }}>{name}</h1>
          <span style={{ fontSize: 13.5, color: COLORS.textDim }}>
            {profile.records.firstFlight ? 'flying since ' + profile.records.firstFlight.date.slice(0, 4) : 'on WTW'}
          </span>
          <span
            style={{
              ...pill,
              alignSelf: 'flex-start',
              padding: '4px 10px',
              fontSize: 11,
              ...(isFriend
                ? { background: '#123F2C', borderColor: '#2C6B4C', color: '#7FE0AE' }
                : { background: COLORS.raised, borderColor: COLORS.lineStrong, color: COLORS.textMuted }),
            }}
          >
            {isFriend
              ? friendsSince
                ? 'FRIENDS SINCE ' + new Date(friendsSince).toLocaleDateString('en-GB', { month: 'short', year: 'numeric' }).toUpperCase()
                : 'FRIENDS'
              : relation === 'REQUEST_SENT'
                ? 'REQUEST SENT'
                : relation === 'REQUEST_RECEIVED'
                  ? 'WANTS TO BE FRIENDS'
                  : 'NOT CONNECTED'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
          {isFriend && <DangerAction label="Remove friend" busy={busy} onClick={() => act(() => removeFriend(userId))} />}
          {relation === 'NONE' && <PrimaryAction label="Send request" busy={busy} onClick={() => act(() => sendFriendRequest(userId))} />}
          {relation === 'REQUEST_SENT' && <DangerAction label="Cancel request" busy={busy} onClick={() => act(() => removeFriend(userId))} />}
          {relation === 'REQUEST_RECEIVED' && (
            <PrimaryAction label="Accept request" busy={busy} onClick={() => act(() => acceptFriendRequest(userId))} />
          )}
        </div>
      </div>

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}

      {nothingShared ? (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 15,
            padding: '68px 28px',
            background: 'rgba(10,44,57,0.7)',
            border: '1px dashed ' + COLORS.lineStrong,
            borderRadius: 14,
            textAlign: 'center',
          }}
        >
          <svg viewBox="0 0 48 48" width={44} height={44} fill="none" stroke={COLORS.lineStrong} strokeWidth={2} aria-hidden="true">
            <rect x={11} y={21} width={26} height={19} rx={3} />
            <path d="M17 21 v-5 a7 7 0 0 1 14 0 v5" />
          </svg>
          <div style={{ fontSize: 18, fontWeight: 600 }}>{isFriend ? 'This log is private' : 'Nothing shared publicly'}</div>
          <div style={{ fontSize: 13.5, color: COLORS.textMuted, maxWidth: 380, lineHeight: 1.6 }}>
            {isFriend
              ? name + ' keeps their flights to themselves. Being friends does not change it, they would need to set flights to friends or public.'
              : 'You are seeing only public flights, and there are none. Send a request and they can accept to share the rest.'}
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          {!isFriend && (
            <Banner tone="info" title="Only public flights shown">
              You are seeing {shared} of {name}'s flights. The rest are friends-only. Send a request and they can accept to share
              the full log.
            </Banner>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 16 }}>
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

          {mine && (
            <div style={CARD}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '16px 20px',
                  borderBottom: '1px solid ' + COLORS.line,
                  flexWrap: 'wrap',
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 600 }}>You against {name}</span>
                <span style={{ fontSize: 12, color: COLORS.textDim }}>Counting only what they share with you</span>
              </div>
              <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 17 }}>
                <CompareBar label="Distance" mine={mine.stats.distanceKm} theirs={profile.stats.distanceKm} format={formatKm} />
                <CompareBar label="Flights" mine={mine.stats.flightCount} theirs={profile.stats.flightCount} format={formatNumber} />
                <CompareBar
                  label="Countries"
                  mine={mine.countries.visited.length}
                  theirs={profile.countries.visited.length}
                  format={formatNumber}
                />
                <CompareBar
                  label="Aircraft families"
                  mine={mine.stats.aircraftFamilyCount}
                  theirs={profile.stats.aircraftFamilyCount}
                  format={formatNumber}
                />
              </div>
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1.45fr) minmax(0,1fr)', gap: 18, alignItems: 'start' }}>
            <div style={CARD}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '15px 20px',
                  borderBottom: '1px solid ' + COLORS.line,
                  flexWrap: 'wrap',
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 600 }}>Their flights</span>
                <span style={{ fontSize: 12, color: COLORS.textDim }}>Only what they share with you</span>
              </div>
              {recent.map((userFlight) => (
                <FlightRow
                  key={userFlight.id}
                  userFlight={userFlight}
                  isFriend={isFriend}
                  onJoin={() => join(userFlight)}
                  state={joinStateOf(userFlight)}
                />
              ))}
              <div style={{ padding: '13px 20px', fontSize: 12.5, color: COLORS.textDim }}>
                Showing {recent.length} of {flights.length} shared flights.
              </div>
            </div>

            <div style={CARD}>
              <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>
                Aircraft they have that you do not
              </div>
              <div style={{ padding: '17px 20px', display: 'flex', flexDirection: 'column', gap: 13 }}>
                <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
                  {envy.map((family) => (
                    <span
                      key={family}
                      style={{
                        padding: '6px 12px',
                        borderRadius: 999,
                        background: '#2E1D0C',
                        border: '1px solid #7A4A16',
                        color: '#FFB067',
                        fontSize: 12.5,
                        fontWeight: 500,
                      }}
                    >
                      {family}
                    </span>
                  ))}
                  {envy.length === 0 && (
                    <span style={{ fontSize: 13, color: COLORS.textMuted }}>Nothing in their collection is missing from yours.</span>
                  )}
                </div>
                {envy.length > 0 && (
                  <div style={{ fontSize: 12.5, lineHeight: 1.6, color: COLORS.textMuted }}>
                    {envy.length} {envy.length === 1 ? 'family' : 'families'} in their collection still locked in yours.
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}
