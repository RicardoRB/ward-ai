class Violating {
  int run(int a) {
    return run(a - 1);
  }
  void loop() {
    this.loop();
  }

  int twice(int n) {
    return twice(n);
  }

  int twice(long n) {
    return 2;
  }
}
