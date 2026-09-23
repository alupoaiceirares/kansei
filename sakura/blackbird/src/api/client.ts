import { CONTROL_TOWER_URL } from '../config';
import { getToken, redirectToLogin } from '../auth/auth';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
  ) {
    super('Request failed with ' + status);
  }

  /** The service answers with a problem detail, so prefer its own wording over a generic line. */
  get detail(): string | null {
    try {
      const parsed = JSON.parse(this.body) as { detail?: string; message?: string };
      return parsed.detail ?? parsed.message ?? null;
    } catch {
      return null;
    }
  }
}

type Options = {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
  /** 404 is a real answer on some endpoints (not opted in yet), not an error to throw on. */
  allow404?: boolean;
  signal?: AbortSignal;
};

/**
 * Every call goes to control-tower with the bearer token, it injects X-User-Id and X-User-Role
 * downstream. A 401 means the session is gone, so bounce to northstar rather than showing a form.
 */
export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const { method = 'GET', body, query, allow404 = false, signal } = options;
  const url = new URL(`${CONTROL_TOWER_URL}${path}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== '') url.searchParams.set(key, String(value));
    }
  }

  const token = getToken();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  // A FormData body is a file upload, the browser sets the multipart boundary itself
  const form = body instanceof FormData;
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json';

  const response = await fetch(url.toString(), {
    method,
    headers,
    body: body === undefined ? undefined : form ? (body as FormData) : JSON.stringify(body),
    signal,
  });

  if (response.status === 401) {
    redirectToLogin();
    throw new ApiError(401, 'Session expired');
  }
  if (response.status === 404 && allow404) {
    return null as T;
  }
  if (!response.ok) {
    throw new ApiError(response.status, await response.text().catch(() => ''));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** GraphQL lives at the same gateway, used by the stats screens. */
export async function graphql<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const payload = await api<{ data: T; errors?: { message: string }[] }>('/tailwind/graphql', {
    method: 'POST',
    body: { query, variables },
  });
  if (payload.errors?.length) throw new Error(payload.errors[0].message);
  return payload.data;
}

/**
 * Images live behind the gateway's JWT check, so an <img src> cannot fetch them directly.
 * The bytes are pulled with the token and handed back as an object URL the caller revokes.
 */
export async function fetchImageUrl(path: string, query?: Record<string, string>): Promise<string | null> {
  const url = new URL(CONTROL_TOWER_URL + path);
  if (query) for (const [key, value] of Object.entries(query)) url.searchParams.set(key, value);
  const token = getToken();
  const response = await fetch(url.toString(), { headers: token ? { Authorization: 'Bearer ' + token } : {} });
  if (!response.ok) return null;
  const blob = await response.blob();
  return URL.createObjectURL(blob);
}
