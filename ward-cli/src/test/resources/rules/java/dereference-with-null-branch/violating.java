class Sample {
  int first(String a, boolean b) {
    return (b ? null : a).length();
  }

  int second(String a, boolean b) {
    return (b ? a : null).length();
  }
}
