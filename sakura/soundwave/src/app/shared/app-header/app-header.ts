import { Component, Input, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { WirehoodMarkComponent } from '../wirehood-mark/wirehood-mark';
import { AuthService } from '../../core/auth';
import { DownloadsService } from '../../core/downloads';
import { FriendsService } from '../../core/friends';

interface MenuItem {
  label: string;
  route: string;
  badge?: string | null;
  badgeColor?: string;
}

/** Shared header chrome for every authenticated wirehood page, logo, nav pills, downloads bell, sign out, burger menu. */
@Component({
  selector: 'wh-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, WirehoodMarkComponent],
  templateUrl: './app-header.html',
  styleUrl: './app-header.css',
})
export class AppHeaderComponent {
  @Input() isAdmin = false;
  @Input() adminThumbnailCount = 0;
  @Input() adminGenreCount = 0;
  /** Admin Thumbnails/Genres pages swap the Home/Search/Library pills for Thumbnails/Genres + an Admin badge. */
  @Input() adminHeader = false;

  private auth = inject(AuthService);
  protected downloads = inject(DownloadsService);
  protected friends = inject(FriendsService);

  protected menuOpen = signal(false);
  protected trayOpen = signal(false);

  constructor() {
    this.downloads.ensureStarted();
    this.friends.ensureStarted();
  }

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

  protected retryDownload(id: string): void {
    this.downloads.retry(id);
  }

  protected get menuItems(): MenuItem[] {
    const pendingDownloads = this.downloads.pendingCount();
    const incomingRequests = this.friends.incomingCount();
    return [
      { label: 'Home', route: '/home' },
      { label: 'Search & Download', route: '/search' },
      { label: 'Library', route: '/library' },
      { label: 'Favorites', route: '/favorites' },
      { label: 'Playlists', route: '/playlists' },
      {
        label: 'Friends',
        route: '/friends',
        badge: incomingRequests > 0 ? String(incomingRequests) : null,
        badgeColor: '#E23A3A',
      },
      { label: 'Song of the Day', route: '/song-of-the-day' },
      { label: 'Music Profile', route: '/stats' },
      { label: 'Downloads', route: '/search', badge: pendingDownloads > 0 ? String(pendingDownloads) : null },
      { label: 'Account', route: '/account' },
    ];
  }
}
