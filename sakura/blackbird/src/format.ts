// Shared formatting. Every figure the design shows as a number is rendered tabular, so the
// separators here have to stay consistent across screens.

const DATE = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
const DATE_SHORT = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short' });
const TIME = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false });

/** "12 Mar 2026" from a plain yyyy-mm-dd date, read as a date not an instant. */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return DATE.format(new Date(year, month - 1, day));
}

export function formatDateShort(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return DATE_SHORT.format(new Date(year, month - 1, day));
}

/** Local clock time of an instant, in the airport's own time zone when one is known. */
export function formatTime(instant: string | null | undefined, timeZone?: string | null): string {
  if (!instant) return '--:--';
  const formatter = timeZone ? new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone }) : TIME;
  try {
    return formatter.format(new Date(instant));
  } catch {
    return TIME.format(new Date(instant));
  }
}

export function formatNumber(value: number, fractionDigits = 0): string {
  return value.toLocaleString('en-US', { minimumFractionDigits: fractionDigits, maximumFractionDigits: fractionDigits });
}

export function formatKm(km: number): string {
  return formatNumber(Math.round(km)) + ' km';
}

/** Time in air reads as whole hours once it is past a day's worth, minutes below that. */
export function formatMinutes(minutes: number): string {
  if (minutes < 60) return minutes + ' min';
  const hours = Math.round(minutes / 60);
  return formatNumber(hours) + ' h';
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return hours + 'h ' + String(rest).padStart(2, '0') + 'm';
}

/** "In 9 days", "Tomorrow", "Today" for an upcoming flight. */
export function daysUntil(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const target = new Date(year, month - 1, day);
  const today = new Date();
  const midnight = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const days = Math.round((target.getTime() - midnight.getTime()) / 86_400_000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  return 'In ' + days + ' days';
}

export function initialsOf(name: string | null | undefined): string {
  if (!name) return 'WT';
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return 'WT';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

/** Earth's equator, for the "times around the Earth" line. */
export function earthLaps(km: number): string {
  return (km / 40_075).toFixed(1);
}
