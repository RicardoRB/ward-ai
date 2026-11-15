export function pair(a: number): number {
  for (let i = 0; i < a; i++) {
    a += i;
  }
  const b = a + 1;
  return b;
}
// A comment; it may hold a semicolon.
type Pair = {left: string; right: number};
export const text: Pair = {left: 'a; b', right: 1};
