type Callable = { <A extends string>(a: A): A };
type Inner<T> = T extends Array<infer V> ? V : never;
type Picked<O> = { [K in keyof O as K extends 'a' ? K : never]: O[K] };
function parse(text: string): number;
function parse(text: string, radix?: number): number {
  return Number.parseInt(text, radix);
}
const build = <T>(Maker: new () => T) => new Maker();
const rows: Array<[string, number]> = [];
for (const [name, count] of rows) {
  console.log(name, count);
}
for (let [index] of rows.entries()) {
  console.log(index);
}
const settings = { make({ size = 1, label = fallback }) { return size + label; } };
