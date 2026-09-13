import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { CONTROL_TOWER_URL } from './config';

const POLL_INTERVAL_MS = 30000;

/** Polls control-tower's wirehood-reachability check so the app can show a "service is down" overlay instead of every page silently failing its own API calls one by one. */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private http = inject(HttpClient);

  readonly wirehoodDown = signal(false);

  private started = false;

  ensureStarted(): void {
    if (this.started) return;
    this.started = true;
    this.check();
    setInterval(() => this.check(), POLL_INTERVAL_MS);
  }

  private check(): void {
    this.http.get<{ status: string }>(`${CONTROL_TOWER_URL}/health/wirehood`).subscribe({
      next: (body) => this.wirehoodDown.set(body.status !== 'UP'),
      // A network-level failure reaching control-tower itself is not distinguishable from
      // "wirehood is down" here - either way the app can't actually do anything, same overlay applies
      error: () => this.wirehoodDown.set(true),
    });
  }
}
