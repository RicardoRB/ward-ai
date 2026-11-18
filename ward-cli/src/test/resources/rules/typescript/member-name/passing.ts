class A {
  someValue = 1;
  private _cache = 2;
}

export class Inspectable {
  [Symbol.for('example.inspect')](depth: number) {
    return depth;
  }
}
