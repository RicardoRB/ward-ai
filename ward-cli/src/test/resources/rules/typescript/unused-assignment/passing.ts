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

export function watched() {
  let cancelled = false;
  const source = {
    cancel() {
      cancelled = true;
    },
  };
  return [source, cancelled];
}

export function slowStream() {
  let next = 0;
  return {
    read() {
      return next++ < 10;
    },
  };
}
