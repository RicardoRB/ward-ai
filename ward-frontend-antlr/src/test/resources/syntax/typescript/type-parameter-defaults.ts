interface Box<T extends object | string = string> extends Iterable<T> {
  <R>(input?: undefined): Box<R>;
  size: number;
}

type Handler<A extends unknown[] = unknown[]> = (...args: A) => void;
type Named = [first: string, second?: number];
type Loose = [string, number?, ...boolean[]];

export type { Box, Handler, Named, Loose };
