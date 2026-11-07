final class Check {
  int pick(int mode) {
    int value = 0;
    switch (mode) {
      case 1:
        value = 1;
      default:
        throw new IllegalStateException();
    }
    return value;
  }
}
