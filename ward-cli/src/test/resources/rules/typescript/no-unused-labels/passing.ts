function f(): void {
  used: for (;;) {
    for (;;) {
      continue used;
    }
  }
}
