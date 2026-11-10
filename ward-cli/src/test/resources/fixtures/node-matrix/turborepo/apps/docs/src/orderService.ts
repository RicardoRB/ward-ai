/** One line of the example ledger that the documentation pages walk through. */
export interface LedgerLine {
  readonly orderId: string;
  readonly amount: number;
}

const ledger: LedgerLine[] = [];

/**
 * Appends an example line, rejecting an amount that is not greater than zero.
 *
 * @param orderId the order the line belongs to
 * @param amount the amount of the line
 * @returns the recorded line
 */
export function recordLine(orderId: string, amount: number): LedgerLine {
  if (amount <= 0) {
    throw new RangeError("amount must be greater than zero");
  }
  const line: LedgerLine = { orderId: orderId, amount: amount };
  ledger.push(line);
  return line;
}

/**
 * Sums the example amounts recorded for one order.
 *
 * @param orderId the order to sum
 * @returns the sum of its amounts
 */
export function sumFor(orderId: string): number {
  let total = 0;
  for (const line of ledger) {
    if (line.orderId === orderId) {
      total += line.amount;
    }
  }
  return total;
}
