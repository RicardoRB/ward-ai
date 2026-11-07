function f(): void {
  outer: for (;;) {
    break;
  }
  used: for (;;) {
    break used;
  }
}
