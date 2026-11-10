











class Sample {
  Object run(String value) {
    return Util.<Object>pick(value);
  }

  String plain(String value) {
    return Util.pick(value);
  }
}

class Util {
  static <T> T pick(T value) {
    return value;
  }
}
