final class Falls {
  int pick(final int value) {
    int result = 0;
    switch (value) {
      case 1:
        result = 1;
        break;
      default:
        result = 3;
    }
    return result;
  }
}
