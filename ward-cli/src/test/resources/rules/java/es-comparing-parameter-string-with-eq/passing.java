class Sample {
  boolean check(String a, String b, Object o) {
    String local = b;
    String other = local;
    boolean x = local == other;
    return a == null || o == a;
  }
}
