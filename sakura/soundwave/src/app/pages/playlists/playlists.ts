import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

type PlaylistTag = 'Shared' | 'Private' | 'Collab';

interface PlaylistRow {
  name: string;
  tag: PlaylistTag;
  meta: string;
  updated: string;
  owned: boolean;
}

interface PlaylistTrack {
  n: number;
  title: string;
  artist: string;
  format: string;
  length: string;
}

interface Collaborator {
  name: string;
  initial: string;
  avatarBg: string;
  ring: string;
  initialColor: string;
}

const TAG_STYLE: Record<PlaylistTag, { bg: string; color: string }> = {
  Shared: { bg: 'rgba(18,183,107,0.14)', color: '#12B76B' },
  Private: { bg: 'rgba(255,255,255,0.07)', color: 'rgba(242,240,236,0.6)' },
  Collab: { bg: 'rgba(226,58,58,0.18)', color: '#F2565A' },
};

const COLLAB_PALETTE = [
  { avatarBg: 'rgba(18,183,107,0.13)', ring: 'rgba(18,183,107,0.32)', initialColor: '#12B76B' },
  { avatarBg: 'rgba(226,58,58,0.17)', ring: 'rgba(226,58,58,0.32)', initialColor: '#F2565A' },
];

/** List view (owned/shared/collab) → detail view with reorderable tracks and collaborators. */
@Component({
  selector: 'wh-playlists',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './playlists.html',
  styleUrl: './playlists.css',
})
export class PlaylistsPage {
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected queue = MOCK_DOWNLOAD_QUEUE;

  protected view = signal<'list' | 'detail'>('list');
  protected shared = signal(true);
  protected activePlaylist = signal<PlaylistRow | null>(null);

  protected tagStyle = TAG_STYLE;

  protected playlists: PlaylistRow[] = [
    { name: 'Late shift', tag: 'Shared', meta: '31 tracks · 2h 14m · you own', updated: '2d ago', owned: true },
    { name: 'Wire & static', tag: 'Private', meta: '18 tracks · 1h 09m · you own', updated: '5d ago', owned: true },
    { name: 'Reed sessions', tag: 'Collab', meta: '9 tracks · 41m · Ansel Reed owns', updated: '1w ago', owned: false },
    { name: 'Winter drone', tag: 'Private', meta: '44 tracks · 3h 52m · you own', updated: '1w ago', owned: true },
    { name: 'Kitchen radio', tag: 'Shared', meta: '27 tracks · 1h 48m · you own', updated: '2w ago', owned: true },
    { name: 'Halden picks', tag: 'Collab', meta: '62 tracks · 4h 11m · Halden Mure owns', updated: '2w ago', owned: false },
    { name: 'Rust and rain', tag: 'Private', meta: '13 tracks · 52m · you own', updated: '3w ago', owned: true },
    { name: 'Bellhouse b-sides', tag: 'Collab', meta: '21 tracks · 1h 26m · Bellhouse owns', updated: '1mo ago', owned: false },
    { name: 'Signal bleed', tag: 'Shared', meta: '8 tracks · 34m · you own', updated: '1mo ago', owned: true },
  ];

  protected tracks = signal<PlaylistTrack[]>([
    { n: 1, title: 'Ghost Frequency', artist: 'Mora Vale', format: 'MP3', length: '3:31' },
    { n: 2, title: 'Low Tide Signal', artist: 'Bellhouse', format: 'MP3', length: '4:18' },
    { n: 3, title: 'Halogen Hymn', artist: 'Ivy Sennett', format: 'MP3', length: '5:02' },
    { n: 4, title: 'Rust Cathedral', artist: 'The Longwave', format: 'MP4', length: '6:44' },
    { n: 5, title: 'Copper Line', artist: 'Ansel Reed', format: 'MP3', length: '3:57' },
    { n: 6, title: 'Quiet Wire', artist: 'Ivy Sennett', format: 'MP3', length: '4:12' },
    { n: 7, title: 'Nightshift Bloom', artist: 'Kestrel Park', format: 'MP4', length: '5:38' },
    { n: 8, title: 'Slow Static Parade', artist: 'Halden Mure', format: 'MP3', length: '4:07' },
  ]);

  protected collaborators: Collaborator[] = ['Ansel Reed', 'Ivy Sennett', 'Halden Mure'].map((name, i) => ({
    name,
    initial: name.charAt(0),
    ...COLLAB_PALETTE[i % 2],
  }));

  private dragIndex: number | null = null;

  protected openDetail(playlist: PlaylistRow): void {
    this.activePlaylist.set(playlist);
    this.view.set('detail');
  }

  protected backToList(): void {
    this.view.set('list');
  }

  protected toggleShared(): void {
    this.shared.update((v) => !v);
  }

  protected removeTrack(index: number): void {
    this.tracks.update((list) => list.filter((_, i) => i !== index).map((t, i) => ({ ...t, n: i + 1 })));
  }

  protected removeCollaborator(name: string): void {
    this.collaborators = this.collaborators.filter((c) => c.name !== name);
  }

  protected onDragStart(index: number): void {
    this.dragIndex = index;
  }

  protected onDrop(index: number): void {
    if (this.dragIndex === null || this.dragIndex === index) return;
    this.tracks.update((list) => {
      const next = [...list];
      const [moved] = next.splice(this.dragIndex!, 1);
      next.splice(index, 0, moved);
      return next.map((t, i) => ({ ...t, n: i + 1 }));
    });
    this.dragIndex = null;
  }
}
