class ShortCircuit {
  boolean flag;

  void run(boolean a) {
    if (a && check()) {
      work();
    }
    flag = a || check();
    done();
  }
}
