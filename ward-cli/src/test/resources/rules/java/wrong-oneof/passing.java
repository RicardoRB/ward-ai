












class Sample {
  int run(Msg msg) {
    switch (msg.getKindCase()) {
      case FOO:
        return msg.getFoo();
      default:
        return msg.getBar();
    }
  }
}

class Msg {}
