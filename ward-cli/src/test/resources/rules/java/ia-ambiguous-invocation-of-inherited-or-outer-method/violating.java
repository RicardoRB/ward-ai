class Outer {
  void run(int n) {}

  class Inner extends Base {
    void go() {
      run(1);
    }
  }
}
