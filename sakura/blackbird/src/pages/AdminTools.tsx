import { useCallback, useEffect, useRef, useState, type CSSProperties, type DragEvent } from 'react';
import { COLORS, FONT_STACK, pill } from '../design/tokens';
import { Banner } from '../components/Banner';
import { Spinner } from '../components/Spinner';
import { ReferencePicker } from '../components/ReferencePicker';
import {
  adminSearchUsers,
  commitImport,
  disableUser,
  enableUser,
  fetchDisabledUsers,
  fetchImportRuns,
  fetchUnmappedAircraft,
  mapAircraftString,
  previewImport,
  searchAircraftTypes,
} from '../api/tailwind';
import type { AdminUser, AircraftTypeOption, ImportPreview, ImportRowStatus, ImportRun, UnmappedAircraftString } from '../api/types';
import { formatDate } from '../format';
import { CARD, CARD_HEAD, DangerButton, PrimaryButton, Row, errorText } from './adminShared';

const LIST_ROW: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 14,
  flexWrap: 'wrap',
  padding: '13px 20px',
  borderBottom: '1px solid ' + COLORS.lineSoft,
  fontSize: 13.5,
};

const STATUS_PILL: Record<ImportRowStatus, [string, string, string]> = {
  READY: ['#123F2C', '#2C6B4C', '#7FE0AE'],
  DUPLICATE: [COLORS.raised, COLORS.lineStrong, COLORS.textMuted],
  ERROR: [COLORS.dangerBg, COLORS.dangerBorder, COLORS.dangerText],
};

function statusPill(status: ImportRowStatus) {
  const [background, borderColor, color] = STATUS_PILL[status];
  return <span style={{ ...pill, padding: '3px 9px', fontSize: 10.5, background, borderColor, color }}>{status}</span>;
}

function Loading({ text }: { text: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>
      <Spinner size={13} />
      {text}
    </div>
  );
}

// ---- aircraft strings

export function AircraftStringsSection() {
  const [rows, setRows] = useState<UnmappedAircraftString[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [type, setType] = useState<AircraftTypeOption | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const typeSearch = useCallback((value: string) => searchAircraftTypes(value, 25), []);

  const load = useCallback(() => {
    fetchUnmappedAircraft()
      .then(setRows)
      .catch((cause) => {
        setRows([]);
        setError(errorText(cause, 'The unmapped strings could not be loaded.'));
      });
  }, []);

  useEffect(load, [load]);

  const save = async (modelString: string) => {
    if (!type) return;
    setSaving(true);
    setError(null);
    try {
      const result = await mapAircraftString(modelString, type.id);
      setSaved(
        '"' + modelString + '" now means ' + result.aircraftType.name + ', ' + result.flightsUpdated +
          (result.flightsUpdated === 1 ? ' flight' : ' flights') + ' updated',
      );
      setOpen(null);
      setType(null);
      load();
    } catch (cause) {
      setError(errorText(cause, 'The mapping could not be saved.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}
      {saved && <Banner tone="success" title={saved} />}

      <div style={CARD}>
        <div style={CARD_HEAD}>Unmapped aircraft strings</div>
        <div style={{ padding: '14px 20px', fontSize: 13, lineHeight: 1.6, color: COLORS.textMuted, borderBottom: '1px solid ' + COLORS.line }}>
          Aircraft names the flight data provider or an import sent that no exact type was found for. Mapping one fixes the
          flights already stored and every later flight with the same name.
        </div>
        {rows === null && <Loading text="Loading the strings" />}
        {rows?.length === 0 && (
          <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>Nothing unmapped, every aircraft resolved to a type.</div>
        )}
        {rows?.map((row) => (
          <div key={row.modelString} style={{ ...LIST_ROW, flexDirection: 'column', alignItems: 'stretch' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 600, color: COLORS.text, flex: '1 1 220px' }}>{row.modelString}</span>
              <span style={{ color: COLORS.textMuted, fontSize: 12.5 }}>{row.family ? row.family + ' family' : 'Family unknown'}</span>
              <span style={{ color: COLORS.textMuted, fontSize: 12.5, fontVariantNumeric: 'tabular-nums' }}>
                {row.flightCount} {row.flightCount === 1 ? 'flight' : 'flights'} · last {formatDate(row.lastSeen)}
              </span>
              <PrimaryButton
                label={open === row.modelString ? 'Close' : 'Map'}
                onClick={() => {
                  setOpen(open === row.modelString ? null : row.modelString);
                  setType(null);
                }}
              />
            </div>
            {open === row.modelString && (
              <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', paddingTop: 4 }}>
                <div style={{ flex: '1 1 320px', minWidth: 0 }}>
                  <ReferencePicker
                    value={type}
                    onChange={setType}
                    search={typeSearch}
                    labelOf={(item) => item.name}
                    metaOf={(item) => [item.manufacturer, item.icaoCode, item.family].filter(Boolean).join(' · ')}
                    placeholder="The exact type, e.g. A320 or 737-800"
                  />
                </div>
                <PrimaryButton label="Save mapping" onClick={() => save(row.modelString)} busy={saving} disabled={!type} />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ---- CSV import

const FORMAT: [string, string][] = [
  ['date', 'Required. 2024-05-31, only past dates'],
  ['from, to', 'Required. IATA (OTP) or ICAO (LROP)'],
  ['airline', 'ICAO (ROT) or IATA code. Optional with a flight number, its prefix picks the passenger airline (LH is Lufthansa, not Lufthansa Cargo)'],
  ['flight_number', 'Optional. RO301'],
  ['departure_time, arrival_time', 'Optional local times, 14:05'],
  ['aircraft', 'Optional. A designator (A320) or a name, unknown names land under aircraft strings'],
  ['seat, seat_position, cabin_class, reason', 'Optional. WINDOW / ECONOMY / LEISURE and so on'],
  ['notes, cargo', 'Optional. cargo is yes or no'],
  ['journey', 'Optional label, rows sharing one go into one journey with that title'],
  ['visibility', 'Optional. PUBLIC, FRIENDS or PRIVATE, empty means the user default'],
];

export function ImportSection() {
  const [target, setTarget] = useState<AdminUser | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [runs, setRuns] = useState<ImportRun[] | null>(null);
  const [busy, setBusy] = useState<'preview' | 'commit' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const userSearch = useCallback((value: string) => adminSearchUsers(value), []);

  const loadRuns = useCallback(() => {
    fetchImportRuns()
      .then(setRuns)
      .catch(() => setRuns([]));
  }, []);

  useEffect(loadRuns, [loadRuns]);

  // Any change to the inputs invalidates the preview, the commit must match what was previewed
  const pick = (next: File | null) => {
    setFile(next);
    setPreview(null);
    setDone(null);
    setError(null);
  };

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    const dropped = event.dataTransfer.files[0];
    if (dropped) pick(dropped);
  };

  const runPreview = async () => {
    if (!target || !file) return;
    setBusy('preview');
    setError(null);
    setDone(null);
    try {
      setPreview(await previewImport(target.userId, file));
    } catch (cause) {
      setPreview(null);
      setError(errorText(cause, 'The file could not be checked.'));
    } finally {
      setBusy(null);
    }
  };

  const runCommit = async () => {
    if (!target || !file || !preview) return;
    setBusy('commit');
    setError(null);
    try {
      const run = await commitImport(target.userId, file);
      setDone(
        run.importedRows + (run.importedRows === 1 ? ' flight' : ' flights') + ' imported for ' + (run.targetUsername ?? 'the user') +
          ', ' + run.duplicateRows + ' duplicates and ' + run.errorRows + ' bad rows skipped',
      );
      setPreview(null);
      setFile(null);
      if (input.current) input.current.value = '';
      loadRuns();
    } catch (cause) {
      setError(errorText(cause, 'The import did not go through, nothing was written.'));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}
      {done && <Banner tone="success" title={done} />}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20, alignItems: 'start' }}>
        <div style={CARD}>
          <div style={CARD_HEAD}>Import flights from a file</div>
          <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 15 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 500, color: COLORS.textMuted }}>For which user</span>
              <ReferencePicker
                value={target}
                onChange={(next) => {
                  setTarget(next);
                  setPreview(null);
                }}
                search={userSearch}
                labelOf={(item) => item.username ?? item.userId}
                metaOf={(item) => (item.enabled ? 'joined ' + formatDate(item.joinedAt.slice(0, 10)) : 'disabled')}
                placeholder="Username"
              />
            </div>

            <div
              role="button"
              tabIndex={0}
              onClick={() => input.current?.click()}
              onKeyDown={(event) => (event.key === 'Enter' || event.key === ' ') && input.current?.click()}
              onDragOver={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={onDrop}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 9,
                padding: '26px 20px',
                border: '1px dashed ' + (dragging ? COLORS.cyan : COLORS.lineStrong),
                background: dragging ? COLORS.raised : 'transparent',
                borderRadius: 11,
                textAlign: 'center',
                cursor: 'pointer',
              }}
            >
              <svg viewBox="0 0 40 40" width={30} height={30} fill="none" stroke={COLORS.lineStrong} strokeWidth={2} aria-hidden="true">
                <path d="M20 27 V9" strokeLinecap="round" />
                <path d="M13 16 L20 9 L27 16" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M8 27 v4 a2 2 0 0 0 2 2 h20 a2 2 0 0 0 2 -2 v-4" />
              </svg>
              <span style={{ fontSize: 13.5, color: COLORS.bodyOnCard }}>{file ? file.name : 'Drop a CSV here or click to choose'}</span>
              <span style={{ fontSize: 12, color: COLORS.textDim }}>Up to 1 MB and 2000 rows</span>
              <input
                ref={input}
                type="file"
                accept=".csv,text/csv"
                style={{ display: 'none' }}
                onChange={(event) => pick(event.target.files?.[0] ?? null)}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 12.5, color: COLORS.textMuted }}>
              <Row label="Preview" value="Every row is checked first, nothing is written until you import" width={90} small />
              <Row label="Duplicates" value="Same day, route and number as a logged flight is skipped" width={90} small />
              <Row label="Visibility" value="The user's default (friends only unless they changed it), or the row's own" width={90} small />
            </div>

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <PrimaryButton label="Check the file" onClick={runPreview} busy={busy === 'preview'} disabled={!target || !file} />
            </div>
          </div>
        </div>

        <div style={CARD}>
          <div style={CARD_HEAD}>Columns</div>
          <div style={{ padding: '14px 20px', display: 'flex', flexDirection: 'column', gap: 9 }}>
            <span style={{ fontSize: 12.5, color: COLORS.textMuted, lineHeight: 1.55 }}>
              First line is the header, any order, unknown columns are refused. UTF-8, comma separated.
            </span>
            {FORMAT.map(([columns, text]) => (
              <div key={columns} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <code style={{ fontSize: 12, color: COLORS.cyan }}>{columns}</code>
                <span style={{ fontSize: 12, color: COLORS.bodyOnCard, lineHeight: 1.5 }}>{text}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {preview && (
        <div style={CARD}>
          <div style={{ ...CARD_HEAD, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, flexWrap: 'wrap' }}>
            <span>
              Preview for {preview.targetUsername ?? 'the user'}: {preview.readyRows} ready, {preview.duplicateRows} duplicates,{' '}
              {preview.errorRows} with problems
            </span>
            <PrimaryButton
              label={'Import ' + preview.readyRows + (preview.readyRows === 1 ? ' flight' : ' flights')}
              onClick={runCommit}
              busy={busy === 'commit'}
              disabled={preview.readyRows === 0}
            />
          </div>
          <div style={{ maxHeight: 460, overflowY: 'auto' }}>
            {preview.rows.map((row) => (
              <div key={row.line} style={LIST_ROW}>
                <span style={{ width: 44, color: COLORS.textDim, fontVariantNumeric: 'tabular-nums' }}>#{row.line}</span>
                {statusPill(row.status)}
                <span style={{ width: 96, color: COLORS.text }}>{row.date ?? '—'}</span>
                <span style={{ width: 110, color: COLORS.text, fontWeight: 600 }}>
                  {(row.from ?? '?') + ' → ' + (row.to ?? '?')}
                </span>
                <span style={{ flex: '1 1 200px', minWidth: 0, color: COLORS.textMuted, fontSize: 12.5 }}>
                  {row.message ?? [row.flightNumber, row.airline, row.aircraft, row.journey].filter(Boolean).join(' · ')}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={CARD}>
        <div style={CARD_HEAD}>Past imports</div>
        {runs === null && <Loading text="Loading the history" />}
        {runs?.length === 0 && <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>No imports yet.</div>}
        {runs?.map((run) => (
          <details key={run.id} style={{ borderBottom: '1px solid ' + COLORS.lineSoft }}>
            <summary style={{ ...LIST_ROW, borderBottom: 'none', cursor: run.errors.length ? 'pointer' : 'default', listStyle: 'none' }}>
              <span style={{ width: 96, color: COLORS.textMuted }}>{formatDate(run.createdAt.slice(0, 10))}</span>
              <span style={{ flex: '1 1 160px', color: COLORS.text, fontWeight: 600 }}>{run.targetUsername ?? run.targetUserId}</span>
              <span style={{ flex: '1 1 160px', color: COLORS.textMuted, fontSize: 12.5 }}>{run.fileName ?? 'unnamed file'}</span>
              <span style={{ color: COLORS.bodyOnCard, fontSize: 12.5, fontVariantNumeric: 'tabular-nums' }}>
                {run.importedRows} imported · {run.duplicateRows} duplicates · {run.errorRows} errors
              </span>
              <span style={{ color: COLORS.textDim, fontSize: 12 }}>by {run.adminUsername ?? 'an admin'}</span>
            </summary>
            {run.errors.length > 0 && (
              <div style={{ padding: '4px 20px 14px 20px', display: 'flex', flexDirection: 'column', gap: 4 }}>
                {run.errors.map((item) => (
                  <span key={item.line} style={{ fontSize: 12, color: COLORS.dangerText }}>
                    Line {item.line}: {item.message}
                  </span>
                ))}
              </div>
            )}
          </details>
        ))}
      </div>
    </div>
  );
}

// ---- users

export function UsersSection() {
  const [user, setUser] = useState<AdminUser | null>(null);
  const [disabled, setDisabled] = useState<AdminUser[] | null>(null);
  // Which user id waits on its confirm step, and for which action
  const [confirming, setConfirming] = useState<{ userId: string; action: 'disable' | 'enable' } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const userSearch = useCallback((value: string) => adminSearchUsers(value), []);

  const loadDisabled = useCallback(() => {
    fetchDisabledUsers()
      .then(setDisabled)
      .catch((cause) => {
        setDisabled([]);
        setError(errorText(cause, 'The disabled users could not be loaded.'));
      });
  }, []);

  useEffect(loadDisabled, [loadDisabled]);

  const run = async (target: AdminUser, action: 'disable' | 'enable') => {
    setBusy(true);
    setError(null);
    setDone(null);
    try {
      if (action === 'disable') await disableUser(target.userId);
      else await enableUser(target.userId);
      const name = target.username ?? 'The user';
      setDone(action === 'disable' ? name + ' is disabled on WTW' : name + ' is reinstated, their log is back as it was');
      if (user?.userId === target.userId) {
        setUser({ ...target, enabled: action === 'enable', disabledAt: action === 'enable' ? null : new Date().toISOString() });
      }
      setConfirming(null);
      loadDisabled();
    } catch (cause) {
      setError(errorText(cause, action === 'disable' ? 'The user could not be disabled.' : 'The user could not be reinstated.'));
    } finally {
      setBusy(false);
    }
  };

  const confirmBox = (target: AdminUser, action: 'disable' | 'enable') => (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 11,
        padding: 15,
        borderRadius: 11,
        border: '1px solid ' + (action === 'disable' ? COLORS.dangerBorder : COLORS.lineStrong),
        background: action === 'disable' ? COLORS.dangerBg : COLORS.raised,
      }}
    >
      <span style={{ fontSize: 13, lineHeight: 1.55, color: COLORS.bodyOnCard }}>
        {action === 'disable'
          ? 'Disable ' + (target.username ?? 'this user') + '? They can be reinstated later from the list below.'
          : 'Reinstate ' + (target.username ?? 'this user') + '? Their log and friends become visible again straight away.'}
      </span>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        {action === 'disable' ? (
          <DangerButton label="Yes, disable" onClick={() => run(target, 'disable')} busy={busy} />
        ) : (
          <PrimaryButton label="Yes, reinstate" onClick={() => run(target, 'enable')} busy={busy} />
        )}
        <button
          type="button"
          onClick={() => setConfirming(null)}
          style={{ background: 'none', border: 'none', fontFamily: FONT_STACK, fontSize: 13, color: COLORS.textMuted, cursor: 'pointer' }}
        >
          Cancel
        </button>
      </div>
    </div>
  );

  const isConfirming = (target: AdminUser, action: 'disable' | 'enable') =>
    confirming?.userId === target.userId && confirming.action === action;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {error && (
        <Banner tone="error" title="That did not work">
          {error}
        </Banner>
      )}
      {done && <Banner tone="success" title={done} />}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20, alignItems: 'start' }}>
        <div style={CARD}>
          <div style={CARD_HEAD}>Find a user</div>
          <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 15 }}>
            <ReferencePicker
              value={user}
              onChange={(next) => {
                setUser(next);
                setConfirming(null);
                setDone(null);
              }}
              search={userSearch}
              labelOf={(item) => item.username ?? item.userId}
              metaOf={(item) => (item.enabled ? 'joined ' + formatDate(item.joinedAt.slice(0, 10)) : 'disabled')}
              placeholder="Username"
            />
            {user && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <Row label="User" value={user.username ?? user.userId} />
                <Row label="Joined" value={formatDate(user.joinedAt.slice(0, 10))} />
                <Row
                  label="Status"
                  value={user.enabled ? 'Active' : 'Disabled' + (user.disabledAt ? ' since ' + formatDate(user.disabledAt.slice(0, 10)) : '')}
                  tone={user.enabled ? 'normal' : 'bad'}
                />
                {!confirming && (
                  <div>
                    {user.enabled ? (
                      <DangerButton label="Disable on WTW" onClick={() => setConfirming({ userId: user.userId, action: 'disable' })} />
                    ) : (
                      <PrimaryButton label="Reinstate" onClick={() => setConfirming({ userId: user.userId, action: 'enable' })} />
                    )}
                  </div>
                )}
                {isConfirming(user, 'disable') && confirmBox(user, 'disable')}
                {isConfirming(user, 'enable') && confirmBox(user, 'enable')}
              </div>
            )}
          </div>
        </div>

        <div style={CARD}>
          <div style={CARD_HEAD}>What disabling does</div>
          <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 9 }}>
            {[
              'Their log stops being reachable, for them and for everyone else.',
              'Nothing is deleted, the flights and journeys stay exactly as they were.',
              'Their Kansei account is untouched, only WTW is closed to them.',
              'Reinstating brings everything back as it was, friends included.',
            ].map((line) => (
              <div key={line} style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                <span style={{ width: 6, height: 6, borderRadius: '50%', background: COLORS.lineStrong, marginTop: 6, flexShrink: 0 }} />
                <span style={{ fontSize: 12.5, lineHeight: 1.55, color: COLORS.textMuted }}>{line}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={CARD}>
        <div style={CARD_HEAD}>Disabled users{disabled && disabled.length > 0 ? ' (' + disabled.length + ')' : ''}</div>
        {disabled === null && <Loading text="Loading the disabled users" />}
        {disabled?.length === 0 && <div style={{ padding: '18px 20px', fontSize: 13, color: COLORS.textMuted }}>Nobody is disabled.</div>}
        {disabled?.map((item) => (
          <div key={item.userId} style={{ ...LIST_ROW, flexDirection: 'column', alignItems: 'stretch' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
              <span style={{ flex: '1 1 180px', fontWeight: 600, color: COLORS.text }}>{item.username ?? item.userId}</span>
              <span style={{ color: COLORS.textMuted, fontSize: 12.5 }}>
                {item.disabledAt ? 'disabled ' + formatDate(item.disabledAt.slice(0, 10)) : 'disabled'}
              </span>
              <span style={{ color: COLORS.textDim, fontSize: 12 }}>joined {formatDate(item.joinedAt.slice(0, 10))}</span>
              {!isConfirming(item, 'enable') && (
                <PrimaryButton label="Reinstate" onClick={() => setConfirming({ userId: item.userId, action: 'enable' })} />
              )}
            </div>
            {isConfirming(item, 'enable') && confirmBox(item, 'enable')}
          </div>
        ))}
      </div>
    </div>
  );
}
