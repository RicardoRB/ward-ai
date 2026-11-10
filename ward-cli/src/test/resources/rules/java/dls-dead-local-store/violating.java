class Dead {
  int compute() {
    return 1;
  }

  int dead() {
    int x = compute();
    x = 2;
    return x;
  }
}
