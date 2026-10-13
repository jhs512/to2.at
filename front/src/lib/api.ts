/**
 * The backend lives on another origin (ADR 0001), so every call has to carry
 * credentials explicitly — the session cookie is not same-origin.
 */
export const API_ORIGIN = import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_ORIGIN}${path}`, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: { accept: 'application/json', ...(init?.headers ?? {}) },
  })
  if (!res.ok) {
    let message = res.statusText
    try {
      const body = (await res.json()) as { message?: string }
      if (body.message) message = body.message
    } catch {
      // Not every failure has a JSON body; the status alone is enough.
    }
    throw new ApiError(res.status, message)
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}

export interface Me {
  nickname: string
  email?: string
}

export type LinkState = 'ACTIVE' | 'DELETED' | 'SUSPENDED'

export interface ApiKeyMetadata {
  id: number
  name: string
  createdAt: string
  revokedAt?: string | null
}

export interface IssuedApiKey extends ApiKeyMetadata {
  key: string
}

export interface Link {
  code: string
  shortUrl: string
  target: string
  state: LinkState
  createdAt: string
}

export const api = {
  listApiKeys: () => request<ApiKeyMetadata[]>('/api-keys'),
  createApiKey: (name: string) =>
    request<IssuedApiKey>('/api-keys', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name }),
    }),
  revokeApiKey: (id: number) => request<void>(`/api-keys/${id}`, { method: 'DELETE' }),
  me: () => request<Me>('/me'),
  listLinks: () => request<Link[]>('/links'),
  createLink: (target: string, requestId?: string) =>
    request<Link>('/links', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ target, requestId }),
    }),
  deleteLink: (code: string) => request<void>(`/links/${code}`, { method: 'DELETE' }),
  updateLink: (code: string, target: string) =>
    request<Link>(`/links/${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ target }),
    }),
}

/** Full-page navigation, not fetch: this is an OAuth redirect, not an API call. */
export function startKakaoLogin() {
  window.location.href = `${API_ORIGIN}/oauth2/authorization/kakao`
}

export function logout() {
  window.location.href = `${API_ORIGIN}/logout`
}
