import { Injectable, computed, inject, signal } from '@angular/core';
import { WirehoodApi } from './wirehood-api';

export interface QueueTrack {
  trackId: string;
  title: string;
  artist: string;
}

/** Global, persistent audio playback, one real `<audio>` element shared across every page. Video (mp4) stays page-local, see Track Detail's own takeover. */
@Injectable({ providedIn: 'root' })
export class PlaybackService {
  private api = inject(WirehoodApi);
  private audio = new Audio();
  private objectUrl: string | null = null;

  private queue = signal<QueueTrack[]>([]);
  private index = signal(-1);

  readonly nowPlaying = computed<QueueTrack | null>(() => {
    const q = this.queue();
    const i = this.index();
    return i >= 0 && i < q.length ? q[i] : null;
  });

  readonly playing = signal(false);
  readonly loading = signal(false);
  readonly currentTime = signal(0);
  readonly duration = signal(0);
  readonly volume = signal(0.7);

  readonly hasPrevious = computed(() => this.index() > 0);
  readonly hasNext = computed(() => this.index() >= 0 && this.index() < this.queue().length - 1);

  constructor() {
    this.audio.volume = this.volume();
    this.audio.addEventListener('timeupdate', () => this.currentTime.set(this.audio.currentTime));
    this.audio.addEventListener('loadedmetadata', () => this.duration.set(this.audio.duration || 0));
    this.audio.addEventListener('play', () => this.playing.set(true));
    this.audio.addEventListener('pause', () => this.playing.set(false));
    this.audio.addEventListener('ended', () => this.next());
  }

  /** Starts a queue at the given index, previous/next walk this same list. */
  playQueue(tracks: QueueTrack[], startIndex: number): void {
    this.queue.set(tracks);
    this.index.set(startIndex);
    this.loadCurrent();
  }

  playSingle(track: QueueTrack): void {
    this.playQueue([track], 0);
  }

  toggle(): void {
    if (!this.nowPlaying()) return;
    if (this.audio.paused) this.audio.play();
    else this.audio.pause();
  }

  seekTo(fraction: number): void {
    if (!this.audio.duration) return;
    this.audio.currentTime = fraction * this.audio.duration;
  }

  setVolume(fraction: number): void {
    this.volume.set(fraction);
    this.audio.volume = fraction;
  }

  previous(): void {
    if (!this.hasPrevious()) return;
    this.index.update((i) => i - 1);
    this.loadCurrent();
  }

  next(): void {
    if (!this.hasNext()) {
      this.stop();
      return;
    }
    this.index.update((i) => i + 1);
    this.loadCurrent();
  }

  stop(): void {
    this.audio.pause();
    this.audio.removeAttribute('src');
    this.queue.set([]);
    this.index.set(-1);
    this.currentTime.set(0);
    this.duration.set(0);
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }

  private loadCurrent(): void {
    const track = this.nowPlaying();
    if (!track) return;
    this.loading.set(true);
    this.currentTime.set(0);
    this.duration.set(0);
    this.api.downloadFile(track.trackId, 'mp3').subscribe({
      next: (blob) => {
        if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
        this.objectUrl = URL.createObjectURL(blob);
        this.audio.src = this.objectUrl;
        this.audio.play();
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
