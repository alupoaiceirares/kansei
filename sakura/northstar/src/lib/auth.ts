const TOKEN_KEY = "kansei_token";

// Notified on every setToken/clearToken so components can stay in sync via useSyncExternalStore, without polling or setState-in-effect
const listeners = new Set<() => void>();

function notify(): void {
  listeners.forEach((listener) => listener());
}

export function subscribeToken(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOKEN_KEY, token);
  notify();
}

export function clearToken(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TOKEN_KEY);
  notify();
}

// Always clears locally, even if the server call fails - a dead/expired token shouldn't be able to trap the user logged in on this device
export async function logout(): Promise<void> {
  const token = getToken();
  try {
    if (token) {
      await fetch(`${process.env.NEXT_PUBLIC_CONTROL_TOWER_URL}/api/auth/logout`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
    }
  } finally {
    clearToken();
  }
}
