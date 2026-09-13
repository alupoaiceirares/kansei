import { CONTROL_TOWER_URL } from '../core/config';

// Same URL admin-thumbnails already used to compare current-vs-proposed art, generalized for
// every other art tile in the app (library/favorites/track-detail/home/song-of-the-day/mini-player)
// which previously never requested this at all, always showing the letter placeholder regardless
// of whether a real thumbnail was on disk. No auth needed, plain <img src>, same as that page.
export function trackThumbnailUrl(trackId: string): string {
  return `${CONTROL_TOWER_URL}/wirehood/tracks/${trackId}/thumbnail`;
}

// Hides a broken/404 thumbnail <img> so the letter placeholder sitting behind it shows through,
// instead of a broken-image icon - use as (error)="hideOnError($event)" on any thumbnail <img>
export function hideOnError(event: Event): void {
  (event.target as HTMLImageElement).style.display = 'none';
}

export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.round(totalSeconds % 60);
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  const diffMs = Date.now() - then;
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.floor(days / 7);
  if (weeks < 4) return `${weeks}w ago`;
  const months = Math.floor(days / 30);
  return `${months}mo ago`;
}
