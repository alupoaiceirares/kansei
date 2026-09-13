import { Component, inject, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, Genre, GenreProposal } from '../../core/wirehood-api';
import { formatRelativeTime } from '../../shared/format';

const DOTS = ['#12B76B', '#E23A3A', '#3ad38b', '#F0122F', 'rgba(18,183,107,0.5)', 'rgba(226,58,58,0.5)'];

/** Read-only active-genre list (still no rename/remove endpoint), plus the crowd-proposal review queue. */
@Component({
  selector: 'wh-admin-genres',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './admin-genres.html',
  styleUrl: './admin-genres.css',
})
export class AdminGenresPage {
  private api = inject(WirehoodApi);

  protected genres = signal<(Genre & { dot: string })[]>([]);
  protected proposals = signal<GenreProposal[]>([]);
  protected formatRelativeTime = formatRelativeTime;

  constructor() {
    this.reloadGenres();
    this.reloadProposals();
  }

  private reloadGenres(): void {
    this.api.genres().subscribe({
      next: (genres) =>
        this.genres.set(
          [...genres].sort((a, b) => a.name.localeCompare(b.name)).map((g, i) => ({ ...g, dot: DOTS[i % DOTS.length] })),
        ),
    });
  }

  private reloadProposals(): void {
    this.api.adminGenreProposals('PENDING', 0, 50).subscribe({ next: (result) => this.proposals.set(result.items) });
  }

  protected approve(id: string): void {
    this.api.adminApproveGenreProposal(id).subscribe({
      next: () => {
        this.reloadProposals();
        this.reloadGenres();
      },
    });
  }

  protected reject(id: string): void {
    this.api.adminRejectGenreProposal(id).subscribe({ next: () => this.reloadProposals() });
  }
}
