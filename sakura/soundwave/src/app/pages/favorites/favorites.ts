import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

interface FavoriteItem {
  title: string;
  artist: string;
  format: 'MP3' | 'MP4';
  when: string;
}

type FormatFilter = 'All' | 'MP3' | 'MP4';

/** Per-format hearts — a track can appear twice if both MP3 and MP4 are favorited independently. */
@Component({
  selector: 'wh-favorites',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './favorites.html',
  styleUrl: './favorites.css',
})
export class FavoritesPage {
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected queue = MOCK_DOWNLOAD_QUEUE;

  protected filter = signal<FormatFilter>('All');
  protected filterOptions: FormatFilter[] = ['All', 'MP3', 'MP4'];

  private all: FavoriteItem[] = [
    { title: 'Ghost Frequency', artist: 'Mora Vale', format: 'MP3', when: '2h ago' },
    { title: 'Ghost Frequency', artist: 'Mora Vale', format: 'MP4', when: '2h ago' },
    { title: 'Copper Line', artist: 'Ansel Reed', format: 'MP3', when: '3d ago' },
    { title: 'Halogen Hymn', artist: 'Ivy Sennett', format: 'MP3', when: '1w ago' },
    { title: 'Low Tide Signal', artist: 'Bellhouse', format: 'MP4', when: '2w ago' },
    { title: 'Quiet Wire', artist: 'Ivy Sennett', format: 'MP3', when: '3w ago' },
    { title: 'Rust Cathedral', artist: 'The Longwave', format: 'MP4', when: '3w ago' },
    { title: 'Verdigris', artist: 'Ansel Reed', format: 'MP3', when: '1mo ago' },
    { title: 'Marbled Sky', artist: 'Kestrel Park', format: 'MP3', when: '1mo ago' },
    { title: 'Paper Antenna', artist: 'Mora Vale', format: 'MP4', when: '2mo ago' },
  ];

  protected items = computed(() => {
    const f = this.filter();
    return f === 'All' ? this.all : this.all.filter((t) => t.format === f);
  });

  protected countText = computed(() => {
    const f = this.filter();
    const n = this.items().length;
    return f === 'All' ? `${n} hearted formats` : `${n} ${f} favorites`;
  });

  protected pickFilter(f: FormatFilter): void {
    this.filter.set(f);
  }

  protected formatColor(format: 'MP3' | 'MP4'): string {
    return format === 'MP3' ? '#12B76B' : '#E23A3A';
  }
}
