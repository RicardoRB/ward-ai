export function add(a: number, b: number): number {
  return a + b;
}

export function clamp(value: number, min: number, max: number): number {
  if (min > max) {
    throw new RangeError("min must not exceed max");
  }
  return Math.min(Math.max(value, min), max);
}

export function average(values: readonly number[]): number {
  if (values.length === 0) {
    throw new RangeError("average needs at least one value");
  }
  return values.reduce((total, value) => total + value, 0) / values.length;
}
