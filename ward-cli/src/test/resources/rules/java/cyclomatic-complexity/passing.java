final class Branchy {
  int classify(int n) {
    if (n == 1) {
      return 1;
    }
    if (n == 2) {
      return 2;
    }
    for (int i = 0; i < n; i++) {
      if (i > 3 && i < 9 || i == 12) {
        n++;
      }
    }
    while (n > 100) {
      n--;
    }
    do {
      n++;
    } while (n < 0);
    return n > 0 ? switchy(n) : 0;
  }

  int switchy(int n) {
    switch (n) {
      case 1:
        return 1;
      case 2:
        return 2;
      default:
        return 0;
    }
  }
}
