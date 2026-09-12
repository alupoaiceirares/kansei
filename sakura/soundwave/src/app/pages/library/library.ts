import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, LibraryItem } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { PlaybackService } from '../../core/playback';
import { formatRelativeTime, saveBlob } from '../../shared/format';

interface Collection {
  name: string;
  meta: string;
}

interface TrackRow {
  trackId: string;
  title: string;
  artist: string;
  formats: string[];
  added: string;
  faved: boolean;
}

type FormatFilter = 'All' | 'MP3' | 'MP4';
const PAGE_SIZE = 12;

/** Collections row (Favorites + playlists) plus the full downloaded-tracks grid, filterable by format/text. */
@Component({
  selector: 'wh-library',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './library.html',
  styleUrl: './library.css',
})
export class LibraryPage {
  private api = inject(WirehoodApi);
  private auth = inject(AuthService);
  private playback = inject(PlaybackService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected filter = signal<FormatFilter>('All');
  protected filterText = signal('');
  protected page = signal(0);
  protected totalPages = signal(1);

  protected filterOptions: FormatFilter[] = ['All', 'MP3', 'MP4'];

  protected collections = signal<Collection[]>([]);
  protected favoritedTrackIds = signal<Set<string>>(new Set());
  protected favoritesCount = signal(0);
  private items = signal<LibraryItem[]>([]);

  constructor() {
    this.loadPage(0);

    this.api.favorites(0, 200).subscribe({
      next: (favPage) => {
        this.favoritedTrackIds.set(new Set(favPage.items.map((f) => f.trackId)));
        this.favoritesCount.set(favPage.totalElements);
      },
      error: () => {},
    });

    this.api.myPlaylists().subscribe({
      next: (playlists) =>
        this.collections.set(
          playlists.map((p) => ({ name: p.name, meta: `${p.trackCount} tracks · ${p.shared ? 'shared' : 'private'}` })),
        ),
      error: () => {},
    });
  }

  private loadPage(page: number): void {
    this.api.library(page, PAGE_SIZE).subscribe({
      next: (res) => {
        this.items.set(res.items);
        this.page.set(res.page);
        this.totalPages.set(Math.max(1, res.totalPages));
      },
    });
  }

  protected tracks = computed<TrackRow[]>(() => {
    const f = this.filter();
    const text = this.filterText().trim().toLowerCase();
    const faved = this.favoritedTrackIds();
    return this.items()
      .filter((t) => f === 'All' || t.formats.some((fmt) => fmt.format.toUpperCase() === f))
      .filter((t) => !text || t.title.toLowerCase().includes(text) || t.artist.toLowerCase().includes(text))
      .map((t) => ({
        trackId: t.trackId,
        title: t.title,
        artist: t.artist,
        formats: t.formats.map((fmt) => fmt.format.toUpperCase()),
        added: formatRelativeTime(t.addedAt),
        faved: faved.has(t.trackId),
      }));
  });

  protected pickFilter(f: FormatFilter): void {
    this.filter.set(f);
  }

  protected prevPage(): void {
    if (this.page() > 0) this.loadPage(this.page() - 1);
  }

  protected nextPage(): void {
    if (this.page() < this.totalPages() - 1) this.loadPage(this.page() + 1);
  }

  protected playTrack(t: TrackRow): void {
    if (!t.formats.includes('MP3')) return;
    const playable = this.tracks().filter((x) => x.formats.includes('MP3'));
    const index = playable.findIndex((x) => x.trackId === t.trackId);
    if (index === -1) return;
    this.playback.playQueue(
      playable.map((x) => ({ trackId: x.trackId, title: x.title, artist: x.artist })),
      index,
    );
  }

  protected downloadTrack(t: TrackRow): void {
    const format = t.formats.includes('MP3') ? 'mp3' : 'mp4';
    this.api.downloadFile(t.trackId, format).subscribe({ next: (blob) => saveBlob(blob, `${t.artist} - ${t.title}.${format}`) });
  }
}
