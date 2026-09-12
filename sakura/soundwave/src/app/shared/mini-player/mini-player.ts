import { Component, EventEmitter, Input, Output } from '@angular/core';
import { QueueTrack } from '../../core/playback';
import { formatDuration } from '../format';

/** Persistent fixed-bottom audio mini-player bar, driven by the app-wide PlaybackService, survives navigation while something plays. */
@Component({
  selector: 'wh-mini-player',
  standalone: true,
  templateUrl: './mini-player.html',
  styleUrl: './mini-player.css',
})
export class MiniPlayerComponent {
  @Input() nowPlaying: QueueTrack | null = null;
  @Input() playing = false;
  @Input() loading = false;
  @Input() currentTime = 0;
  @Input() duration = 0;
  @Input() volume = 0.7;
  @Input() hasPrevious = false;
  @Input() hasNext = false;

  @Output() togglePlay = new EventEmitter<void>();
  @Output() previous = new EventEmitter<void>();
  @Output() next = new EventEmitter<void>();
  @Output() seek = new EventEmitter<number>();
  @Output() volumeChange = new EventEmitter<number>();

  protected formatTime(seconds: number): string {
    return formatDuration(seconds || 0);
  }

  protected onSeek(value: string): void {
    this.seek.emit(Number(value) / 1000);
  }

  protected onVolume(value: string): void {
    this.volumeChange.emit(Number(value) / 100);
  }

  protected get progressPermille(): number {
    if (!this.duration) return 0;
    return Math.round((this.currentTime / this.duration) * 1000);
  }
}
