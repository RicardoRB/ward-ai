class SwitchExpression {
  void run(int n) {
    switch (n) {
      case 1 -> one();
      case 2 -> two();
      default -> other();
    }
    done();
  }
}
