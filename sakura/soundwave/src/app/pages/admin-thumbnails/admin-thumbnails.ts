import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';

type SubmissionStatus = 'Pending' | 'Approved' | 'Rejected';
type FilterOption = SubmissionStatus | 'All';

interface RawSubmission {
  id: number;
  track: string;
  artist: string;
  by: string;
  when: string;
  base: SubmissionStatus;
}

/** Global queue of pending thumbnail submissions — current vs proposed artwork side by side. */
@Component({
  selector: 'wh-admin-thumbnails',
  standalone: true,
  imports: [RouterLink, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './admin-thumbnails.html',
  styleUrl: './admin-thumbnails.css',
})
export class AdminThumbnailsPage {
  protected filter = signal<FilterOption>('Pending');
  protected decisions = signal<Record<number, SubmissionStatus>>({});

  protected filterOptions: FilterOption[] = ['Pending', 'Approved', 'Rejected', 'All'];

  private raw: RawSubmission[] = [
    { id: 1, track: 'Ghost Frequency', artist: 'Mora Vale', by: 'Ansel Reed', when: '2 hours ago', base: 'Pending' },
    { id: 2, track: 'Rust Cathedral', artist: 'The Longwave', by: 'Ivy Sennett', when: '6 hours ago', base: 'Pending' },
    { id: 3, track: 'Copper Line', artist: 'Ansel Reed', by: 'Juno Halloway', when: 'Yesterday', base: 'Pending' },
    { id: 4, track: 'Halogen Hymn', artist: 'Ivy Sennett', by: 'Bellhouse', when: '2 days ago', base: 'Pending' },
    { id: 5, track: 'Paper Antenna', artist: 'Mora Vale', by: 'Wren Alcott', when: '3 days ago', base: 'Approved' },
    { id: 6, track: 'Marbled Sky', artist: 'Kestrel Park', by: 'Otis Vance', when: '4 days ago', base: 'Rejected' },
    { id: 7, track: 'Quiet Wire', artist: 'Ivy Sennett', by: 'Sable Koeppe', when: '5 days ago', base: 'Approved' },
  ];

  protected resolved = computed(() => {
    const decisions = this.decisions();
    return this.raw.map((r) => {
      const status = decisions[r.id] ?? r.base;
      const pending = status === 'Pending';
      const approved = status === 'Approved';
      return {
        id: r.id,
        track: r.track,
        artist: r.artist,
        by: r.by,
        when: r.when,
        status,
        pending,
        decided: !pending,
        border: pending ? 'rgba(255,255,255,0.08)' : approved ? 'rgba(18,183,107,0.25)' : 'rgba(226,58,58,0.25)',
        bg: pending ? 'rgba(13,16,15,0.78)' : approved ? 'rgba(18,183,107,0.05)' : 'rgba(226,58,58,0.045)',
        statusBg: approved ? 'rgba(18,183,107,0.16)' : 'rgba(226,58,58,0.16)',
        statusColor: approved ? '#12B76B' : '#E23A3A',
      };
    });
  });

  protected counts = computed(() => {
    const resolved = this.resolved();
    return {
      Pending: resolved.filter((r) => r.status === 'Pending').length,
      Approved: resolved.filter((r) => r.status === 'Approved').length,
      Rejected: resolved.filter((r) => r.status === 'Rejected').length,
      All: resolved.length,
    };
  });

  protected submissions = computed(() => {
    const f = this.filter();
    const resolved = this.resolved();
    return f === 'All' ? resolved : resolved.filter((r) => r.status === f);
  });

  protected pendingText = computed(() => `${this.counts().Pending} awaiting review`);

  protected pickFilter(f: FilterOption): void {
    this.filter.set(f);
  }

  protected decide(id: number, status: SubmissionStatus): void {
    this.decisions.update((d) => ({ ...d, [id]: status }));
  }
}
