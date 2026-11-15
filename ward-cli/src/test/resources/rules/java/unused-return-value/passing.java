





class Sample {
  String run(String text) {
    String joined = text.concat("x");
    text.length();
    return joined;
  }
}

class Other {
  String pick(int kind, String text) {
    return switch (kind) {
      case 1 -> text.replace(':', '/');
      default -> text;
    };
  }
}
