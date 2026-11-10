class Sample {
  int run(Msg msg) {
    switch (msg.getKindCase()) {
      case FOO:
        return msg.getBar();
      default:
        return 0;
    }
  }
}

class Msg {}
