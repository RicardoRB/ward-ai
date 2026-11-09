export function f(source) {
  let { a, b } = source;
  a = 1;
  let [e, g] = [1, 2];
  return a + b + e + g;
}
