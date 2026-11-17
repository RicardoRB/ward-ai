/* global total */
total = Math.max(1, 2);

type Box<T> = {value: T};
export type Shortcut = {
  <Schema extends Box<number>>(schema: Schema): Box<Schema['value']>;
};
export type Wrapped<T extends Box<string> | string = string> = Promise<T> & {tag: T};
export const shortcut = <T>(input: Promise<T>, kind?: Shortcut): Wrapped<string> & T => input as never;

export const codes: ReadonlySet<number> = new Set([301, 302]);

export function describeAll(rows: Array<[string, number]>, stream: ReadableStream) {
  for (const [name, count] of rows) {
    console.log(name, count, stream);
  }

  for (let [index, row] of rows.entries()) {
    console.log(index, row);
  }
}

export type Inner<T> = T extends Box<infer Value> ? Value : never;
export const marker: unique symbol = Symbol('marker');

export type Keep<O> = {[Key in keyof O as Key extends 'a' ? Key : never]: O[Key]};
export type Same<O> = {[K in keyof O]: O[K]};
