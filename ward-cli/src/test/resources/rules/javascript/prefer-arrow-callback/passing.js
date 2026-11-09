export function run(values) {
  const doubled = values.map((value) => value * 2);
  const counted = values.map(function fact(value) {
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
