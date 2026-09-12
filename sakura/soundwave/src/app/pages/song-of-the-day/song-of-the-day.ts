import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { MiniPlayerComponent, NowPlayingTrack } from '../../shared/mini-player/mini-player';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, SongOfDay, TrackDetail } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { formatDuration } from '../../shared/format';

/** Today's shared pick, real from wirehood. Past picks have no history endpoint yet, see TODO.MD. */
@Component({
  selector: 'wh-song-of-the-day',
  standalone: true,
  imports: [RouterLink, DatePipe, AppHeaderComponent, MiniPlayerComponent, WirehoodWavesComponent],
  templateUrl: './song-of-the-day.html',
  styleUrl: './song-of-the-day.css',
})
export class SongOfTheDayPage {
  private api = inject(WirehoodApi);
  private auth = inject(AuthService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected sotd = signal<SongOfDay | null>(null);
  protected track = signal<TrackDetail | null>(null);
  protected nowPlaying = signal<NowPlayingTrack | null>(null);
  protected playing = signal(false);

  constructor() {
    this.api.songOfTheDay().subscribe({
      next: (sotd) => {
        this.sotd.set(sotd);
        this.api.track(sotd.trackId).subscribe({ next: (track) => this.track.set(track) });
      },
      error: () => this.sotd.set(null),
    });
  }

  protected formattedDuration = computed(() => {
    const sotd = this.sotd();
    return sotd ? formatDuration(sotd.durationSeconds) : '';
  });

  protected audioFormat = computed(() => this.track()?.formats.find((f) => f.format.toLowerCase() === 'mp3') ?? null);
  protected videoFormat = computed(() => this.track()?.formats.find((f) => f.format.toLowerCase() === 'mp4') ?? null);

  protected togglePlay(): void {
    this.playing.update((v) => !v);
  }

  protected play(): void {
    const sotd = this.sotd();
    if (!sotd) return;
    this.nowPlaying.set({ title: sotd.title, artist: sotd.artist });
    this.playing.set(true);
  }

  protected toggleFav(): void {
    const format = this.audioFormat() ?? this.videoFormat();
    if (!format) return;
    const call = format.favorited ? this.api.unfavoriteFormat(format.id) : this.api.favoriteFormat(format.id);
    call.subscribe({
      next: () => {
        this.track.update((t) => (t ? { ...t, formats: t.formats.map((f) => (f.id === format.id ? { ...f, favorited: !f.favorited } : f)) } : t));
      },
    });
  }

  protected faved(): boolean {
    return !!(this.audioFormat() ?? this.videoFormat())?.favorited;
  }
}
