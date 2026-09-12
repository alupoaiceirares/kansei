import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, FavoriteItem } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { PlaybackService } from '../../core/playback';
import { formatRelativeTime, saveBlob } from '../../shared/format';

type FormatFilter = 'All' | 'MP3' | 'MP4';

/** Per-format hearts, a track can appear twice if both MP3 and MP4 are favorited independently. */
@Component({
  selector: 'wh-favorites',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './favorites.html',
  styleUrl: './favorites.css',
})
export class FavoritesPage {
  private api = inject(WirehoodApi);
  private auth = inject(AuthService);
  private playback = inject(PlaybackService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected filter = signal<FormatFilter>('All');
  protected filterOptions: FormatFilter[] = ['All', 'MP3', 'MP4'];

  private all = signal<FavoriteItem[]>([]);

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.api.favorites(0, 200).subscribe({ next: (page) => this.all.set(page.items) });
  }

  protected items = computed(() => {
    const f = this.filter();
    const format = f.toLowerCase();
    return f === 'All' ? this.all() : this.all().filter((t) => t.format.toLowerCase() === format);
  });

  protected countText = computed(() => {
    const f = this.filter();
    const n = this.items().length;
    return f === 'All' ? `${n} hearted formats` : `${n} ${f} favorites`;
  });

  protected pickFilter(f: FormatFilter): void {
    this.filter.set(f);
  }

  protected formatColor(format: string): string {
    return format.toLowerCase() === 'mp3' ? '#12B76B' : '#E23A3A';
  }

  protected when(iso: string): string {
    return formatRelativeTime(iso);
  }

  protected unfavorite(trackFormatId: string): void {
    this.api.unfavoriteFormat(trackFormatId).subscribe({
      next: () => this.all.update((items) => items.filter((i) => i.trackFormatId !== trackFormatId)),
    });
  }

  protected playItem(item: FavoriteItem): void {
    if (item.format.toLowerCase() !== 'mp3') return;
    const playable = this.items().filter((x) => x.format.toLowerCase() === 'mp3');
    const index = playable.findIndex((x) => x.trackFormatId === item.trackFormatId);
    if (index === -1) return;
    this.playback.playQueue(
      playable.map((x) => ({ trackId: x.trackId, title: x.title, artist: x.artist })),
      index,
    );
  }

  protected downloadItem(item: FavoriteItem): void {
    this.api.downloadFile(item.trackId, item.format).subscribe({ next: (blob) => saveBlob(blob, `${item.artist} - ${item.title}.${item.format}`) });
  }
}
