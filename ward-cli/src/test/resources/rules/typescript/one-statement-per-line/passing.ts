export function pair(a: number): number {
  for (let i = 0; i < a; i++) {
    a += i;
  }
  const b = a + 1;
  return b;
}
