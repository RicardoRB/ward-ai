final class Check {
  void spin(int limit) {
    int count = 0;
    while (true) {
      count++;
      if (count > limit) {
        break;
      }
    }
  }
}
