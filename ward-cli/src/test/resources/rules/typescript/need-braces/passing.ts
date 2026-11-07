export function sign(x: number): number {
  if (x < 0) {
    return -1;
  } else if (x > 0) {
    return 1;
  }
  return 0;
}
