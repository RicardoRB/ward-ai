class Sample {
  String find(String key) {
    return null;
  }

  String safe(String key) {
    return key;
  }

  int checked(String key) {
    String value = find(key);
    if (value == null) {
      return 0;
    }
    return value.length();
  }

  int other(String key) {
    return safe(key).length();
  }
}

