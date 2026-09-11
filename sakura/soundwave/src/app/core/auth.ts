import { Injectable, signal } from '@angular/core';
import { CONTROL_TOWER_URL, NORTHSTAR_URL } from './config';

const TOKEN_KEY = 'soundwave_token';
const OPTED_IN_KEY = 'soundwave_opted_in';

/**
 * Auth bridge: captures the JWT handed off from northstar via a URL fragment
 * (`#token=...`), holds it in this app's own localStorage (separate origin/port,
 * doesn't inherit northstar's), and is the single source of truth for whether
 * this browser has a token / has completed the wirehood opt-in gate.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private tokenSignal = signal<string | null>(this.readToken());
  private optedInSignal = signal<boolean>(localStorage.getItem(OPTED_IN_KEY) === 'true');

  /** Reads `#token=...` off the URL (if present), persists it, and strips it from the address bar. */
  init(): void {
    const hash = window.location.hash;
    const match = hash.match(/[#&]token=([^&]+)/);
    if (match) {
      const token = decodeURIComponent(match[1]);
      localStorage.setItem(TOKEN_KEY, token);
      this.tokenSignal.set(token);
      const strippedHash = hash.replace(/[#&]token=[^&]+/, '').replace(/^&/, '#');
      const url = window.location.pathname + window.location.search + (strippedHash === '#' ? '' : strippedHash);
      window.history.replaceState(null, '', url);
    }
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  isAuthenticated(): boolean {
    return !!this.tokenSignal();
  }

  isOptedIn(): boolean {
    return this.optedInSignal();
  }

  markOptedIn(): void {
    localStorage.setItem(OPTED_IN_KEY, 'true');
    this.optedInSignal.set(true);
  }

  redirectToLogin(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
    window.location.href = `${NORTHSTAR_URL}/login`;
  }

  /** Clears local state and best-effort blacklists the token server-side before bouncing to northstar. */
  signOut(): void {
    const token = this.tokenSignal();
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(OPTED_IN_KEY);
    this.tokenSignal.set(null);
    this.optedInSignal.set(false);
    const bounce = () => (window.location.href = NORTHSTAR_URL);
    if (!token) {
      bounce();
      return;
    }
    fetch(`${CONTROL_TOWER_URL}/api/auth/logout`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
      .catch(() => undefined)
      .finally(bounce);
  }

  private readToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }
}
