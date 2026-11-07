class Passing {
  void run(boolean a) {
    for (int i = 0; i < 3; i++) {
    }
    for (; a; a = false) {
    }
    for (;;) {
      break;
    }
  }
}
