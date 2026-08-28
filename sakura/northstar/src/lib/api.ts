import { clearToken, getToken } from "./auth";

const BASE_URL = process.env.NEXT_PUBLIC_CONTROL_TOWER_URL;

// Thin fetch wrapper, always hits control-tower directly, never a Next.js API route/proxy
// Attaches the Bearer token when one exists; public endpoints (login/register) just won't have one yet
export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(options.headers);

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  // 401 means control-tower/shieldwall no longer accepts this token = expired, logged out elsewhere, or credentials_version bumped by a password/email change
  // Clear it locally so every subscriber (Header, route guards) stops treating the session as live instead of leaving a stale token sitting in localStorage
  if (res.status === 401 && token) {
    clearToken();
  }

  return res;
}
