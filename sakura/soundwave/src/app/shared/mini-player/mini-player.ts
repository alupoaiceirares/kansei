import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
import { QueueTrack } from '../../core/playback';
import { formatDuration, trackThumbnailUrl } from '../format';

/** Persistent fixed-bottom audio mini-player bar, driven by the app-wide PlaybackService, survives navigation while something plays. */
@Component({
  selector: 'wh-mini-player',
  standalone: true,
  templateUrl: './mini-player.html',
  styleUrl: './mini-player.css',
})
export class MiniPlayerComponent implements OnChanges {
  @Input() nowPlaying: QueueTrack | null = null;
  @Input() playing = false;
  @Input() loading = false;
  @Input() currentTime = 0;
  @Input() duration = 0;
  @Input() volume = 0.7;
  @Input() hasPrevious = false;
  @Input() hasNext = false;
  @Input() shuffle = false;
  @Input() repeatMode: 'off' | 'all' | 'one' = 'off';

  @Output() togglePlay = new EventEmitter<void>();
  @Output() previous = new EventEmitter<void>();
  @Output() next = new EventEmitter<void>();
  @Output() seek = new EventEmitter<number>();
  @Output() volumeChange = new EventEmitter<number>();
  @Output() close = new EventEmitter<void>();
  @Output() toggleShuffle = new EventEmitter<void>();
  @Output() cycleRepeat = new EventEmitter<void>();

  protected thumbnailUrl = trackThumbnailUrl;

  // Same reasoning as Track Detail's thumbFailed - this component is mounted once globally
  // (app.html), so it's the same <img> element reused across every track change, not a fresh one
  // per track. A signal (reset on track change) instead of the shared imperative hideOnError,
  // which would leave a stale hidden state from the previous track's 404 on this reused element.
  protected thumbOk = signal(true);

  protected onThumbError(): void {
    this.thumbOk.set(false);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['nowPlaying']) {
      this.thumbOk.set(true);
    }
  }

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
