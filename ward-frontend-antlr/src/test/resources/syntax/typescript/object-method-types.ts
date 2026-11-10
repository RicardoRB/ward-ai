const plain = { m() { return; } };
const typed = { m(): void { return; } };
const awaited = { async load(): Promise<void> {} };
const gen = { *items(): Iterator<number> { yield 1; } };
const generic = { pick<T>(value: T): T { return value; } };
const accessor = {
  get size(): number { return 1; },
  set size(v: number) {},
};
