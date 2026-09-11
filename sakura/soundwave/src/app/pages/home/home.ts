import { Component, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { MiniPlayerComponent, NowPlayingTrack } from '../../shared/mini-player/mini-player';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

interface RecentTrack {
  title: string;
  artist: string;
  formats: string[];
  added: string;
}

/** Logo-led logged-in landing page: search, Song of the Day, recently added grid. */
@Component({
  selector: 'wh-home',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, MiniPlayerComponent, WirehoodWavesComponent],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomePage {
  // Stand-ins for API responses (musicProfile/library/friend-request data) until wired up.
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected isEmpty = signal(false);
  protected playing = signal(false);
  protected searchQuery = signal('');

  protected genres = ['Shoegaze', 'Dub techno', 'Alt country', 'Ambient', 'Post-punk'];

  protected sotd = { title: 'Slow Static Parade', artist: 'Halden Mure', duration: '4:07' };

  protected nowPlaying: NowPlayingTrack = { title: 'Ghost Frequency', artist: 'Mora Vale' };

  protected queue = MOCK_DOWNLOAD_QUEUE;

  protected recent: RecentTrack[] = [
    { title: 'Ghost Frequency', artist: 'Mora Vale', formats: ['MP3', 'MP4'], added: '2h ago' },
    { title: 'Tin Roof Static', artist: 'The Longwave', formats: ['MP3'], added: 'Yesterday' },
    { title: 'Copper Line', artist: 'Ansel Reed', formats: ['MP3', 'MP4'], added: '3d ago' },
    { title: 'Nightshift Bloom', artist: 'Kestrel Park', formats: ['MP4'], added: '5d ago' },
    { title: 'Halogen Hymn', artist: 'Ivy Sennett', formats: ['MP3'], added: '1w ago' },
  ];

  constructor(private router: Router) {}

  protected get hasRequests(): boolean {
    return this.incomingRequests() > 0 && !this.isEmpty();
  }

  protected get requestText(): string {
    const n = this.incomingRequests();
    return n === 1 ? '1 friend request waiting' : `${n} friend requests waiting`;
  }

  protected togglePlay(): void {
    this.playing.update((v) => !v);
  }

  protected playSotd(): void {
    this.playing.set(true);
  }

  protected submitSearch(): void {
    this.router.navigate(['/search'], { queryParams: { q: this.searchQuery() || undefined } });
  }
}
