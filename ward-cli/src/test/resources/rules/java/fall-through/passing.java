final class Falls {
  int pick(final int value) {
    int result = 0;
    switch (value) {
      case 1:
      case 2:
        result = 2;
        break;
      case 3:
        return 3;
      default:
        throw new IllegalStateException();
    }
    return result;
  }
}
