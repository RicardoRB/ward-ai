export function sign(value: number): string {
  if (value < 0) {
    return 'negative';
  } else {
    return 'other';
  }
}
