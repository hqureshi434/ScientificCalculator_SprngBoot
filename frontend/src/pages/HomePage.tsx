import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, calcApi } from '../api/client'
import type {
  CalculationResponse,
  HistoryEntryResponse,
  Operation,
  StepResponse,
} from '../api/types'
import {
  BINARY_OPS,
  isUnary,
  OP_SYMBOLS,
  UNARY_OPS,
} from '../calc/operations'
import { useAuth } from '../auth/AuthContext'

export function HomePage() {
  const { username, logout } = useAuth()
  const navigate = useNavigate()

  // Calculator state. `operand` is the raw input text so we can validate empty/NaN ourselves.
  const [calculation, setCalculation] = useState<CalculationResponse | null>(null)
  const [steps, setSteps] = useState<StepResponse[]>([])
  const [operand, setOperand] = useState('')
  const [name, setName] = useState('')
  const [history, setHistory] = useState<HistoryEntryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)

  function handleLogout() {
    logout()
    navigate('/login')
  }

  function describeError(err: unknown): string {
    // The backend's ErrorResponse.message rides on ApiError (e.g. the divide-by-zero text).
    return err instanceof ApiError ? err.message : 'Something went wrong. Please try again.'
  }

  async function handleCreate() {
    setError(null)
    setBusy(true)
    try {
      const created = await calcApi.create(name.trim() ? name.trim() : null)
      setCalculation(created)
      setSteps([])
      setHistory(null)
      setOperand('')
      setName('')
    } catch (err) {
      setError(describeError(err))
    } finally {
      setBusy(false)
    }
  }

  async function handleOperation(op: Operation) {
    const active = calculation
    if (!active) return

    // Clear any prior error at the start of each attempt.
    setError(null)

    let value = 0
    if (!isUnary(op)) {
      // Binary ops need the typed operand. Number('') is 0, so check for blank explicitly.
      const parsed = Number(operand)
      if (operand.trim() === '' || Number.isNaN(parsed)) {
        setError('Enter a number first')
        return
      }
      value = parsed
    }

    setBusy(true)
    try {
      const step = await calcApi.appendStep(active.id, op, value)
      // Honor the backend transaction: only on SUCCESS do we advance the UI. The new current
      // value comes from the persisted step, and we append exactly the step the server returned.
      setSteps((prev) => [...prev, step])
      setCalculation({ ...active, currentValue: step.resultAfter })
    } catch (err) {
      // The backend rolled the transaction back, so the UI must show NO change — leave
      // currentValue and the steps list exactly as they were; just surface the message.
      setError(describeError(err))
    } finally {
      setBusy(false)
    }
  }

  function handleNewCalculation() {
    setCalculation(null)
    setSteps([])
    setHistory(null)
    setError(null)
    setOperand('')
    setName('')
  }

  async function loadHistory() {
    const active = calculation
    if (!active) return
    setError(null)
    setLoadingHistory(true)
    try {
      const rows = await calcApi.getHistory(active.id)
      setHistory(rows)
    } catch (err) {
      setError(describeError(err))
    } finally {
      setLoadingHistory(false)
    }
  }

  return (
    <div className="home">
      <header className="home-header">
        <h1>SciCalculator</h1>
        <div className="home-user">
          <span className="muted">
            Signed in as <strong>{username}</strong>
          </span>
          <button type="button" className="secondary" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>

      {error && <p className="error">{error}</p>}

      {!calculation ? (
        <section className="calc-card">
          <h2>New calculation</h2>
          <label>
            Name (optional)
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. demo"
            />
          </label>
          <button type="button" onClick={handleCreate} disabled={busy}>
            {busy ? 'Creating…' : 'Create'}
          </button>
        </section>
      ) : (
        <>
          <section className="calc-card">
            <div className="calc-card-head">
              <h2>{calculation.name ?? 'Untitled calculation'}</h2>
              <button type="button" className="secondary" onClick={handleNewCalculation}>
                New calculation
              </button>
            </div>

            <div className="current-value" aria-label="Current value">
              {calculation.currentValue}
            </div>

            <label>
              Operand
              <input
                type="number"
                inputMode="decimal"
                value={operand}
                onChange={(e) => setOperand(e.target.value)}
                placeholder="number for binary ops"
              />
            </label>

            <div className="op-group" role="group" aria-label="Binary operations">
              {BINARY_OPS.map((op) => (
                <button
                  key={op}
                  type="button"
                  className="op-button"
                  onClick={() => handleOperation(op)}
                  disabled={busy}
                  title={op}
                >
                  {OP_SYMBOLS[op]}
                </button>
              ))}
            </div>

            <div className="op-group" role="group" aria-label="Unary operations">
              {UNARY_OPS.map((op) => (
                <button
                  key={op}
                  type="button"
                  className="op-button"
                  onClick={() => handleOperation(op)}
                  disabled={busy}
                  title={op}
                >
                  {OP_SYMBOLS[op]}
                </button>
              ))}
            </div>
          </section>

          <section className="calc-card">
            <h2>Steps</h2>
            {steps.length === 0 ? (
              <p className="muted">No steps yet. Apply an operation above.</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Operation</th>
                    <th>Operand</th>
                    <th>Result</th>
                  </tr>
                </thead>
                <tbody>
                  {steps.map((step) => (
                    <tr key={step.id}>
                      <td>{step.sequenceNumber}</td>
                      <td>
                        {OP_SYMBOLS[step.operation]} <span className="muted">{step.operation}</span>
                      </td>
                      <td>{step.operand}</td>
                      <td>{step.resultAfter}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className="calc-card">
            <div className="calc-card-head">
              <h2>History</h2>
              <button
                type="button"
                className="secondary"
                onClick={loadHistory}
                disabled={loadingHistory}
              >
                {loadingHistory
                  ? 'Loading…'
                  : history === null
                    ? 'Show history'
                    : 'Refresh history'}
              </button>
            </div>

            {history === null ? (
              <p className="muted">
                The Envers revision history shows who made each change. Click “Show history”.
              </p>
            ) : history.length === 0 ? (
              <p className="muted">No revisions recorded yet.</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Rev</th>
                    <th>User</th>
                    <th>Time</th>
                    <th>Operation</th>
                    <th>Operand</th>
                    <th>Result</th>
                  </tr>
                </thead>
                <tbody>
                  {history.map((entry) => (
                    <tr key={entry.revision}>
                      <td>{entry.revision}</td>
                      <td>{entry.username}</td>
                      <td>{new Date(entry.timestamp).toLocaleString()}</td>
                      <td>
                        {OP_SYMBOLS[entry.operation]}{' '}
                        <span className="muted">{entry.operation}</span>
                      </td>
                      <td>{entry.operand}</td>
                      <td>{entry.resultAfter}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </div>
  )
}
