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
