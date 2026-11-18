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
class PassingNames {
  void run(String s, int c) {
    document();
    doStatement();
    while (peek() != ')') {
      go();
    }
    if (s.endsWith(")")) {
      go();
    }
    for (final String v : java.util.List.of("[1,2)", "x")) {
      go();
    }
  }
  int peek() {
    return 0;
  }
  void document() {
  }
  void doStatement() {
  }
  void go() {
  }
}
class PassingComment {
  void run(boolean a, boolean b) {
    if (a) {
      go();
    } else // the other case
    if (b) {
      go();
    }
  }
  void go() {
  }
}
