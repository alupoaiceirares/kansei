import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CONTROL_TOWER_URL } from './config';

export type QueueStatus = 'pending' | 'failed' | 'ready';

export interface DownloadQueueItem {
  id: string;
  title: string;
  status: QueueStatus;
  statusText: string;
}

interface PendingApiItem {
  trackId: string;
  title: string;
  artist: string;
  format: string;
  requestedAt: string;
}

interface PendingResponse {
  pending: PendingApiItem[];
  failed: PendingApiItem[];
}

interface StreamEvent {
  userId: string;
  trackId: string;
  format: string;
  status: 'READY' | 'FAILED';
}

export interface SubmitDownloadRequest {
  youtubeVideoId: string;
  title: string;
  artist: string;
  extraInfo: string;
  durationSeconds: number;
  format: 'mp3' | 'mp4';
}

interface Entry {
  trackId: string;
  format: string;
  title: string;
  artist: string;
  status: QueueStatus;
  request?: SubmitDownloadRequest;
}

function key(trackId: string, format: string): string {
  return `${trackId}:${format.toLowerCase()}`;
}

function statusText(status: QueueStatus): string {
  if (status === 'ready') return 'Ready · added to library';
  if (status === 'failed') return 'Source unavailable';
  return 'Queued';
}

/** Live download queue, seeded from GET /wirehood/downloads/pending, kept current over SSE. */
@Injectable({ providedIn: 'root' })
export class DownloadsService {
  private http = inject(HttpClient);
  private eventSource?: EventSource;
  private started = false;
  private entries = signal<Map<string, Entry>>(new Map());

  readonly queue = computed<DownloadQueueItem[]>(() =>
    Array.from(this.entries().values()).map((e) => ({
      id: key(e.trackId, e.format),
      title: `${e.title}, ${e.format.toUpperCase()}`,
      status: e.status,
      statusText: statusText(e.status),
    })),
  );

  readonly pendingCount = computed(() => this.queue().filter((q) => q.status === 'pending').length);

  /** Idempotent, safe to call from every page that renders the shared header. */
  ensureStarted(): void {
    if (this.started) return;
    this.started = true;
    this.loadPending();
    this.connectStream();
  }

  submit(request: SubmitDownloadRequest, onResult?: (result: { trackId: string; outcome: string }) => void): void {
    this.http.post<{ trackId: string; outcome: string }>(`${CONTROL_TOWER_URL}/wirehood/downloads`, request).subscribe({
      next: (result) => {
        const status: QueueStatus = result.outcome === 'ALREADY_READY' ? 'ready' : 'pending';
        this.upsert({ trackId: result.trackId, format: request.format, title: request.title, artist: request.artist, status, request });
        onResult?.(result);
      },
    });
  }

  retry(id: string): void {
    const entry = Array.from(this.entries().values()).find((e) => key(e.trackId, e.format) === id);
    if (!entry?.request) {
      // Seeded from GET /wirehood/downloads/pending, the original youtubeVideoId is never
      // returned by that endpoint, so there is nothing to resubmit without a fresh search.
      console.warn('Cannot retry this item, its source video id is not known client-side.');
      return;
    }
    this.submit(entry.request);
  }

  private loadPending(): void {
    this.http.get<PendingResponse>(`${CONTROL_TOWER_URL}/wirehood/downloads/pending`).subscribe({
      next: (res) => {
        res.pending.forEach((p) => this.upsert({ ...p, status: 'pending' }));
        res.failed.forEach((p) => this.upsert({ ...p, status: 'failed' }));
      },
    });
  }

  private connectStream(): void {
    this.http.post<{ ticket: string }>(`${CONTROL_TOWER_URL}/wirehood/sse-ticket`, {}).subscribe({
      next: ({ ticket }) => {
        this.eventSource?.close();
        this.eventSource = new EventSource(`${CONTROL_TOWER_URL}/wirehood/downloads/stream?ticket=${ticket}`);
        this.eventSource.onmessage = (ev) => {
          const data = JSON.parse(ev.data) as StreamEvent;
          const existing = this.entries().get(key(data.trackId, data.format));
          this.upsert({
            trackId: data.trackId,
            format: data.format,
            title: existing?.title ?? data.trackId,
            artist: existing?.artist ?? '',
            status: data.status === 'READY' ? 'ready' : 'failed',
            request: existing?.request,
          });
        };
        this.eventSource.onerror = () => {
          this.eventSource?.close();
          setTimeout(() => this.connectStream(), 5000);
        };
      },
    });
  }

  private upsert(entry: Entry): void {
    this.entries.update((map) => {
      const next = new Map(map);
      next.set(key(entry.trackId, entry.format), entry);
      return next;
    });
  }
}
