import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, ThumbnailSubmission } from '../../core/wirehood-api';
import { CONTROL_TOWER_URL } from '../../core/config';
import { formatRelativeTime } from '../../shared/format';

type FilterOption = 'PENDING' | 'APPROVED' | 'REJECTED';

const STATUS_STYLE: Record<FilterOption, { statusBg: string; statusColor: string }> = {
  PENDING: { statusBg: 'rgba(255,255,255,0.1)', statusColor: 'rgba(242,240,236,0.7)' },
  APPROVED: { statusBg: 'rgba(18,183,107,0.16)', statusColor: '#12B76B' },
  REJECTED: { statusBg: 'rgba(226,58,58,0.16)', statusColor: '#E23A3A' },
};

interface TrackLabel {
  title: string;
  artist: string;
  hasThumbnail: boolean;
}

/** Global queue of pending thumbnail submissions, no per-track title/artist in the list response so each row fetches its own track. */
@Component({
  selector: 'wh-admin-thumbnails',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './admin-thumbnails.html',
  styleUrl: './admin-thumbnails.css',
})
export class AdminThumbnailsPage implements OnDestroy {
  private api = inject(WirehoodApi);

  protected filter = signal<FilterOption>('PENDING');
  protected filterOptions: FilterOption[] = ['PENDING', 'APPROVED', 'REJECTED'];

  protected items = signal<ThumbnailSubmission[]>([]);
  protected page = signal(0);
  protected totalPages = signal(1);

  protected trackLabels = signal<Record<string, TrackLabel>>({});
  protected previewUrls = signal<Record<string, string>>({});

  constructor() {
    this.reload();
  }

  ngOnDestroy(): void {
    Object.values(this.previewUrls()).forEach((url) => URL.revokeObjectURL(url));
  }

  private reload(): void {
    this.api.adminThumbnailSubmissions(this.filter(), this.page(), 10).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.totalPages.set(Math.max(result.totalPages, 1));
        result.items.forEach((s) => {
          this.loadTrackLabel(s.trackId);
          this.loadPreview(s.id);
        });
      },
    });
  }

  private loadTrackLabel(trackId: string): void {
    if (this.trackLabels()[trackId]) return;
    this.api.track(trackId).subscribe({
      next: (track) =>
        this.trackLabels.update((m) => ({ ...m, [trackId]: { title: track.title, artist: track.artist, hasThumbnail: track.hasThumbnail } })),
      error: () => {},
    });
  }

  private loadPreview(submissionId: string): void {
    if (this.previewUrls()[submissionId]) return;
    this.api.adminThumbnailFile(submissionId).subscribe({
      next: (blob) => this.previewUrls.update((m) => ({ ...m, [submissionId]: URL.createObjectURL(blob) })),
      error: () => {},
    });
  }

  protected rows = computed(() =>
    this.items().map((s) => {
      const label = this.trackLabels()[s.trackId];
      return {
        id: s.id,
        trackId: s.trackId,
        title: label?.title ?? s.trackId,
        artist: label?.artist ?? '',
        by: s.submittedBy,
        when: formatRelativeTime(s.submittedAt),
        preview: this.previewUrls()[s.id] ?? null,
        currentThumbnail: label?.hasThumbnail ? `${CONTROL_TOWER_URL}/wirehood/tracks/${s.trackId}/thumbnail` : null,
        pending: s.status === 'PENDING',
        ...STATUS_STYLE[s.status],
        status: s.status,
      };
    }),
  );

  protected pickFilter(f: FilterOption): void {
    this.filter.set(f);
    this.page.set(0);
    this.reload();
  }

  protected prevPage(): void {
    if (this.page() === 0) return;
    this.page.update((p) => p - 1);
    this.reload();
  }

  protected nextPage(): void {
    if (this.page() + 1 >= this.totalPages()) return;
    this.page.update((p) => p + 1);
    this.reload();
  }

  protected approve(id: string): void {
    this.api.adminApproveThumbnail(id).subscribe({ next: () => this.reload() });
  }

  protected reject(id: string): void {
    this.api.adminRejectThumbnail(id).subscribe({ next: () => this.reload() });
  }
}
