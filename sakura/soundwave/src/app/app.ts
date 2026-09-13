import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MiniPlayerComponent } from './shared/mini-player/mini-player';
import { ServiceDownOverlayComponent } from './shared/service-down-overlay/service-down-overlay';
import { PlaybackService } from './core/playback';
import { AuthService } from './core/auth';
import { WirehoodApi } from './core/wirehood-api';

@Component({
  imports: [RouterOutlet, MiniPlayerComponent, ServiceDownOverlayComponent],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected playback = inject(PlaybackService);
  private auth = inject(AuthService);
  private api = inject(WirehoodApi);

  constructor() {
    // Role/enabled only ever got cached once, at opt-in time, with no refresh path - a role
    // change made directly in the DB (e.g. promoting to ADMIN) was invisible until the browser's
    // localStorage happened to get cleared. Re-sync it once per app load instead.
    if (this.auth.isAuthenticated() && this.auth.isOptedIn()) {
      this.api.me().subscribe({
        next: (me) => this.auth.markOptedIn(me.role, me.joinedAt),
        error: () => {},
      });
    }
  }
}
