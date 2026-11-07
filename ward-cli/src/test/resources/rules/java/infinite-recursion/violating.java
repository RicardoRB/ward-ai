class Violating {
  int run(int a) {
    return run(a - 1);
  }
  void loop() {
    this.loop();
  }
}
