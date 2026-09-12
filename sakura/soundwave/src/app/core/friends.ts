import { Injectable, computed, inject, signal } from '@angular/core';
import { WirehoodApi, FriendRequest } from './wirehood-api';

/** Live friend-request state, shared so every page's header badge and Home's banner agree. */
@Injectable({ providedIn: 'root' })
export class FriendsService {
  private api = inject(WirehoodApi);
  private started = false;

  private requests = signal<FriendRequest[]>([]);
  readonly requestsSignal = this.requests.asReadonly();

  readonly incoming = computed(() => this.requests().filter((r) => r.direction === 'INCOMING'));
  readonly incomingCount = computed(() => this.incoming().length);

  /** Idempotent, safe to call from every page that renders the shared header. */
  ensureStarted(): void {
    if (this.started) return;
    this.started = true;
    this.refresh();
  }

  refresh(): void {
    this.api.friendRequests().subscribe({
      next: (requests) => this.requests.set(requests),
      error: () => {},
    });
  }
}
