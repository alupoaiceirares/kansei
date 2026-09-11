import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

interface PastPick {
  date: string;
  title: string;
  artist: string;
  plays: number;
}

/** Today's pick (large card) plus the last 7 days of past picks with play counts. */
@Component({
  selector: 'wh-song-of-the-day',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './song-of-the-day.html',
  styleUrl: './song-of-the-day.css',
})
export class SongOfTheDayPage {
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected queue = MOCK_DOWNLOAD_QUEUE;

  protected faved = signal(false);

  protected today = {
    date: '10 September 2026',
    title: 'Slow Static Parade',
    artist: 'Halden Mure',
    duration: '4:07',
    genre: 'Ambient',
    plays: '318 plays today',
    description: 'Picked from the shared archive at midnight. Everyone on wirehood gets the same track today.',
  };

  protected past: PastPick[] = [
    { date: 'Sep 9', title: 'Copper Line', artist: 'Ansel Reed', plays: 412 },
    { date: 'Sep 8', title: 'Ghost Frequency', artist: 'Mora Vale', plays: 508 },
    { date: 'Sep 7', title: 'Rust Cathedral', artist: 'The Longwave', plays: 276 },
    { date: 'Sep 6', title: 'Quiet Wire', artist: 'Ivy Sennett', plays: 331 },
    { date: 'Sep 5', title: 'Low Tide Signal', artist: 'Bellhouse', plays: 189 },
    { date: 'Sep 4', title: 'Nightshift Bloom', artist: 'Kestrel Park', plays: 244 },
    { date: 'Sep 3', title: 'Paper Antenna', artist: 'Mora Vale', plays: 297 },
  ];

  protected toggleFav(): void {
    this.faved.update((v) => !v);
  }
}
