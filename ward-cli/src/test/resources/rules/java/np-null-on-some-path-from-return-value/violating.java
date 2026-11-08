class Sample {
  String find(String key) {
    return null;
  }

  int size(String key) {
    return find(key).length();
  }

  int viaLocal(String key) {
    String value = find(key);
    return value.length();
  }
}

