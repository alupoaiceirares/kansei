import { Component, computed, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';

interface Genre {
  name: string;
  uses: number;
  votes: number;
}

interface ProposedGenre {
  name: string;
  uses: number;
  by: string;
}

const DOTS = ['#12B76B', '#E23A3A', '#3ad38b', '#F0122F', 'rgba(18,183,107,0.5)', 'rgba(226,58,58,0.5)'];

/** Controlled genre taxonomy: add, approve/reject user-proposed genres, rename/remove active ones. */
@Component({
  selector: 'wh-admin-genres',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './admin-genres.html',
  styleUrl: './admin-genres.css',
})
export class AdminGenresPage {
  protected draft = signal('');
  protected dupWarning = signal(false);
  protected confirming = signal<string | null>(null);

  protected genres = signal<Genre[]>([
    { name: 'Shoegaze', uses: 412, votes: 1206 },
    { name: 'Dub techno', uses: 288, votes: 903 },
    { name: 'Ambient', uses: 201, votes: 744 },
    { name: 'Post-punk', uses: 166, votes: 512 },
    { name: 'Alt country', uses: 121, votes: 388 },
    { name: 'Drone', uses: 96, votes: 274 },
    { name: 'Dream pop', uses: 74, votes: 211 },
    { name: 'Krautrock', uses: 38, votes: 96 },
  ]);

  protected proposed = signal<ProposedGenre[]>([
    { name: 'Slowcore', uses: 12, by: 'Ivy Sennett' },
    { name: 'Hauntology', uses: 5, by: 'Halden Mure' },
    { name: 'vibes', uses: 2, by: 'Otis Vance' },
  ]);

  protected sortedGenres = computed(() =>
    [...this.genres()]
      .sort((a, b) => b.uses - a.uses)
      .map((g, i) => ({ ...g, dot: DOTS[i % DOTS.length], confirming: this.confirming() === g.name })),
  );

  protected countText = computed(() => `${this.genres().length} active · ${this.proposed().length} awaiting review`);

  protected onDraft(value: string): void {
    this.draft.set(value);
    this.dupWarning.set(false);
  }

  protected addGenre(): void {
    const name = this.draft().trim();
    if (!name) return;
    const exists = this.genres().some((g) => g.name.toLowerCase() === name.toLowerCase());
    if (exists) {
      this.dupWarning.set(true);
      return;
    }
    this.genres.update((g) => [...g, { name, uses: 0, votes: 0 }]);
    this.draft.set('');
    this.dupWarning.set(false);
  }

  protected askRemove(name: string): void {
    this.confirming.set(name);
  }

  protected cancelRemove(): void {
    this.confirming.set(null);
  }

  protected removeGenre(name: string): void {
    this.genres.update((g) => g.filter((x) => x.name !== name));
    this.confirming.set(null);
  }

  protected approveProposed(name: string): void {
    const p = this.proposed().find((x) => x.name === name);
    if (!p) return;
    this.proposed.update((list) => list.filter((x) => x.name !== name));
    this.genres.update((list) => [...list, { name: p.name, uses: p.uses, votes: p.uses }]);
  }

  protected rejectProposed(name: string): void {
    this.proposed.update((list) => list.filter((x) => x.name !== name));
  }
}
