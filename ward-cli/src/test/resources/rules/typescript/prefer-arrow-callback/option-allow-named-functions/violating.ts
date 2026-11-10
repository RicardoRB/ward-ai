export function run(values: number[]): number[] {
  const doubled = values.map(function (value) {
    return value * 2;
  });
  return doubled.map(function twice(value) {
    return value * 2;
  });
}
