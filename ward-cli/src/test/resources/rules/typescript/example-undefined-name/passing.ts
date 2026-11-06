import type { Shape } from './shape';

interface Box<T> {
  value: T;
}

function wrap<R>(shape: Shape, item: R): Box<R> {
  return { value: item };
}

export class Holder<S> {
  constructor(public item: Box<S>) {}
}
console.log(wrap, Holder, window.name);
