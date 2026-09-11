import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface NowPlayingTrack {
  title: string;
  artist: string;
}

/** Persistent fixed-bottom audio mini-player bar — survives navigation while something plays. */
@Component({
  selector: 'wh-mini-player',
  standalone: true,
  templateUrl: './mini-player.html',
  styleUrl: './mini-player.css',
})
export class MiniPlayerComponent {
  @Input() nowPlaying: NowPlayingTrack | null = null;
  @Input() playing = false;

  @Output() togglePlay = new EventEmitter<void>();
  @Output() previous = new EventEmitter<void>();
  @Output() next = new EventEmitter<void>();
}
