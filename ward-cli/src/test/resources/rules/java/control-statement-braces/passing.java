class Passing {
  void run(boolean a) {
    if (a) {
      go();
    } else if (!a) {
      go();
    } else {
      go();
    }
    while (a) {
      go();
    }
    for (int i = 0; i < 3; i++) {
      go();
    }
    do {
      go();
    } while (a);
  }
  void go() {
  }
}
