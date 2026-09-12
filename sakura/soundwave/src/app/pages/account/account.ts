import { Component, inject, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { AuthService } from '../../core/auth';
import { WirehoodApi } from '../../core/wirehood-api';

interface Preference {
  key: 'toasts' | 'autoplay' | 'publicProfile';
  label: string;
  desc: string;
}

/** Wirehood-scoped account settings, name/email/password stay on the Kansei profile. */
@Component({
  selector: 'wh-account',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
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
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'wirehood-export.json';
        link.click();
        URL.revokeObjectURL(url);
        this.exported.set(true);
      },
      error: () => this.exportError.set(true),
    });
  }

  protected togglePref(key: Preference['key']): void {
    this.prefs.update((p) => ({ ...p, [key]: !p[key] }));
  }
}
