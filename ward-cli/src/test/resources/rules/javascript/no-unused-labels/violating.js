function f() {
  outer: for (;;) {
    break;
  }
  used: for (;;) {
    break used;
  }
}
