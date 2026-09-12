import { Component, inject, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { WirehoodApi, Genre } from '../../core/wirehood-api';

const DOTS = ['#12B76B', '#E23A3A', '#3ad38b', '#F0122F', 'rgba(18,183,107,0.5)', 'rgba(226,58,58,0.5)'];

/** Read-only genre list, wirehood has no add/rename/remove/approve endpoints yet, see TODO.MD. */
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

  constructor() {
    this.api.genres().subscribe({
      next: (genres) =>
        this.genres.set(
          [...genres].sort((a, b) => a.name.localeCompare(b.name)).map((g, i) => ({ ...g, dot: DOTS[i % DOTS.length] })),
        ),
    });
  }
}
