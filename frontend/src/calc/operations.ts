import type { Operation } from '../api/types'

// Split the Operation union into the two shapes the UI treats differently:
//   - BINARY ops combine the current value with the operand the user types.
//   - UNARY ops act on the current value alone (the backend ignores the operand, but still
//     requires it non-null, so the UI sends 0).
// NOTE: the backend enum constant is POW (not "POWER") — these arrays are typed against the
// Operation union, so a typo here would fail to compile.
export const BINARY_OPS = ['ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE', 'POW'] as const
export const UNARY_OPS = ['SQRT', 'SIN', 'COS', 'TAN', 'LOG', 'LN'] as const

/** Short display symbol/label per operation, for the buttons and the step/history tables. */
export const OP_SYMBOLS: Record<Operation, string> = {
  ADD: '+',
  SUBTRACT: '−',
  MULTIPLY: '×',
  DIVIDE: '÷',
  POW: 'xʸ',
  SQRT: '√',
  SIN: 'SIN',
  COS: 'COS',
  TAN: 'TAN',
  LOG: 'LOG',
  LN: 'LN',
}

// A Set gives an O(1), exhaustive membership test that stays correct if UNARY_OPS changes.
const UNARY_SET = new Set<Operation>(UNARY_OPS)

/** True for operations that act on the current value alone (operand ignored by the engine). */
export function isUnary(op: Operation): boolean {
  return UNARY_SET.has(op)
}
