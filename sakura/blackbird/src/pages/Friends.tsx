import { useCallback, useEffect, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { COLORS, FONT_STACK } from '../design/tokens';
import { useHoverStyle } from '../design/useHover';
import { PageShell } from '../components/PageShell';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import {
  acceptFriendRequest,
  declineFriendRequest,
  fetchFriendRequests,
  fetchFriends,
  removeFriend,
  searchUsers,
  sendFriendRequest,
} from '../api/tailwind';
import { fetchFriendTotals, type FriendTotals } from '../api/profile';
import { ApiError } from '../api/client';
import type { Friend, FriendRequest, UserSearchResult } from '../api/types';
import { useSession } from '../session';
import { formatKm, formatNumber, initialsOf } from '../format';

const CARD: CSSProperties = {
  background: COLORS.surfaceOverlay,
  border: '1px solid ' + COLORS.line,
  borderRadius: 14,
  overflow: 'hidden',
};

const AVATAR: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 40,
  height: 40,
  borderRadius: '50%',
  background: COLORS.raised,
  border: '1px solid ' + COLORS.lineStrong,
  fontSize: 13,
  fontWeight: 600,
  color: COLORS.textMuted,
  flexShrink: 0,
};

const ROW_BORDER = '1px solid ' + COLORS.lineSoft;

const FRIEND_GRID: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '40px minmax(0,1.2fr) minmax(0,1fr) minmax(0,1fr) auto',
  gap: 14,
  alignItems: 'center',
  padding: '13px 20px',
  borderBottom: ROW_BORDER,
  textDecoration: 'none',
  color: 'inherit',
};

function SearchButton() {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 44,
      padding: '0 22px',
      borderRadius: 9,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 14,
      fontWeight: 700,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="submit" {...hover}>
      Search
    </button>
  );
}

function AcceptButton({ onClick, busy }: { onClick: () => void; busy: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 36,
      padding: '0 16px',
      borderRadius: 8,
      background: COLORS.orange,
      border: 'none',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 13,
      fontWeight: 700,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={onClick} disabled={busy} {...hover}>
      {busy && <Spinner size={12} color={COLORS.textOnOrange} />}
      Accept
    </button>
  );
}

function QuietButton({ label, onClick, danger = false }: { label: string; onClick: () => void; danger?: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      height: 36,
      padding: '0 16px',
      borderRadius: 8,
      border: '1px solid ' + COLORS.lineStrong,
      background: 'transparent',
      color: COLORS.textMuted,
      fontFamily: FONT_STACK,
      fontSize: 13,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    },
    danger ? { color: COLORS.dangerText, borderColor: COLORS.dangerBorder } : { color: COLORS.text, borderColor: COLORS.cyan },
  );
  return (
    <button type="button" onClick={onClick} {...hover}>
      {label}
    </button>
  );
}

function AddFriendButton({ onClick, busy }: { onClick: () => void; busy: boolean }) {
  const hover = useHoverStyle(
    {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      height: 36,
      padding: '0 16px',
      borderRadius: 8,
      background: COLORS.orange,
      border: '1px solid transparent',
      color: COLORS.textOnOrange,
      fontFamily: FONT_STACK,
      fontSize: 13,
      fontWeight: 600,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
      flexShrink: 0,
    },
    { background: COLORS.orangeHover },
  );
  return (
    <button type="button" onClick={onClick} disabled={busy} {...hover}>
      {busy && <Spinner size={12} color={COLORS.textOnOrange} />}
      Add friend
    </button>
  );
}

function StateChip({ label, tone }: { label: string; tone: 'friend' | 'pending' }) {
  const colours =
    tone === 'friend'
      ? { background: '#123F2C', borderColor: '#2C6B4C', color: '#7FE0AE' }
      : { background: 'transparent', borderColor: COLORS.lineStrong, color: COLORS.textMuted };
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 36,
        padding: '0 16px',
        borderRadius: 8,
        border: '1px solid ' + colours.borderColor,
        background: colours.background,
        color: colours.color,
        fontSize: 13,
        fontWeight: 600,
        whiteSpace: 'nowrap',
        flexShrink: 0,
      }}
    >
      {label}
    </span>
  );
}

/** "2 days ago" from an instant, for requests waiting on an answer. */
function ago(instant: string): string {
  const days = Math.floor((Date.now() - new Date(instant).getTime()) / 86_400_000);
  if (days <= 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 14) return days + ' days ago';
  if (days < 60) return Math.round(days / 7) + ' weeks ago';
  return Math.round(days / 30.44) + ' months ago';
}

export function FriendsPage() {
  const { refreshRequests } = useSession();
  const [friends, setFriends] = useState<Friend[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [totals, setTotals] = useState<FriendTotals[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [loadedFriends, loadedRequests] = await Promise.all([
      fetchFriends().catch(() => []),
      fetchFriendRequests().catch(() => []),
    ]);
    setFriends(loadedFriends);
    setRequests(loadedRequests);
    setLoading(false);
    if (loadedFriends.length > 0) {
      fetchFriendTotals(loadedFriends.map((friend) => friend.userId))
        .then(setTotals)
        .catch(() => undefined);
    }
    void refreshRequests();
  }, [refreshRequests]);

  useEffect(() => {
    void load();
  }, [load]);

  const runSearch = async (event: { preventDefault: () => void }) => {
    event.preventDefault();
    if (query.trim().length < 2) return;
    setSearching(true);
    setError(null);
    try {
      setResults(await searchUsers(query.trim()));
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'The search failed.') : 'The search failed.');
    } finally {
      setSearching(false);
    }
  };

  const act = async (userId: string, action: () => Promise<unknown>) => {
    setBusyId(userId);
    setError(null);
    try {
      await action();
      await load();
      if (results) setResults(await searchUsers(query.trim()).catch(() => results));
    } catch (cause) {
      setError(cause instanceof ApiError ? (cause.detail ?? 'That did not work.') : 'That did not work.');
    } finally {
      setBusyId(null);
    }
  };

  const incoming = requests.filter((request) => request.direction === 'INCOMING');
  const outgoing = requests.filter((request) => request.direction === 'OUTGOING');

  if (loading) {
    return (
      <PageShell maxWidth={1100}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 14 }}>
          <Spinner size={15} />
          Loading your friends
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell maxWidth={1100}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 620 }}>
        <h1 style={{ margin: 0, fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em' }}>Friends</h1>
        <p style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: COLORS.textMuted, textWrap: 'pretty' }}>
          Friendship is mutual and has to be accepted both ways. Friends see the flights you marked public or friends-only, never
          the private ones.
        </p>
      </div>

      <form
        onSubmit={runSearch}
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 10,
          alignItems: 'center',
          padding: '14px 16px',
          background: COLORS.surfaceOverlay,
          border: '1px solid ' + COLORS.lineStrong,
          borderRadius: 12,
        }}
      >
        <input
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Find someone by name or username"
          spellCheck={false}
          style={{
            flex: '1 1 260px',
            minWidth: 0,
            height: 44,
            padding: '0 14px',
            borderRadius: 9,
            background: COLORS.ground,
            border: '1px solid ' + COLORS.lineStrong,
            fontFamily: FONT_STACK,
            fontSize: 14,
            color: COLORS.text,
            outline: 'none',
          }}
        />
        <SearchButton />
        {results && (
          <button
            type="button"
            onClick={() => {
              setResults(null);
              setQuery('');
            }}
            style={{ background: 'none', border: 'none', fontFamily: FONT_STACK, fontSize: 13, color: COLORS.textMuted, cursor: 'pointer' }}
          >
            Clear
          </button>
        )}
      </form>

      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}

      {searching && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, color: COLORS.textMuted, fontSize: 13.5 }}>
          <Spinner size={14} />
          Searching
        </div>
      )}

      {results && results.length > 0 && (
        <div style={CARD}>
          <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>
            {results.length} {results.length === 1 ? 'person matches' : 'people match'} “{query.trim()}”
          </div>
          {results.map((person) => (
            <div key={person.userId} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 20px', borderBottom: ROW_BORDER }}>
              <span style={AVATAR}>{initialsOf(person.username)}</span>
              <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0, flex: '1 1 auto' }}>
                <Link to={'/users/' + person.userId} style={{ fontSize: 14.5, fontWeight: 600, color: COLORS.text }}>
                  {person.username ?? 'Unknown user'}
                </Link>
                <span style={{ fontSize: 12.5, color: COLORS.textDim }}>
                  {person.relation === 'REQUEST_RECEIVED' ? 'waiting on your answer' : 'on WTW'}
                </span>
              </span>
              {person.relation === 'NONE' && (
                <AddFriendButton busy={busyId === person.userId} onClick={() => act(person.userId, () => sendFriendRequest(person.userId))} />
              )}
              {person.relation === 'REQUEST_SENT' && <StateChip label="Requested" tone="pending" />}
              {person.relation === 'REQUEST_RECEIVED' && (
                <AcceptButton busy={busyId === person.userId} onClick={() => act(person.userId, () => acceptFriendRequest(person.userId))} />
              )}
              {person.relation === 'FRIENDS' && <StateChip label="Friends" tone="friend" />}
            </div>
          ))}
        </div>
      )}

      {results && results.length === 0 && (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 14,
            padding: '64px 28px',
            background: 'rgba(10,44,57,0.7)',
            border: '1px dashed ' + COLORS.lineStrong,
            borderRadius: 14,
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: 18, fontWeight: 600 }}>Nobody found for “{query.trim()}”</div>
          <div style={{ fontSize: 13.5, color: COLORS.textMuted, maxWidth: 360, lineHeight: 1.6 }}>
            Names and usernames only — WTW does not search by email address. Ask them for their username.
          </div>
        </div>
      )}

      {!results && friends.length === 0 && incoming.length === 0 && outgoing.length === 0 && (
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
          <svg viewBox="0 0 120 60" width={130} height={65} fill="none" aria-hidden="true">
            <circle cx={44} cy={24} r={11} stroke={COLORS.lineStrong} strokeWidth={1.6} />
            <circle cx={76} cy={24} r={11} stroke={COLORS.lineStrong} strokeWidth={1.6} strokeDasharray="4 4" />
            <path d="M22 52 C28 40 40 36 44 36 C48 36 60 40 66 52" stroke={COLORS.lineStrong} strokeWidth={1.6} />
            <path d="M54 52 C60 40 72 36 76 36 C80 36 92 40 98 52" stroke={COLORS.lineStrong} strokeWidth={1.6} strokeDasharray="4 4" />
          </svg>
          <div style={{ fontSize: 18, fontWeight: 600 }}>No friends yet</div>
          <div style={{ fontSize: 13.5, color: COLORS.textMuted, maxWidth: 380, lineHeight: 1.6 }}>
            Search for someone above. Once you are friends you can compare distance, countries and records, and see the journeys
            they choose to share.
          </div>
        </div>
      )}

      {!results && (friends.length > 0 || incoming.length > 0 || outgoing.length > 0) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          {incoming.length > 0 && (
            <div style={{ ...CARD, border: '1px solid #7A4A16' }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 11,
                  padding: '15px 20px',
                  borderBottom: '1px solid #7A4A16',
                  background: 'rgba(46,29,12,0.4)',
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 600 }}>Requests</span>
                <span
                  style={{
                    padding: '2px 9px',
                    borderRadius: 999,
                    background: '#2E1D0C',
                    border: '1px solid #7A4A16',
                    color: '#FFB067',
                    fontSize: 11,
                    fontWeight: 700,
                  }}
                >
                  {incoming.length}
                </span>
              </div>
              {incoming.map((request) => (
                <div
                  key={request.userId}
                  style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 20px', borderBottom: ROW_BORDER, flexWrap: 'wrap' }}
                >
                  <span style={AVATAR}>{initialsOf(request.username)}</span>
                  <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0, flex: '1 1 160px' }}>
                    <Link to={'/users/' + request.userId} style={{ fontSize: 14.5, fontWeight: 600, color: COLORS.text }}>
                      {request.username ?? 'Unknown user'}
                    </Link>
                    <span style={{ fontSize: 12.5, color: COLORS.textDim }}>{ago(request.requestedAt)}</span>
                  </span>
                  <span style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <AcceptButton busy={busyId === request.userId} onClick={() => act(request.userId, () => acceptFriendRequest(request.userId))} />
                    <QuietButton label="Decline" onClick={() => act(request.userId, () => declineFriendRequest(request.userId))} />
                  </span>
                </div>
              ))}
            </div>
          )}

          {friends.length > 0 && (
            <div style={CARD}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'baseline',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '15px 20px',
                  borderBottom: '1px solid ' + COLORS.line,
                }}
              >
                <span style={{ fontSize: 16, fontWeight: 600 }}>Your friends</span>
                <span style={{ fontSize: 12.5, color: COLORS.textDim }}>
                  {friends.length} {friends.length === 1 ? 'person' : 'people'}
                </span>
              </div>
              {friends.map((friend) => (
                <FriendRow key={friend.userId} friend={friend} totals={totals.find((total) => total.userId === friend.userId)} />
              ))}
            </div>
          )}

          {outgoing.length > 0 && (
            <div style={CARD}>
              <div style={{ padding: '15px 20px', borderBottom: '1px solid ' + COLORS.line, fontSize: 16, fontWeight: 600 }}>
                Sent, waiting for them
              </div>
              {outgoing.map((request) => (
                <div key={request.userId} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '13px 20px', borderBottom: ROW_BORDER }}>
                  <span style={{ ...AVATAR, width: 36, height: 36, fontSize: 12 }}>{initialsOf(request.username)}</span>
                  <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0, flex: '1 1 auto' }}>
                    <span style={{ fontSize: 14, fontWeight: 500 }}>{request.username ?? 'Unknown user'}</span>
                    <span style={{ fontSize: 12, color: COLORS.textDim }}>sent {ago(request.requestedAt)}</span>
                  </span>
                  <QuietButton label="Cancel" danger onClick={() => act(request.userId, () => removeFriend(request.userId))} />
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </PageShell>
  );
}

function FriendRow({ friend, totals }: { friend: Friend; totals?: FriendTotals }) {
  const hover = useHoverStyle(FRIEND_GRID, { background: COLORS.raised });
  return (
    <Link to={'/users/' + friend.userId} {...hover}>
      <span style={AVATAR}>{initialsOf(friend.username)}</span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
        <span style={{ fontSize: 14, fontWeight: 600 }}>{friend.username ?? 'Unknown user'}</span>
        <span style={{ fontSize: 12, color: COLORS.textDim }}>friends since {new Date(friend.friendsSince).getFullYear()}</span>
      </span>
      <span style={{ fontSize: 13, color: COLORS.bodyOnCard, fontVariantNumeric: 'tabular-nums' }}>
        {totals ? formatKm(totals.distanceKm) : '—'}
      </span>
      <span style={{ fontSize: 13, color: COLORS.textMuted, fontVariantNumeric: 'tabular-nums' }}>
        {totals ? formatNumber(totals.flightCount) + ' flights' : 'nothing shared'}
      </span>
      <span style={{ fontSize: 12, color: COLORS.textDim, whiteSpace: 'nowrap' }}>
        {totals ? totals.countryCount + ' countries' : ''}
      </span>
    </Link>
  );
}
