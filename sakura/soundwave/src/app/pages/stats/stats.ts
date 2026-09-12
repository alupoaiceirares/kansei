import { Component, computed, inject, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, MusicProfile } from '../../core/wirehood-api';
import { AuthService } from '../../core/auth';

const DONUT_PALETTE = ['#12B76B', '#E23A3A', '#3ad38b', '#F0122F', 'rgba(18,183,107,0.45)', 'rgba(226,58,58,0.45)', 'rgba(242,240,236,0.28)'];

/** KPI row + genre donut, both backed by wirehood's musicProfile GraphQL query. */
@Component({
  selector: 'wh-stats',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './stats.html',
  styleUrl: './stats.css',
})
export class StatsPage {
  private api = inject(WirehoodApi);
  private auth = inject(AuthService);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected profile = signal<MusicProfile | null>(null);

  constructor() {
    this.api.musicProfile().subscribe({ next: (profile) => this.profile.set(profile) });
  }

  protected kpis = computed(() => {
    const p = this.profile();
    if (!p) return [];
    return [
      { label: 'Tracks saved', value: `${p.totalTracksSaved}`, sub: '', color: '#F2F0EC', size: '27px' },
      { label: 'Total plays', value: p.totalPlays.toLocaleString(), sub: 'all time', color: '#12B76B', size: '27px' },
      { label: 'Top artist', value: p.mostDownloadedArtist ?? 'None yet', sub: '', color: '#E23A3A', size: '19px' },
      { label: 'Playlists', value: `${p.playlistsOwned}`, sub: `${p.playlistsCollaborated} collaborating`, color: '#F2F0EC', size: '27px' },
      { label: 'Friends', value: `${p.friendCount}`, sub: '', color: '#F2F0EC', size: '27px' },
    ];
  });

  protected genres = computed(() => {
    const p = this.profile();
    if (!p) return [];
    return p.genreBreakdown.map((g, i) => ({ name: g.genreName, pct: Math.round(g.percentage), color: DONUT_PALETTE[i % DONUT_PALETTE.length] }));
  });

  protected donutBackground = computed(() => {
    let acc = 0;
    const stops = this.genres()
      .map((g) => {
        const from = acc;
        acc += g.pct;
        return `${g.color} ${from}% ${acc}%`;
      })
      .join(', ');
    return stops ? `conic-gradient(${stops})` : 'rgba(255,255,255,0.06)';
  });
}
