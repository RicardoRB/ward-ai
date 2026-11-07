class Dispatch {
  void run(int x) {
    switch (x) {
      case 1:
        inner:
        for (int i = 0; i < 2; i++) {
          break inner;
        }
        break;
      default:
        break;
    }
  }
}
