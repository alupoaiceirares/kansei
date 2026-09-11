import { Component, signal } from '@angular/core';
import { AppHeaderComponent } from '../../shared/app-header/app-header';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { MOCK_DOWNLOAD_QUEUE } from '../../shared/mock-data';

interface Preference {
  key: 'toasts' | 'autoplay' | 'publicProfile';
  label: string;
  desc: string;
}

/** Wirehood-scoped account settings — name/email/password stay on the Kansei profile. */
@Component({
  selector: 'wh-account',
  standalone: true,
  imports: [AppHeaderComponent, WirehoodWavesComponent],
  templateUrl: './account.html',
  styleUrl: './account.css',
})
export class AccountPage {
  protected pendingDownloads = signal(3);
  protected incomingRequests = signal(2);
  protected isAdmin = signal(false);
  protected queue = MOCK_DOWNLOAD_QUEUE;

  protected exported = signal(false);

  protected prefDefs: Preference[] = [
    { key: 'toasts', label: 'Download notifications', desc: 'Toast when a queued download flips to ready' },
    { key: 'autoplay', label: 'Autoplay next in queue', desc: 'Continue through a playlist without prompting' },
    { key: 'publicProfile', label: 'Public music profile', desc: 'Let other wirehood users see your stats card' },
  ];

  protected prefs = signal<Record<Preference['key'], boolean>>({ toasts: true, autoplay: false, publicProfile: true });

  protected startExport(): void {
    this.exported.set(true);
  }

  protected togglePref(key: Preference['key']): void {
    this.prefs.update((p) => ({ ...p, [key]: !p[key] }));
  }
}
