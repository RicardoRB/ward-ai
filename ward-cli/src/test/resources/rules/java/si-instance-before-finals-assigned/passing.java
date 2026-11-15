final class Check {
  static final String NAME = String.valueOf(1);
  static final Check INSTANCE = new Check();
}

final class Constants {
  static final Constants INSTANCE = new Constants();
  static final int SIZE = 10;
  static final String LABEL = "label";
}

final class Quiet {
  static final Quiet NONE = new Quiet();
  static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("quiet");

  Quiet() {}
}
