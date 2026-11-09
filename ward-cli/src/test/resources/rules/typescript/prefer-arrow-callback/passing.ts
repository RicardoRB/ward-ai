export function run(values: number[]): number[] {
  const doubled = values.map((value) => value * 2);
  const counted = values.map(function fact(value): number {
    return value > 0 ? fact(value - 1) : 0;
  });
  const scaled = values.map(function (value) {
    return this.factor * value;
  });
  run(function* () {
    yield 1;
  });
  return doubled.concat(counted, scaled);
}
