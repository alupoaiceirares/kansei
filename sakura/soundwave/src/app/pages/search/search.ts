import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, SearchResult, Genre } from '../../core/wirehood-api';
import { DownloadsService } from '../../core/downloads';
import { AuthService } from '../../core/auth';
import { formatDuration } from '../../shared/format';

type Format = 'mp3' | 'mp4';

/** Search by name or YouTube link, preview, then confirm-download into the shared archive. */
@Component({
  selector: 'wh-search',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './search.html',
  styleUrl: './search.css',
})
export class SearchPage {
  protected formatDuration = formatDuration;
  private auth = inject(AuthService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected searchQuery = signal('');
  protected searching = signal(false);

  protected previewOpen = signal(false);
  protected confirmOpen = signal(false);
  protected toastOpen = signal(false);
  protected toastTitle = signal('');

  protected format = signal<Format>('mp3');
  protected selectedGenreIds = signal<string[]>([]);
  protected genreOptions = signal<Genre[]>([]);

  protected confirmArtist = signal('');
  protected confirmTitle = signal('');
  protected confirmExtra = signal('');

  protected activeResult: SearchResult | null = null;
  protected results = signal<SearchResult[]>([]);

  private toastTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: WirehoodApi,
    private downloads: DownloadsService,
  ) {
    this.api.genres().subscribe({ next: (genres) => this.genreOptions.set(genres), error: () => {} });

    const q = this.route.snapshot.queryParamMap.get('q');
    const genre = this.route.snapshot.queryParamMap.get('genre');
    const query = q ?? genre;
    if (query) {
      this.searchQuery.set(query);
      this.runSearch(query);
    }
  }

  protected submitSearch(): void {
    const query = this.searchQuery();
    this.router.navigate([], { queryParams: { q: query || undefined }, relativeTo: this.route });
    if (query) this.runSearch(query);
  }

  private runSearch(query: string): void {
    this.searching.set(true);
    this.api.search(query).subscribe({
      next: (results) => {
        this.results.set(results);
        this.searching.set(false);
      },
      error: () => this.searching.set(false),
    });
  }

  protected openPreview(result: SearchResult): void {
    this.activeResult = result;
    this.previewOpen.set(true);
    this.confirmOpen.set(false);
  }

  protected openConfirm(result: SearchResult): void {
    this.activeResult = result;
    this.confirmExtra.set('');
    this.selectedGenreIds.set([]);
    this.previewOpen.set(false);
    this.confirmOpen.set(true);
    this.api.parseTitle(result.title).subscribe({
      next: (parsed) => {
        this.confirmArtist.set(parsed.artist || result.channelTitle);
        this.confirmTitle.set(parsed.title || result.title);
        this.confirmExtra.set(parsed.extraInfo ?? '');
      },
      error: () => {
        this.confirmArtist.set(result.channelTitle);
        this.confirmTitle.set(result.title);
      },
    });
  }

  protected closeAll(): void {
    this.previewOpen.set(false);
    this.confirmOpen.set(false);
  }

  protected pickFormat(format: Format): void {
    this.format.set(format);
  }

  protected toggleGenre(id: string): void {
    this.selectedGenreIds.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  protected confirmDownload(): void {
    const result = this.activeResult;
    if (!result) return;
    const title = `${this.confirmArtist()}, ${this.confirmTitle()}`;
    const genreIds = this.selectedGenreIds();

    this.downloads.submit(
      {
        youtubeVideoId: result.videoId,
        title: this.confirmTitle(),
        artist: this.confirmArtist(),
        extraInfo: this.confirmExtra(),
        durationSeconds: result.durationSeconds,
        format: this.format(),
      },
      (queued) => {
        if (genreIds.length > 0) this.api.tagGenres(queued.trackId, genreIds).subscribe();
      },
    );

    this.confirmOpen.set(false);
    this.toastTitle.set(title);
    this.toastOpen.set(true);
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastOpen.set(false), 5200);
  }
}
