import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { WirehoodMarkComponent } from '../wirehood-mark/wirehood-mark';
import { AuthService } from '../../core/auth';

interface MenuItem {
  label: string;
  route: string;
  badge?: string | null;
  badgeColor?: string;
}

export type QueueStatus = 'pending' | 'failed' | 'ready';

export interface DownloadQueueItem {
  id: string;
  title: string;
  status: QueueStatus;
  statusText: string;
}

/** Shared header chrome for every authenticated wirehood page: logo, nav pills, downloads bell, sign out, burger menu. */
@Component({
  selector: 'wh-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, WirehoodMarkComponent],
  templateUrl: './app-header.html',
  styleUrl: './app-header.css',
})
export class AppHeaderComponent {
  @Input() pendingDownloads = 0;
  @Input() incomingRequests = 0;
  @Input() isAdmin = false;
  @Input() adminThumbnailCount = 0;
  @Input() adminGenreCount = 0;
  @Input() queue: DownloadQueueItem[] = [];
  /** Admin Thumbnails/Genres pages swap the Home/Search/Library pills for Thumbnails/Genres + an Admin badge. */
  @Input() adminHeader = false;

  @Output() retryDownload = new EventEmitter<string>();

  private auth = inject(AuthService);

  protected menuOpen = signal(false);
  protected trayOpen = signal(false);

  protected signOut(): void {
    this.auth.signOut();
  }

  protected toggleMenu(): void {
    this.menuOpen.update((v) => !v);
    this.trayOpen.set(false);
  }

  protected toggleTray(): void {
    this.trayOpen.update((v) => !v);
    this.menuOpen.set(false);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected get menuItems(): MenuItem[] {
    return [
      { label: 'Home', route: '/home' },
      { label: 'Search & Download', route: '/search' },
      { label: 'Library', route: '/library' },
      { label: 'Favorites', route: '/favorites' },
      { label: 'Playlists', route: '/playlists' },
      {
        label: 'Friends',
        route: '/friends',
        badge: this.incomingRequests > 0 ? String(this.incomingRequests) : null,
        badgeColor: '#E23A3A',
      },
      { label: 'Song of the Day', route: '/song-of-the-day' },
      { label: 'Music Profile', route: '/stats' },
      { label: 'Downloads', route: '/search', badge: this.pendingDownloads > 0 ? String(this.pendingDownloads) : null },
      { label: 'Account', route: '/account' },
    ];
  }
}
