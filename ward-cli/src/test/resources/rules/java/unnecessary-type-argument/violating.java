class Sample {
  String run(String value) {
    return Util.<String>pick(value);
  }
}

class Util {
  static <T> T pick(T value) {
    return value;
  }
}
