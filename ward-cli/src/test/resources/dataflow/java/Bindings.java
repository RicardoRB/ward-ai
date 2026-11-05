package dataflow;

import java.util.List;

class Bindings {
  int risky() {
    return 1;
  }

  void use(Object value) {}

  void unreadForEach(List<String> items) {
    for (String s : items) {
    }
  }

  void overwrittenForEach(List<String> items) {
    for (String s : items) {
      s = "x";
      use(s);
    }
  }

  void unreadCatch() {
    try {
      risky();
    } catch (RuntimeException e) {
    }
  }

  void overwrittenCatch() {
    try {
      risky();
    } catch (RuntimeException e) {
      e = null;
    }
  }
}
