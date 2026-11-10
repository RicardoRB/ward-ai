class SwitchFallthrough {
  void run(int n) {
    switch (n) {
      case 1:
        one();
      case 2:
        two();
        break;
      default:
        other();
    }
  }
}
