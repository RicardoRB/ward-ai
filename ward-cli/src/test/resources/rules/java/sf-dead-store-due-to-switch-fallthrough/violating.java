final class Check {
  int pick(int mode) {
    int value = 0;
    switch (mode) {
      case 1:
        value = 1;
      case 2:
        value = 2;
        break;
      default:
        break;
    }
    return value;
  }
}
