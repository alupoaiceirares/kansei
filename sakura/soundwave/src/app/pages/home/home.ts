import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, LibraryItem, SongOfDay, TrackDetail } from '../../core/wirehood-api';
import { FriendsService } from '../../core/friends';
import { AuthService } from '../../core/auth';
import { PlaybackService } from '../../core/playback';
import { formatDuration, formatRelativeTime, saveBlob } from '../../shared/format';

interface RecentTrack {
  trackId: string;
  title: string;
  artist: string;
  formats: string[];
  added: string;
}

/** Logo-led logged-in landing page, search, Song of the Day, recently added grid. */
@Component({
  selector: 'wh-home',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomePage {
  protected formatDuration = formatDuration;
  protected friends = inject(FriendsService);
  private auth = inject(AuthService);
  private playback = inject(PlaybackService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected isEmpty = signal(false);
  protected loaded = signal(false);
  protected searchQuery = signal('');

  protected genres = signal<string[]>([]);
  protected sotd = signal<SongOfDay | null>(null);
  protected sotdTrack = signal<TrackDetail | null>(null);
  protected recent = signal<RecentTrack[]>([]);

  constructor(
    private router: Router,
    private api: WirehoodApi,
  ) {
    this.api.genres().subscribe({
      next: (genres) => this.genres.set(genres.slice(0, 5).map((g) => g.name)),
      error: () => {},
    });

    // 404 just means no pick exists yet (empty dev DB, or the daily job hasn't run) - not an error state
    this.api.songOfTheDay().subscribe({
      next: (sotd) => {
        this.sotd.set(sotd);
        this.api.track(sotd.trackId).subscribe({ next: (track) => this.sotdTrack.set(track) });
      },
      error: () => this.sotd.set(null),
    });

    this.friends.ensureStarted();

    this.api.library(0, 5).subscribe({
      next: (page) => {
        this.recent.set(page.items.map(toRecentTrack));
        this.isEmpty.set(page.totalElements === 0);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  protected get hasRequests(): boolean {
    return this.friends.incomingCount() > 0 && !this.isEmpty();
  }

  protected get requestText(): string {
    const n = this.friends.incomingCount();
    return n === 1 ? '1 friend request waiting' : `${n} friend requests waiting`;
  }

  protected get showRecent(): boolean {
    return this.loaded() && !this.isEmpty();
  }

  private sotdAudioFormat = computed(() => this.sotdTrack()?.formats.find((f) => f.format.toLowerCase() === 'mp3') ?? null);

  protected sotdFaved = computed(() => !!this.sotdAudioFormat()?.favorited);

  protected playSotd(): void {
    const sotd = this.sotd();
    if (!sotd) return;
    this.playback.playSingle({ trackId: sotd.trackId, title: sotd.title, artist: sotd.artist });
  }

  protected downloadSotd(): void {
    const sotd = this.sotd();
    if (!sotd) return;
    this.api.downloadFile(sotd.trackId, 'mp3').subscribe({ next: (blob) => saveBlob(blob, `${sotd.artist} - ${sotd.title}.mp3`) });
  }

  protected toggleFavSotd(): void {
    const format = this.sotdAudioFormat();
    if (!format) return;
    const call = format.favorited ? this.api.unfavoriteFormat(format.id) : this.api.favoriteFormat(format.id);
    call.subscribe({
      next: () =>
        this.sotdTrack.update((t) => (t ? { ...t, formats: t.formats.map((f) => (f.id === format.id ? { ...f, favorited: !f.favorited } : f)) } : t)),
    });
  }

  protected submitSearch(): void {
    this.router.navigate(['/search'], { queryParams: { q: this.searchQuery() || undefined } });
  }
}

function toRecentTrack(item: LibraryItem): RecentTrack {
  return {
    trackId: item.trackId,
    title: item.title,
    artist: item.artist,
    formats: item.formats.map((f) => f.format.toUpperCase()),
    added: formatRelativeTime(item.addedAt),
  };
}
