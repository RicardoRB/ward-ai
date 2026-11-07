class Dispatch {
  void run(int x) {
    switch (x) {
      case 1:
        foo();
        break;
      default:
        break;
    }
  }

  void foo() {}
}
