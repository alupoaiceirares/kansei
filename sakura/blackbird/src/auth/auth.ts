import { CONTROL_TOWER_URL, NORTHSTAR_URL } from '../config';

const TOKEN_KEY = 'blackbird_token';
const RETURN_KEY = 'blackbird_return_to';

type Claims = { sub: string; username?: string; email?: string; role?: string; exp?: number };

let token: string | null = localStorage.getItem(TOKEN_KEY);
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribeToken(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getToken() {
  return token;
}

/**
 * Reads `#token=...` off the URL, persists it in this app's own localStorage (separate origin
 * from northstar, so nothing is inherited), and strips it from the address bar.
 */
export function captureHandoffToken(): void {
  const hash = window.location.hash;
  const match = hash.match(/[#&]token=([^&]+)/);
  if (!match) return;
  token = decodeURIComponent(match[1]);
  localStorage.setItem(TOKEN_KEY, token);
  const strippedHash = hash.replace(/[#&]token=[^&]+/, '').replace(/^&/, '#');
  const url = window.location.pathname + window.location.search + (strippedHash === '#' ? '' : strippedHash);
  window.history.replaceState(null, '', url);
  emit();
}

/** Reads the JWT's own claims client-side, no signature check, display and ownership only. */
function claims(): Claims | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as Claims;
  } catch {
    return null;
  }
}

export function getUserId(): string | null {
  return claims()?.sub ?? null;
}

export function getUsername(): string | null {
  return claims()?.username ?? null;
}

export function getEmail(): string | null {
  return claims()?.email ?? null;
}

/** Role is fixed at token issue time, a promotion only takes effect after a fresh login. */
export function isAdmin(): boolean {
  return claims()?.role === 'ADMIN';
}

export function isAuthenticated(): boolean {
  return !!token;
}

export function rememberReturnTo(path: string): void {
  sessionStorage.setItem(RETURN_KEY, path);
}

export function takeReturnTo(): string | null {
  const value = sessionStorage.getItem(RETURN_KEY);
  if (value) sessionStorage.removeItem(RETURN_KEY);
  return value;
}

export function peekReturnTo(): string | null {
  return sessionStorage.getItem(RETURN_KEY);
}

export function clearToken(): void {
  token = null;
  localStorage.removeItem(TOKEN_KEY);
  emit();
}

/** A 401 or a missing token sends the user back to northstar, blackbird never shows a login form. */
export function redirectToLogin(): void {
  clearToken();
  window.location.href = `${NORTHSTAR_URL}/login`;
}

export function goToKansei(): void {
  window.location.href = NORTHSTAR_URL;
}

/** Blacklists the token server-side on a best-effort basis, then bounces to northstar. */
export function signOut(): void {
  const current = token;
  clearToken();
  const bounce = () => (window.location.href = NORTHSTAR_URL);
  if (!current) {
    bounce();
    return;
  }
  fetch(`${CONTROL_TOWER_URL}/api/auth/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${current}` },
  })
    .catch(() => undefined)
    .finally(bounce);
}
