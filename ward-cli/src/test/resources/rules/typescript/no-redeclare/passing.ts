var a = 1;
function f() {}
function g(p) {
  var q = p;
  return q;
}
{
  let b = 1;
}
interface Merged {
  a: number;
}
interface Merged {
  b: number;
}
function overloaded(a: number): void;
function overloaded(a: string): void;
function overloaded(a: unknown) {}
namespace Space {
  export const x = 1;
}
namespace Space {
  export const y = 2;
}
function Space2() {}
namespace Space2 {}
class Box {}
interface Box {
  size: number;
}
enum Color {
  Red,
}
namespace Color {
  export const count = 1;
}
