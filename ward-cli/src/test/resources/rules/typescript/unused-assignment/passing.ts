export function compute() {
  return 1;
}

export function used() {
  const x = compute();
  return x;
}

export function wasCalled(run: (callback: () => void) => void) {
  let called = false;
  run(() => {
    called = true;
  });
  return called;
}

let index = 0;
export function more() {
  return index++ < 10;
}
