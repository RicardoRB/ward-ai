export function run(values: number[], factor: number): number[] {
  return values.map(function (value) {
    return this.factor * value;
  });
}
