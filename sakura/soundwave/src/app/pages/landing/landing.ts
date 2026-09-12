import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { WirehoodMarkComponent } from '../../shared/wirehood-mark/wirehood-mark';
import { WirehoodWavesComponent } from '../../shared/wirehood-waves/wirehood-waves';
import { AuthService } from '../../core/auth';

/** Entry point from Kansei, pre-opt-in intro page, links into the opt-in gate. */
@Component({
  selector: 'wh-landing',
  standalone: true,
  imports: [RouterLink, WirehoodMarkComponent, WirehoodWavesComponent],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class LandingPage {
  private auth = inject(AuthService);
  private router = inject(Router);

  protected menuOpen = signal(false);

  protected previewGenres = ['Shoegaze', 'Dub techno', 'Alt country', 'Ambient', 'Post-punk'];

  protected toggleMenu(): void {
    this.menuOpen.update((v) => !v);
  }

  protected signOut(): void {
    this.auth.signOut();
  }

  protected openOptIn(): void {
    this.router.navigateByUrl('/opt-in');
  }
}
