import type {
  CalculationDetailResponse,
  CalculationResponse,
  ErrorResponse,
  HistoryEntryResponse,
  Operation,
  StepResponse,
  TokenResponse,
} from './types'

const TOKEN_KEY = 'scicalc.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** Thrown for any non-2xx response; carries the HTTP status so callers can branch on it. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

// A 401 from anywhere should cleanly log the user out. The client deliberately does NOT import
// React, so instead AuthProvider registers its logout() here at mount and request() calls it on
// a 401. This keeps the transport layer decoupled from the UI layer.
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(fn: () => void): void {
  unauthorizedHandler = fn
}

/**
 * The single fetch wrapper for the whole app.
 *  - Sets Content-Type: application/json, and Authorization: Bearer <token> when a token exists.
 *  - 401: clear the token, fire the unauthorized handler, then throw ApiError(401).
 *  - other non-2xx: parse ErrorResponse if present, then throw ApiError(status, message).
 *  - 2xx with an empty body (e.g. 201 from register): resolve without parsing.
 *  - 2xx with a body: parse and return it as T.
 */
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...init, headers })

  if (response.status === 401) {
    clearToken()
    unauthorizedHandler?.()
    throw new ApiError(401, 'Unauthorized')
  }

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`
    try {
      const body = (await response.json()) as ErrorResponse
      if (body?.message) {
        message = body.message
      }
    } catch {
      // No body, or a non-JSON body — keep the default message.
    }
    throw new ApiError(response.status, message)
  }

  // 2xx: a 201/204 may carry no body, so read text first and only parse when there's something.
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const authApi = {
  /** POST /api/auth/register — expects 201 with no body (or 409 if the username is taken). */
  register(username: string, password: string): Promise<void> {
    return request<void>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
  },

  /** POST /api/auth/login — returns the signed JWT to store and send on later requests. */
  login(username: string, password: string): Promise<TokenResponse> {
    return request<TokenResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
  },
}

export const calcApi = {
  /** POST /api/calculations — create a new (optionally named) calculation owned by the caller. */
  create(name: string | null): Promise<CalculationResponse> {
    return request<CalculationResponse>('/api/calculations', {
      method: 'POST',
      body: JSON.stringify({ name }),
    })
  },

  /** POST /api/calculations/{id}/steps — append one step; returns the persisted step (or throws on rollback). */
  appendStep(
    id: number,
    operation: Operation,
    operand: number,
  ): Promise<StepResponse> {
    return request<StepResponse>(`/api/calculations/${id}/steps`, {
      method: 'POST',
      body: JSON.stringify({ operation, operand }),
    })
  },

  /** GET /api/calculations/{id} — the calculation plus its ordered steps. */
  get(id: number): Promise<CalculationDetailResponse> {
    return request<CalculationDetailResponse>(`/api/calculations/${id}`)
  },

  /** GET /api/calculations/{id}/history — Envers revision history (ascending revision). */
  getHistory(id: number): Promise<HistoryEntryResponse[]> {
    return request<HistoryEntryResponse[]>(`/api/calculations/${id}/history`)
  },
}
