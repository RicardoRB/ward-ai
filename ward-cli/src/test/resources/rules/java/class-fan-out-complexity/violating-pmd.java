import java.util.List;

class Declared {
  Alpha a;
  Beta b;
  Gamma[] c;
  List<Delta> d;
  List<Echo> e;
  String text;
  int count;
}

class Created {
  Alpha a;

  void run() {
    new Beta();
    new Gamma();
    new Delta();
    new Echo();
  }
}

class Loops {
  void run(Object l) throws Exception {
    for (Alpha a : l) {}
    for (Beta b : l) {}
    for (Gamma c : l) {}
    try (Delta d = null) {}
  }
}
