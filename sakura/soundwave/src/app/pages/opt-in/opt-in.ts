import { Component, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { WirehoodMarkComponent } from '../../shared/wirehood-mark/wirehood-mark';
import { AuthService } from '../../core/auth';
import { WirehoodApi } from '../../core/wirehood-api';

/** Modal-style gate: blocks wirehood content until the user's wirehood_users row exists. */
@Component({
  selector: 'wh-opt-in',
  standalone: true,
  imports: [RouterLink, WirehoodMarkComponent],
  templateUrl: './opt-in.html',
  styleUrl: './opt-in.css',
})
export class OptInPage {
  protected joining = signal(false);
  protected error = signal<string | null>(null);

  constructor(
    private router: Router,
    private auth: AuthService,
    private wirehoodApi: WirehoodApi,
  ) {}

  protected join(): void {
    this.joining.set(true);
    this.error.set(null);
    this.wirehoodApi.optIn().subscribe({
      next: () => {
        this.auth.markOptedIn();
        this.router.navigateByUrl('/home');
      },
      error: () => {
        this.joining.set(false);
        this.error.set('Could not join wirehood — try again.');
      },
    });
  }
}
