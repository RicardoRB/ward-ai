class Outer {
  void run(int n) {}
  void stop() {}

  class Own extends Base {
    void run(int n) {}

    void go() {
      run(1);
    }
  }

  class Other extends Base {
    void go() {
      run(1, 2);
      stop();
    }
  }
}
