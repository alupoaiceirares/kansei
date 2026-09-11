import { Component, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppHeaderComponent, DownloadQueueItem } from '../../shared/app-header/app-header';
import { MiniPlayerComponent, NowPlayingTrack } from '../../shared/mini-player/mini-player';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

interface SearchResult {
  title: string;
  channel: string;
  views: string;
  duration: string;
  inArchive: boolean;
}

type Format = 'mp3' | 'mp4';

/** Search by name or YouTube link, preview, then confirm-download into the shared archive. */
@Component({
  selector: 'wh-search',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, MiniPlayerComponent, WirehoodWavesComponent],
  templateUrl: './search.html',
  styleUrl: './search.css',
})
export class SearchPage {
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected queue = signal<DownloadQueueItem[]>([...MOCK_DOWNLOAD_QUEUE]);

  protected searchQuery = signal('mora vale ghost frequency');
  protected playing = signal(true);

  protected previewOpen = signal(false);
  protected confirmOpen = signal(false);
  protected toastOpen = signal(false);
  protected toastTitle = signal('');

  protected format = signal<Format>('mp3');
  protected selectedGenres = signal<string[]>(['Shoegaze']);

  protected confirmArtist = signal('');
  protected confirmTitle = signal('');
  protected confirmExtra = signal('');

  protected activeResult: SearchResult | null = null;

  protected genreOptions = ['Shoegaze', 'Dream pop', 'Post-punk', 'Ambient', 'Dub techno', 'Alt country'];

  protected nowPlaying: NowPlayingTrack = { title: 'Ghost Frequency', artist: 'Mora Vale' };

  protected results: SearchResult[] = [
    { title: 'Ghost Frequency (Official Video)', channel: 'Mora Vale', views: '412K views', duration: '3:31', inArchive: true },
    { title: 'Ghost Frequency — Live at Vault Sessions', channel: 'Vault Sessions', views: '88K views', duration: '4:12', inArchive: false },
    { title: 'Mora Vale — Ghost Frequency (Slowed)', channel: 'nightloop', views: '1.2M views', duration: '4:48', inArchive: false },
    { title: 'Ghost Frequency (Instrumental)', channel: 'Mora Vale', views: '31K views', duration: '3:29', inArchive: false },
    { title: 'Mora Vale — Full Album: Signal Bleed', channel: 'Mora Vale', views: '206K views', duration: '38:14', inArchive: false },
    { title: 'Ghost Frequency (Kestrel Park Remix)', channel: 'Kestrel Park', views: '74K views', duration: '5:02', inArchive: false },
  ];

  private toastTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
  ) {
    const q = this.route.snapshot.queryParamMap.get('q');
    const genre = this.route.snapshot.queryParamMap.get('genre');
    if (q) this.searchQuery.set(q);
    else if (genre) this.searchQuery.set(genre);
  }

  protected togglePlay(): void {
    this.playing.update((v) => !v);
  }

  protected submitSearch(): void {
    this.router.navigate([], { queryParams: { q: this.searchQuery() }, relativeTo: this.route });
  }

  protected openPreview(result: SearchResult): void {
    this.activeResult = result;
    this.previewOpen.set(true);
    this.confirmOpen.set(false);
  }

  protected openConfirm(result: SearchResult): void {
    this.activeResult = result;
    this.confirmArtist.set(result.channel);
    this.confirmTitle.set(result.title.replace(/\s*\([^)]*\)\s*$/, ''));
    this.confirmExtra.set('');
    this.confirmOpen.set(true);
    this.previewOpen.set(false);
  }

  protected closeAll(): void {
    this.previewOpen.set(false);
    this.confirmOpen.set(false);
  }

  protected pickFormat(format: Format): void {
    this.format.set(format);
  }

  protected toggleGenre(label: string): void {
    this.selectedGenres.update((genres) => (genres.includes(label) ? genres.filter((g) => g !== label) : [...genres, label]));
  }

  protected confirmDownload(): void {
    const title = `${this.confirmArtist()} — ${this.confirmTitle()}`;
    this.queue.update((q) => [
      { id: `q${Date.now()}`, title: `${this.confirmTitle()} — ${this.format().toUpperCase()}`, status: 'pending', statusText: 'Queued' },
      ...q,
    ]);
    this.confirmOpen.set(false);
    this.toastTitle.set(title);
    this.toastOpen.set(true);
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastOpen.set(false), 5200);
  }

  protected retryDownload(id: string): void {
    this.queue.update((q) => q.map((item) => (item.id === id ? { ...item, status: 'pending', statusText: 'Retrying…' } : item)));
  }
}
