export function sign(value: number): string {
  if (value < 0) {
    return 'negative';
  } else if (value > 0) {
    return 'positive';
  }
  return 'zero';
}
