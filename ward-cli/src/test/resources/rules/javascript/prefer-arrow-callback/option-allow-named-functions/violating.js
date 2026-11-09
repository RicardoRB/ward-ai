export function run(values) {
  const doubled = values.map(function (value) {
    return value * 2;
  });
  return doubled.map(function twice(value) {
    return value * 2;
  });
}
