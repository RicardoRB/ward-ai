final class Check {
  int pick(int value) {
    int out = 0;
    switch (value) {
      case 1:
        out = 1;
      case 2:
        out = 2;
        break;
      default:
        break;
    }
    return out;
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
