export function run(values, factor) {
  return values.map(function (value) {
    return this.factor * value;
  });
}
