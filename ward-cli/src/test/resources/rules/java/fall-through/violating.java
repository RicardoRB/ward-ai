final class Falls {
  int pick(final int value) {
    int result = 0;
    switch (value) {
      case 1:
        result = 1;
      case 2:
        result = 2;
        break;
      default:
        result = 3;
    }
    return result;
  }

  int open(final int value) {
    int total = 0;
    switch (value) {
      case 1: {
        total = 1;
      }
      case 2:
        // adds two
        total += 2;
        break;
      default:
        break;
    }
    return total;
  }
}
