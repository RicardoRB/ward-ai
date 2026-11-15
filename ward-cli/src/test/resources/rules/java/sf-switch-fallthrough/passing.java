final class Check {
  int pick(int value) {
    int out = 0;
    switch (value) {
      case 1:
        out = 1;
        break;
      case 2:
        out = 2;
        break;
      default:
        break;
    }
    return out;
  }

  int meant(final int value) {
    int total = 0;
    switch (value) {
      case 1: {
        total = 1;
        break;
      }
      case 2:
        total += 2;
        // falls-through
      case 3:
        total += 3;
        /* fall through */
      case 4:
        total += 4;
        // fallthru
      default:
        total += 5;
    }
    return total;
  }
}
