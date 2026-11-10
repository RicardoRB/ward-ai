function h(a: number, b: boolean): number {
  while (true) {
    a--;
    if (a < 0) break;
  }
  if (b) {
    return a;
  }
  return b ? a : 0;
}
