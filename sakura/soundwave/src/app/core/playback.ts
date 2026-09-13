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

  readonly shuffle = signal(false);
  readonly repeatMode = signal<'off' | 'all' | 'one'>('off');

  readonly hasPrevious = computed(() => this.index() > 0);
  // With shuffle or repeat-all, "Next" always has somewhere to go as long as there's more than
  // one track - repeat-one deliberately doesn't factor in here, it only loops the current track
  // on natural end (see onEnded), a manual Next press still advances normally either way
  readonly hasNext = computed(() => {
    const len = this.queue().length;
    if (len <= 1) return false;
    if (this.shuffle() || this.repeatMode() === 'all') return true;
    return this.index() < len - 1;
  });

  constructor() {
    this.audio.volume = this.volume();
    this.audio.addEventListener('timeupdate', () => this.currentTime.set(this.audio.currentTime));
    this.audio.addEventListener('loadedmetadata', () => this.duration.set(this.audio.duration || 0));
    this.audio.addEventListener('play', () => this.playing.set(true));
    this.audio.addEventListener('pause', () => this.playing.set(false));
    this.audio.addEventListener('ended', () => this.onEnded());
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

  toggleShuffle(): void {
    this.shuffle.update((v) => !v);
  }

  cycleRepeat(): void {
    const order: Array<'off' | 'all' | 'one'> = ['off', 'all', 'one'];
    this.repeatMode.update((m) => order[(order.indexOf(m) + 1) % order.length]);
  }

  // Natural end of track - repeat-one loops in place (just seeks back, no refetch needed),
  // anything else falls through to the normal next() logic (shuffle/repeat-all/stop)
  private onEnded(): void {
    if (this.repeatMode() === 'one') {
      this.audio.currentTime = 0;
      this.audio.play();
      return;
    }
    this.next();
  }

  // Previous always steps back sequentially regardless of shuffle - only "next" picks randomly,
  // matching how most players treat shuffle (affects what's coming, not where you've already been)
  previous(): void {
    if (!this.hasPrevious()) return;
    this.index.update((i) => i - 1);
    this.loadCurrent();
  }

  next(): void {
    const len = this.queue().length;
    if (len === 0) return;

    if (this.shuffle() && len > 1) {
      const current = this.index();
      let nextIndex = current;
      while (nextIndex === current) {
        nextIndex = Math.floor(Math.random() * len);
      }
      this.index.set(nextIndex);
      this.loadCurrent();
      return;
    }

    if (this.index() < len - 1) {
      this.index.update((i) => i + 1);
      this.loadCurrent();
      return;
    }

    if (this.repeatMode() === 'all') {
      this.index.set(0);
      this.loadCurrent();
      return;
    }

    this.stop();
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
