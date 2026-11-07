declare function parse(text: string): number;
declare function assertDefined(value: unknown): asserts value is string;

export declare class Reader<T> extends Base<T> {
  protected abstract read(): T;
  get size(): number;
  set size(value: number);
  [key: string]: unknown;
}

declare global {
  interface Window {
    readonly [index: number]: string;
    get title(): string;
  }
}

type Pair = [first: string, second?: number];
