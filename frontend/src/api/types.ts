// TypeScript mirrors of the backend DTOs (com.example.scicalculator.web.dto.*) and the
// Operation enum. Field names and shapes match the Java records exactly. Conventions:
//   - BigDecimal serializes to a JSON number under Jackson defaults  -> number
//   - Instant serializes to an ISO-8601 string                       -> string
//   - optional / blank-able fields                                   -> | null

/** The 11 Operation enum constants (com.example.scicalculator.domain.Operation). */
export type Operation =
  | 'ADD'
  | 'SUBTRACT'
  | 'MULTIPLY'
  | 'DIVIDE'
  | 'SIN'
  | 'COS'
  | 'TAN'
  | 'LOG'
  | 'LN'
  | 'POW'
  | 'SQRT'

/** POST /api/auth/login response: the signed JWT. */
export interface TokenResponse {
  token: string
}

/** POST /api/calculations body. name is optional (blank/null = unnamed). */
export interface CreateCalculationRequest {
  name: string | null
}

/** POST /api/calculations/{id}/steps body. Both fields required. */
export interface AppendStepRequest {
  operation: Operation
  operand: number
}

/** Calculation view. owner is the username string (the backend never exposes the lazy entity). */
export interface CalculationResponse {
  id: number
  name: string | null
  owner: string
  currentValue: number
  createdAt: string
  updatedAt: string
}

/** One applied step. */
export interface StepResponse {
  id: number
  sequenceNumber: number
  operation: Operation
  operand: number
  resultAfter: number
  createdAt: string
}

/** GET /api/calculations/{id} — calculation plus its ordered steps. */
export interface CalculationDetailResponse {
  calculation: CalculationResponse
  steps: StepResponse[]
}

/** One Envers revision of a step — body element of GET /api/calculations/{id}/history. */
export interface HistoryEntryResponse {
  revision: number
  username: string
  timestamp: string
  sequenceNumber: number
  operation: Operation
  operand: number
  resultAfter: number
}

/** Minimal error body returned by ApiExceptionHandler. */
export interface ErrorResponse {
  message: string
}
