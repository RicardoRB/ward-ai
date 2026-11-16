export function pair(a) {
  for (let i = 0; i < a; i++) {
    a += i;
  }
  const b = a + 1;
  return b;
}
// A comment; it may hold a semicolon.
export const text = 'a; b';
