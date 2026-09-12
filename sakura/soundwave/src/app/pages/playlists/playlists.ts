import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, Playlist, PlaylistDetail, LibraryItem } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';
import { PlaybackService } from '../../core/playback';

type PlaylistTag = 'Shared' | 'Private' | 'Collab';

const TAG_STYLE: Record<PlaylistTag, { bg: string; color: string }> = {
  Shared: { bg: 'rgba(18,183,107,0.14)', color: '#12B76B' },
  Private: { bg: 'rgba(255,255,255,0.07)', color: 'rgba(242,240,236,0.6)' },
  Collab: { bg: 'rgba(226,58,58,0.18)', color: '#F2565A' },
};

const COLLAB_PALETTE = [
  { avatarBg: 'rgba(18,183,107,0.13)', ring: 'rgba(18,183,107,0.32)', initialColor: '#12B76B' },
  { avatarBg: 'rgba(226,58,58,0.17)', ring: 'rgba(226,58,58,0.32)', initialColor: '#F2565A' },
];

function tagFor(p: Playlist, myUserId: string | null): PlaylistTag {
  if (p.ownerId !== myUserId) return 'Collab';
  return p.shared ? 'Shared' : 'Private';
}

/** List view (owned/shared/collab) leads to detail view with reorderable tracks and collaborators, all real. */
@Component({
  selector: 'wh-playlists',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './playlists.html',
  styleUrl: './playlists.css',
})
export class PlaylistsPage {
  private api = inject(WirehoodApi);
  private auth = inject(AuthService);
  private playback = inject(PlaybackService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected view = signal<'list' | 'detail'>('list');
  protected playlists = signal<Playlist[]>([]);
  protected activePlaylist = signal<PlaylistDetail | null>(null);

  protected tagStyle = TAG_STYLE;

  protected creating = signal(false);
  protected newName = signal('');
  protected newShared = signal(false);

  protected renaming = signal(false);
  protected renameName = signal('');

  protected addingTracks = signal(false);
  protected libraryQuery = signal('');
  private library = signal<LibraryItem[]>([]);

  protected addingCollaborator = signal(false);
  protected collaboratorUserId = signal('');

  private dragIndex: number | null = null;

  constructor() {
    this.reloadList();
  }

  private reloadList(): void {
    this.api.myPlaylists().subscribe({ next: (list) => this.playlists.set(list) });
  }

  protected rowTag(p: Playlist): PlaylistTag {
    return tagFor(p, this.auth.getUserId());
  }

  protected isOwner(): boolean {
    const pl = this.activePlaylist();
    return !!pl && pl.ownerId === this.auth.getUserId();
  }

  protected openDetail(p: Playlist): void {
    this.api.playlist(p.id).subscribe({
      next: (detail) => {
        this.activePlaylist.set(detail);
        this.view.set('detail');
      },
    });
  }

  private reloadDetail(): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.api.playlist(pl.id).subscribe({ next: (detail) => this.activePlaylist.set(detail) });
  }

  protected backToList(): void {
    this.view.set('list');
    this.activePlaylist.set(null);
    this.reloadList();
  }

  protected startCreate(): void {
    this.creating.set(true);
    this.newName.set('');
    this.newShared.set(false);
  }

  protected cancelCreate(): void {
    this.creating.set(false);
  }

  protected confirmCreate(): void {
    const name = this.newName().trim();
    if (!name) return;
    this.api.createPlaylist(name, this.newShared()).subscribe({
      next: () => {
        this.creating.set(false);
        this.reloadList();
      },
    });
  }

  protected startRename(): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.renameName.set(pl.name);
    this.renaming.set(true);
  }

  protected cancelRename(): void {
    this.renaming.set(false);
  }

  protected confirmRename(): void {
    const pl = this.activePlaylist();
    const name = this.renameName().trim();
    if (!pl || !name) return;
    this.api.updatePlaylist(pl.id, name, pl.shared).subscribe({
      next: () => {
        this.renaming.set(false);
        this.reloadDetail();
      },
    });
  }

  protected toggleShared(): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.api.updatePlaylist(pl.id, pl.name, !pl.shared).subscribe({ next: () => this.reloadDetail() });
  }

  protected deletePlaylist(): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.api.deletePlaylist(pl.id).subscribe({ next: () => this.backToList() });
  }

  protected playAll(): void {
    const pl = this.activePlaylist();
    if (!pl || pl.tracks.length === 0) return;
    this.playback.playQueue(
      pl.tracks.map((t) => ({ trackId: t.trackId, title: t.title, artist: t.artist })),
      0,
    );
  }

  protected removeTrack(trackId: string): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.api.removeTrackFromPlaylist(pl.id, trackId).subscribe({ next: () => this.reloadDetail() });
  }

  protected onDragStart(index: number): void {
    this.dragIndex = index;
  }

  protected onDrop(index: number): void {
    const pl = this.activePlaylist();
    if (!pl || this.dragIndex === null || this.dragIndex === index) return;
    const next = [...pl.tracks];
    const [moved] = next.splice(this.dragIndex, 1);
    next.splice(index, 0, moved);
    this.dragIndex = null;
    this.activePlaylist.set({ ...pl, tracks: next.map((t, i) => ({ ...t, position: i + 1 })) });
    this.api.reorderPlaylistTracks(pl.id, next.map((t) => t.trackId)).subscribe({ error: () => this.reloadDetail() });
  }

  protected startAddTracks(): void {
    this.addingTracks.set(true);
    this.libraryQuery.set('');
    if (this.library().length === 0) {
      this.api.library(0, 200).subscribe({ next: (page) => this.library.set(page.items) });
    }
  }

  protected cancelAddTracks(): void {
    this.addingTracks.set(false);
  }

  protected addableTracks = computed(() => {
    const pl = this.activePlaylist();
    const already = new Set((pl?.tracks ?? []).map((t) => t.trackId));
    const q = this.libraryQuery().trim().toLowerCase();
    return this.library()
      .filter((t) => !already.has(t.trackId))
      .filter((t) => !q || t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q))
      .slice(0, 30);
  });

  protected addTrack(trackId: string): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.api.addTrackToPlaylist(pl.id, trackId).subscribe({ next: () => this.reloadDetail() });
  }

  protected startAddCollaborator(): void {
    this.addingCollaborator.set(true);
    this.collaboratorUserId.set('');
  }

  protected cancelAddCollaborator(): void {
    this.addingCollaborator.set(false);
  }

  protected confirmAddCollaborator(): void {
    const pl = this.activePlaylist();
    const userId = this.collaboratorUserId().trim();
    if (!pl || !userId) return;
    this.api.addCollaborator(pl.id, userId).subscribe({
      next: () => {
        this.addingCollaborator.set(false);
        this.reloadDetail();
      },
    });
  }

  protected removeCollaborator(userId: string): void {
    const pl = this.activePlaylist();
    if (!pl) return;
    this.api.removeCollaborator(pl.id, userId).subscribe({ next: () => this.reloadDetail() });
  }

  protected collabColor(i: number) {
    return COLLAB_PALETTE[i % 2];
  }
}
