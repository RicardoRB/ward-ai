export function compute() {
  return 1;
}

export function overwritten() {
  let x = compute();
  x = 2;
  return x;
}
