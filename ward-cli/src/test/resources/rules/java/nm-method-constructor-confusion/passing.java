class Widget {
  Widget() {
  }

  static class Tool extends Widget {
    Tool() {
    }

    String describe(Object Tools) {
      return String.valueOf(Tools) + ObjectT();
    }

    String ObjectT() {
      return "";
    }
  }

  static class Builder {
    String build() {
      return new StringBuilder("a").append(new ObjectBuilder(1)).toString();
    }
  }
}
