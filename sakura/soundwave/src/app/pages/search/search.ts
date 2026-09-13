import { Component, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, SearchResult, Genre, ExistingTrack } from '../../core/wirehood-api';
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
  protected toastError = signal(false);

  protected format = signal<Format>('mp3');
  protected selectedGenreIds = signal<string[]>([]);
  protected genreOptions = signal<Genre[]>([]);

  protected confirmArtist = signal('');
  protected confirmTitle = signal('');
  protected confirmExtra = signal('');
  protected existingTrack = signal<ExistingTrack | null>(null);

  protected activeResult: SearchResult | null = null;
  protected results = signal<SearchResult[]>([]);
  private sanitizer = inject(DomSanitizer);

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

  protected previewEmbedUrl(): SafeResourceUrl | null {
    const result = this.activeResult;
    if (!result) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(`https://www.youtube.com/embed/${result.videoId}?autoplay=1`);
  }

  protected openConfirm(result: SearchResult): void {
    this.activeResult = result;
    this.confirmExtra.set('');
    this.selectedGenreIds.set([]);
    this.previewOpen.set(false);
    this.confirmOpen.set(true);
    this.existingTrack.set(null);

    // Already on the platform - grey the fields and show the canonical values instead, editing
    // them here would be silently discarded on submit anyway (existing track's columns always win)
    this.api.existingTrack(result.videoId).subscribe({
      next: (existing) => {
        this.existingTrack.set(existing);
        if (existing.exists) {
          this.confirmArtist.set(existing.artist ?? result.channelTitle);
          this.confirmTitle.set(existing.title ?? result.title);
          this.confirmExtra.set(existing.extraInfo ?? '');
        }
      },
      error: () => {},
    });

    this.api.parseTitle(result.title).subscribe({
      next: (parsed) => {
        if (this.existingTrack()?.exists) return;
        this.confirmArtist.set(parsed.artist || result.channelTitle);
        this.confirmTitle.set(parsed.title || result.title);
        this.confirmExtra.set(parsed.extraInfo ?? '');
      },
      error: () => {
        if (this.existingTrack()?.exists) return;
        this.confirmArtist.set(result.channelTitle);
        this.confirmTitle.set(result.title);
      },
    });
  }

  protected existingFormatStatus(fmt: Format): string | null {
    const found = this.existingTrack()?.formats.find((f) => f.format.toLowerCase() === fmt);
    if (!found) return null;
    switch (found.status) {
      case 'READY':
        return 'Already ready, no re-download';
      case 'PENDING':
        return 'Already queued';
      case 'DOWNLOADING':
        return 'Downloading now';
      case 'FAILED':
        return 'Failed before, will retry';
      default:
        return found.status;
    }
  }

  protected closeAll(): void {
    this.previewOpen.set(false);
    this.confirmOpen.set(false);
  }

  // A click event's target is decided by where the mouse comes UP, not where it went down -
  // selecting text inside the modal (mousedown on an input) and dragging the cursor out past the
  // scrim before releasing fired a "click" on the scrim itself, closing the modal mid-selection.
  // Only close if the press that started this click also began on the scrim, not inside the modal.
  private scrimPressStartedOnScrim = false;

  protected onScrimMouseDown(event: MouseEvent): void {
    this.scrimPressStartedOnScrim = event.target === event.currentTarget;
  }

  protected onScrimClick(event: MouseEvent): void {
    if (this.scrimPressStartedOnScrim && event.target === event.currentTarget) {
      this.closeAll();
    }
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
        this.showToast(title, false);
      },
      () => this.showToast(title, true),
    );

    this.confirmOpen.set(false);
  }

  private showToast(title: string, isError: boolean): void {
    this.toastTitle.set(title);
    this.toastError.set(isError);
    this.toastOpen.set(true);
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toastOpen.set(false), 5200);
  }
}
