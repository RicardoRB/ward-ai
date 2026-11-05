export interface Shape {
  readonly id: number;
  area(): number;
}
type Point = { x: number; y: number };
export type Result<T> = { ok: true; value: T } | { ok: false; error: string };
enum Color { Red, Green }
namespace Util {
export const id = <T>(v: T): T => v;
}
