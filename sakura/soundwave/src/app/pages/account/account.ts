import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { AuthService } from '../../core/auth';
import { WirehoodApi } from '../../core/wirehood-api';
import { NORTHSTAR_URL } from '../../core/config';
import { saveBlob } from '../../shared/format';

interface Preference {
  key: 'toasts' | 'autoplay' | 'publicProfile';
  label: string;
  desc: string;
}

/** Wirehood-scoped account settings, name/email/password stay on the Kansei profile. */
@Component({
  selector: 'wh-account',
  standalone: true,
  imports: [DatePipe, AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './account.html',
  styleUrl: './account.css',
})
export class AccountPage {
  private auth = inject(AuthService);
  private api = inject(WirehoodApi);

  protected isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  protected exported = signal(false);
  protected exportError = signal(false);
  protected northstarProfileUrl = `${NORTHSTAR_URL}/profile`;

  protected joinedAt = this.auth.getJoinedAt();
  protected tracksContributed = signal<number | null>(null);

  constructor() {
    this.api.library(0, 1).subscribe({ next: (page) => this.tracksContributed.set(page.totalElements) });
  }

  // No backend field for any of these yet (TODO.MD), kept as inert local-only UI state on purpose
  protected prefDefs: Preference[] = [
    { key: 'toasts', label: 'Download notifications', desc: 'Toast when a queued download flips to ready' },
    { key: 'autoplay', label: 'Autoplay next in queue', desc: 'Continue through a playlist without prompting' },
    { key: 'publicProfile', label: 'Public music profile', desc: 'Let other wirehood users see your stats card' },
  ];

  protected prefs = signal<Record<Preference['key'], boolean>>({ toasts: true, autoplay: false, publicProfile: true });

  protected startExport(): void {
    this.exportError.set(false);
    this.api.exportData().subscribe({
      next: (blob) => {
        saveBlob(blob, 'wirehood-export.json');
        this.exported.set(true);
      },
      error: () => this.exportError.set(true),
    });
  }

  protected togglePref(key: Preference['key']): void {
    this.prefs.update((p) => ({ ...p, [key]: !p[key] }));
  }

  protected confirmingLeave = signal(false);
  protected leaveRequested = signal(false);
  protected leaveError = signal(false);

  protected askDisableRequest(): void {
    this.confirmingLeave.set(true);
  }

  protected cancelDisableRequest(): void {
    this.confirmingLeave.set(false);
  }

  protected confirmDisableRequest(): void {
    this.leaveError.set(false);
    this.api.requestDisable().subscribe({
      next: () => {
        this.confirmingLeave.set(false);
        this.leaveRequested.set(true);
      },
      error: () => this.leaveError.set(true),
    });
  }
}
