export function sign(value) {
  if (value < 0) {
    return 'negative';
  } else if (value > 0) {
    return 'positive';
  }
  return 'zero';
}
