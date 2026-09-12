import { Component, computed, inject, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, Friend, FriendRequest, FriendSearchResult } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { FriendsService } from '../../core/friends';

type Tab = 'Friends' | 'Incoming' | 'Outgoing';

const PALETTE = [
  { avatarBg: 'rgba(18,183,107,0.13)', ringColor: 'rgba(18,183,107,0.35)', initialColor: '#12B76B' },
  { avatarBg: 'rgba(226,58,58,0.13)', ringColor: 'rgba(226,58,58,0.35)', initialColor: '#E23A3A' },
];

/** Friends/Incoming/Outgoing tabs, plus a wirehood-wide user search with relation-aware actions. */
@Component({
  selector: 'wh-friends',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './friends.html',
  styleUrl: './friends.css',
})
export class FriendsPage {
  private api = inject(WirehoodApi);
  private auth = inject(AuthService);
  protected friendsService = inject(FriendsService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected tab = signal<Tab>('Friends');
  protected filterText = signal('');
  protected tabOrder: Tab[] = ['Friends', 'Incoming', 'Outgoing'];

  protected friendsList = signal<Friend[]>([]);
  protected searchResults = signal<FriendSearchResult[]>([]);
  protected searching = signal(false);

  private searchDebounce?: ReturnType<typeof setTimeout>;

  constructor() {
    this.reloadFriends();
    this.friendsService.ensureStarted();
  }

  private reloadFriends(): void {
    this.api.friends().subscribe({ next: (list) => this.friendsList.set(list) });
  }

  protected outgoing = computed(() => this.friendsService.requestsSignal().filter((r) => r.direction === 'OUTGOING'));

  protected tabCounts = computed(() => ({
    Friends: this.friendsList().length,
    Incoming: this.friendsService.incoming().length,
    Outgoing: this.outgoing().length,
  }));

  protected people = computed(() => {
    const tab = this.tab();
    const list: (Friend | FriendRequest)[] = tab === 'Friends' ? this.friendsList() : tab === 'Incoming' ? this.friendsService.incoming() : this.outgoing();
    return list.map((p, i) => ({ ...p, ...PALETTE[i % 2], initial: p.username.charAt(0).toUpperCase() }));
  });

  protected onSearchInput(value: string): void {
    this.filterText.set(value);
    clearTimeout(this.searchDebounce);
    const q = value.trim();
    if (!q) {
      this.searchResults.set([]);
      return;
    }
    this.searchDebounce = setTimeout(() => {
      this.searching.set(true);
      this.api.searchFriends(q).subscribe({
        next: (results) => {
          this.searchResults.set(results);
          this.searching.set(false);
        },
        error: () => this.searching.set(false),
      });
    }, 300);
  }

  protected pickTab(t: Tab): void {
    this.tab.set(t);
  }

  protected sendRequest(userId: string): void {
    this.api.sendFriendRequest(userId).subscribe({ next: () => this.refreshSearch() });
  }

  protected accept(userId: string): void {
    this.api.acceptFriendRequest(userId).subscribe({
      next: () => {
        this.reloadFriends();
        this.friendsService.refresh();
        this.refreshSearch();
      },
    });
  }

  protected remove(userId: string): void {
    this.api.removeFriendOrRequest(userId).subscribe({
      next: () => {
        this.reloadFriends();
        this.friendsService.refresh();
        this.refreshSearch();
      },
    });
  }

  private refreshSearch(): void {
    const q = this.filterText().trim();
    if (!q) return;
    this.api.searchFriends(q).subscribe({ next: (results) => this.searchResults.set(results) });
  }
}
