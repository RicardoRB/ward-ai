export function total(values: number[]): number {
  let count = values.length;
  count += 1;
  let late;
  if (values.length) {
    late = 1;
  }
  return count + (late ?? 0);
}
