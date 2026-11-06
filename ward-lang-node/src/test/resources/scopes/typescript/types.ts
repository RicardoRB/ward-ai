import type { T } from './t';
import { type U, V } from './u';
import Def, * as All from './all';
interface I<P> extends J { a: T; m(x: U): P }
enum E { A, B = 2 }
function f<R>(p: T, q?: number): R {
  const z: Array<R> = p as any;
  return z;
}
class K<S> implements I<S> {
  private x: S;
  constructor(public y: V) {}
  get g(): T { return this.y; }
}
export type Alias<Q> = Q | V;
let typed: Def = new All.Thing();
export { E };
