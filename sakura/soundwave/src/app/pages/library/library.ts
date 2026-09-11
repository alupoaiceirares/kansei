import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { MiniPlayerComponent, NowPlayingTrack } from '../../shared/mini-player/mini-player';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

interface LibraryTrack {
  title: string;
  artist: string;
  formats: string[];
  added: string;
  plays: number;
  faved: boolean;
}

interface Collection {
  name: string;
  meta: string;
}

type FormatFilter = 'All' | 'MP3' | 'MP4';

/** Collections row (Favorites + playlists) plus the full downloaded-tracks grid, filterable by format/text. */
@Component({
  selector: 'wh-library',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, MiniPlayerComponent, WirehoodWavesComponent],
  templateUrl: './library.html',
  styleUrl: './library.css',
})
export class LibraryPage {
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected queue = MOCK_DOWNLOAD_QUEUE;

  protected playing = signal(true);
  protected nowPlaying: NowPlayingTrack = { title: 'Ghost Frequency', artist: 'Mora Vale' };

  protected filter = signal<FormatFilter>('All');
  protected filterText = signal('');
  protected page = signal(1);
  protected totalPages = 7;

  protected filterOptions: FormatFilter[] = ['All', 'MP3', 'MP4'];

  protected collections: Collection[] = [
    { name: 'Late shift', meta: '31 tracks · shared' },
    { name: 'Wire & static', meta: '18 tracks · private' },
    { name: 'Reed sessions', meta: '9 tracks · collaborating' },
  ];

  private allTracks: LibraryTrack[] = [
    { title: 'Ghost Frequency', artist: 'Mora Vale', formats: ['MP3', 'MP4'], added: '2h ago', plays: 41, faved: true },
    { title: 'Tin Roof Static', artist: 'The Longwave', formats: ['MP3'], added: 'Yesterday', plays: 12, faved: false },
    { title: 'Copper Line', artist: 'Ansel Reed', formats: ['MP3', 'MP4'], added: '3d ago', plays: 88, faved: true },
    { title: 'Nightshift Bloom', artist: 'Kestrel Park', formats: ['MP4'], added: '5d ago', plays: 7, faved: false },
    { title: 'Halogen Hymn', artist: 'Ivy Sennett', formats: ['MP3'], added: '1w ago', plays: 134, faved: true },
    { title: 'Slow Static Parade', artist: 'Halden Mure', formats: ['MP3', 'MP4'], added: '1w ago', plays: 56, faved: false },
    { title: 'Paper Antenna', artist: 'Mora Vale', formats: ['MP3'], added: '2w ago', plays: 23, faved: false },
    { title: 'Low Tide Signal', artist: 'Bellhouse', formats: ['MP3', 'MP4'], added: '2w ago', plays: 62, faved: true },
    { title: 'Rust Cathedral', artist: 'The Longwave', formats: ['MP4'], added: '3w ago', plays: 19, faved: false },
    { title: 'Verdigris', artist: 'Ansel Reed', formats: ['MP3'], added: '3w ago', plays: 71, faved: false },
    { title: 'Quiet Wire', artist: 'Ivy Sennett', formats: ['MP3', 'MP4'], added: '1mo ago', plays: 205, faved: true },
    { title: 'Marbled Sky', artist: 'Kestrel Park', formats: ['MP3'], added: '1mo ago', plays: 34, faved: false },
  ];

  protected tracks = computed(() => {
    const f = this.filter();
    const text = this.filterText().trim().toLowerCase();
    return this.allTracks
      .filter((t) => f === 'All' || t.formats.includes(f))
      .filter((t) => !text || t.title.toLowerCase().includes(text) || t.artist.toLowerCase().includes(text));
  });

  protected togglePlay(): void {
    this.playing.update((v) => !v);
  }

  protected pickFilter(f: FormatFilter): void {
    this.filter.set(f);
    this.page.set(1);
  }

  protected prevPage(): void {
    this.page.update((p) => Math.max(1, p - 1));
  }

  protected nextPage(): void {
    this.page.update((p) => Math.min(this.totalPages, p + 1));
  }
}
