import { Injectable, signal } from '@angular/core';
import { CONTROL_TOWER_URL, NORTHSTAR_URL } from './config';

const TOKEN_KEY = 'soundwave_token';
const OPTED_IN_KEY = 'soundwave_opted_in';
const ROLE_KEY = 'soundwave_role';
const JOINED_AT_KEY = 'soundwave_joined_at';

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
  private roleSignal = signal<string | null>(localStorage.getItem(ROLE_KEY));

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

  /** Reads the JWT's own claims client-side, no signature check, only for display/ownership, never trust for security. */
  getUserId(): string | null {
    return this.decodeClaims()?.sub ?? null;
  }

  getUsername(): string | null {
    return this.decodeClaims()?.username ?? null;
  }

  private decodeClaims(): { sub: string; username: string } | null {
    const token = this.tokenSignal();
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    } catch {
      return null;
    }
  }

  isAuthenticated(): boolean {
    return !!this.tokenSignal();
  }

  isOptedIn(): boolean {
    return this.optedInSignal();
  }

  markOptedIn(role: string, joinedAt: string): void {
    localStorage.setItem(OPTED_IN_KEY, 'true');
    localStorage.setItem(ROLE_KEY, role);
    localStorage.setItem(JOINED_AT_KEY, joinedAt);
    this.optedInSignal.set(true);
    this.roleSignal.set(role);
  }

  /** Only known if this browser is the one that actually did the opt-in, no GET-status endpoint exists to re-fetch it. */
  getJoinedAt(): string | null {
    return localStorage.getItem(JOINED_AT_KEY);
  }

  isAdmin(): boolean {
    return this.roleSignal() === 'ADMIN';
  }

  redirectToLogin(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
    window.location.href = `${NORTHSTAR_URL}/login`;
  }

  /**
   * Clears the session credential and best-effort blacklists the token server-side before
   * bouncing to northstar. Opt-in/role/joinedAt are permanent wirehood membership, not session
   * state, so they deliberately survive sign-out - clearing them would force the opt-in modal
   * back up on next login even though the wirehood_users row still exists server-side.
   */
  signOut(): void {
    const token = this.tokenSignal();
    localStorage.removeItem(TOKEN_KEY);
    this.tokenSignal.set(null);
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
