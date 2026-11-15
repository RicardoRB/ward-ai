final class Check {
  static final Check INSTANCE = new Check();
  static final String NAME = String.valueOf(1);

  Check() {
    System.out.println(NAME);
  }
}
