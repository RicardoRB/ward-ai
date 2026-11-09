var a = 1;
a: for (;;) {
  break a;
}
function f() {
  let b;
  b: for (;;) {
    break b;
  }
}
