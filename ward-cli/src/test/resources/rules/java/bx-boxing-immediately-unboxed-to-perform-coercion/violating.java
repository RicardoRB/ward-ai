class Boxing {
  double bad(int count) {
    return Integer.valueOf(count).doubleValue();
  }

  int alsoBad(String text) {
    return new Double(text).intValue();
  }
}
