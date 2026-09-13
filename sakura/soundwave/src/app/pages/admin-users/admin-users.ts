import { Component, inject, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, FriendSearchResult } from '../../core/wirehood-api';

/**
 * Disable a wirehood user (wirehood-scoped kick, shieldwall login untouched) - see
 * WIREHOOD_PLAN.md's Admin roles section. No endpoint exists to list every wirehood user or to
 * check a user's current enabled/disabled status, or to re-enable one, so this stays a search
 * (reusing the friend-search endpoint) plus a one-way disable action, not a full user-management
 * table - same "simplify rather than fake data" call as Admin Genres.
 */
@Component({
  selector: 'wh-admin-users',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.css',
})
export class AdminUsersPage {
  private api = inject(WirehoodApi);

  protected query = signal('');
  protected results = signal<FriendSearchResult[]>([]);
  protected searching = signal(false);
  protected disabledIds = signal<Set<string>>(new Set());
  protected confirmingId = signal<string | null>(null);

  protected search(): void {
    const q = this.query().trim();
    if (!q) {
      this.results.set([]);
      return;
    }
    this.searching.set(true);
    this.api.searchFriends(q, 20).subscribe({
      next: (results) => {
        this.results.set(results);
        this.searching.set(false);
      },
      error: () => this.searching.set(false),
    });
  }

  protected isDisabled(userId: string): boolean {
    return this.disabledIds().has(userId);
  }

  protected askDisable(userId: string): void {
    this.confirmingId.set(userId);
  }

  protected cancelDisable(): void {
    this.confirmingId.set(null);
  }

  protected confirmDisable(userId: string): void {
    this.api.disableUser(userId).subscribe({
      next: () => {
        this.disabledIds.update((s) => new Set(s).add(userId));
        this.confirmingId.set(null);
      },
    });
  }
}
